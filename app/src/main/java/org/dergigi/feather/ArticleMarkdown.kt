package org.dergigi.feather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.*
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full document renderer shared with Boris; keep parsing asynchronous and use Feather's media cache. */
@Composable
internal fun ArticleMarkdown(event: Event, onReference: (NostrContent.Reference) -> Unit, articleLayout: Boolean = true, embedState: FeedState? = null) {
    val context = LocalContext.current
    val navigate by rememberUpdatedState(onReference)
    val links = remember(context) { object : UriHandler {
        override fun openUri(uri: String) {
            NostrContent.decode(uri)?.let(navigate) ?: run {
                if (android.net.Uri.parse(uri).scheme in listOf("https", "http", "blossom")) context.openWebLink(uri)
            }
        }
    } }
    val translated = LocalTranslations.current?.text(event)
    val body by produceState<String?>(null, event.id, translated) {
        value = withContext(Dispatchers.Default) { (translated ?: event.content).withoutObjectMarkers() }
    }
    val flavour = remember { GFMFlavourDescriptor() }
    // The library's default arguments allocate new instances on every recomposition.
    // Those instances key its parse state, resetting the body to Loading and collapsing
    // the enclosing list item whenever relay/profile/scroll state updates.
    val parser = remember(flavour) { MarkdownParser(flavour) }
    val referenceLinks = remember(event.id) { ReferenceLinkHandlerImpl() }
    val markdown = rememberMarkdownState(content = body.orEmpty(), flavour = flavour,
        parser = parser, referenceLinkHandler = referenceLinks)
    val gallery by rememberUpdatedState(LocalOpenGallery.current)
    val transformer = remember(event.id) { ArticleImageTransformer { url -> gallery(listOf(url), 0, event) } }
    val title = remember(event.id, articleLayout) { if (articleLayout) event.tagValue("title")?.withoutObjectMarkers()?.takeIf(String::isNotBlank) else null }
    val cover = remember(event.id, articleLayout) { if (articleLayout) MediaContent.mediaUrl(event.tagValue("image")) else null }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (cover != null) {
            ArticleCover(cover, title, Modifier.clickable(onClickLabel = "Open article cover") {
                gallery(listOf(cover), 0, event)
            }, reader = true)
        } else if (title != null) Text(title, Modifier.padding(horizontal = 24.dp), style = typography.headlineLarge)
        if (articleLayout && isRecipe(event)) RecipeDetails(event, Modifier.padding(horizontal = 24.dp))
        if (body == null) CircularProgressIndicator(Modifier.padding(horizontal = 24.dp).size(24.dp), strokeWidth = 2.dp)
        else CompositionLocalProvider(
            LocalUriHandler provides links,
            LocalMarkdownEmbeds provides embedState?.let { MarkdownEmbeds(event, it, onReference) },
        ) {
            NoteSelection(event) {
                Markdown(markdownState = markdown, modifier = Modifier.fillMaxWidth().padding(horizontal = if (articleLayout) 24.dp else 0.dp),
                    components = articleLinkComponents,
                    colors = markdownColor(text = colors.onSurface, codeBackground = colors.surfaceContainerHigh,
                        inlineCodeBackground = colors.surfaceContainerHigh, dividerColor = colors.outlineVariant,
                        tableBackground = colors.surfaceContainer),
                    typography = markdownTypography(
                        h1 = typography.headlineLarge, h2 = typography.headlineMedium, h3 = typography.headlineSmall,
                        text = typography.bodyLarge, paragraph = typography.bodyLarge,
                        ordered = typography.bodyLarge, bullet = typography.bodyLarge, list = typography.bodyLarge,
                        quote = typography.bodyLarge, code = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        inlineCode = typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        textLink = TextLinkStyles(typography.bodyLarge.copy(color = colors.primary).toSpanStyle())),
                    padding = markdownPadding(block = 12.dp, list = 8.dp, listItemTop = 4.dp, listItemBottom = 4.dp),
                    imageTransformer = transformer)
            }
        }
    }
}

private class ArticleImageTransformer(private val open: (String) -> Unit) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val url = remember(link) { MediaContent.mediaUrl(link) } ?: return null
        val media = LocalMedia.current
        val loader = media.resources?.images ?: return null
        val context = LocalContext.current
        val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        val request = remember(url, width, media.active) {
            ImageRequest.Builder(context).data(url).diskCacheKey(url)
                .size(coil3.size.Size(coil3.size.Dimension(width.coerceAtLeast(1)), coil3.size.Dimension.Undefined))
                .networkCachePolicy(if (media.active) CachePolicy.ENABLED else CachePolicy.DISABLED).build()
        }
        val painter = rememberAsyncImagePainter(request, imageLoader = loader)
        return ImageData(painter, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open article image") { open(url) })
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        if (painter is AsyncImagePainter) {
            val state by painter.state.collectAsState()
            return state.painter?.intrinsicSize ?: Size.Unspecified
        }
        return painter.intrinsicSize
    }

    override fun placeholderConfig(density: Density, containerSize: Size, intrinsicImageSize: Size): PlaceholderConfig {
        val width = containerSize.width.takeIf { it.isFinite() && it > 0 } ?: with(density) { 280.dp.toPx() }
        val ratio = if (intrinsicImageSize.width > 0 && intrinsicImageSize.height > 0)
            intrinsicImageSize.height / intrinsicImageSize.width else 0.625f
        return PlaceholderConfig(with(density) { Size(width.toSp().value, (width * ratio).toSp().value) })
    }
}
