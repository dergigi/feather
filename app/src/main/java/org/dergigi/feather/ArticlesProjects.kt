package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val markdownImage = Regex("!\\[([^]\\n]*)]\\((https?://[^\\s)]+)(?:\\s+\"[^\"]*\")?\\)")

@Composable
internal fun ArticleContent(event: Event, compact: Boolean, onReference: (NostrContent.Reference) -> Unit, state: FeedState? = null) {
    if (compact) {
        ArticlePreview(event)
        return
    }
    ArticleMarkdown(event, onReference, embedState = state)
}

@Composable
internal fun ArticleListRow(event: Event, author: String, picture: String?, onProfile: () -> Unit, onOpen: () -> Unit, compactOverview: Boolean = false) {
    val moderationKey = remember(event.id) { Any() }
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (isRecipe(event)) "Read recipe" else "Read article", onClick = onOpen)
        .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ModerationAvatar(event.pubKey, author, picture, expansionKey = moderationKey, note = event, onClick = onProfile)
                Text(author, Modifier.weight(1f).padding(end = 8.dp)
                    .avatarModerationName(event.pubKey, moderationKey, onClick = onProfile),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isRecipe(event)) ArticleReadingTime(event)
                NoteTimestamp(event)
            }
        }
        ArticlePreview(event, compactOverview = compactOverview && !isRecipe(event))
    }
}

/** Estimate from the complete article, never its summary; retain only small results. */
private object ArticleReadingTimes {
    private val cache = object : LinkedHashMap<String, Int>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 128
    }
    private val urls = Regex("https?://[^\\s)<>]+")
    private val words = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    suspend fun minutes(event: Event): Int = withContext(Dispatchers.Default) {
        synchronized(cache) { cache[event.id] }?.let { return@withContext it }
        val text = urls.replace(markdownImage.replace(event.content, ""), "")
        val count = words.findAll(text).count()
        // A deliberately simple 200 words/minute estimate, rounded up.
        ((count + 199) / 200).coerceAtLeast(1).also { synchronized(cache) { cache[event.id] = it } }
    }
}

@Composable
internal fun ArticleReadingTime(event: Event) {
    val minutes by produceState<Int?>(null, event.id) { value = ArticleReadingTimes.minutes(event) }
    minutes?.let { estimate ->
        Row(Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Estimated reading time: $estimate ${if (estimate == 1) "minute" else "minutes"}"
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.reading_time), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            val duration = when {
                estimate < 60 -> "${estimate}m"
                estimate % 60 == 0 -> "${estimate / 60}h"
                else -> "${estimate / 60}h ${estimate % 60}m"
            }
            Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class ArticlePreviewModel(val title: String, val excerpt: String, val cover: String?)

/** A bounded, non-interactive excerpt keeps the entire preview a reliable article target. */
@Composable
private fun ArticlePreview(event: Event, compactOverview: Boolean = false) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val model by produceState<ArticlePreviewModel?>(null, event.id, bodyStyle) {
        value = withContext(Dispatchers.Default) {
            val raw = (event.tagValue("summary")?.takeIf { it.isNotBlank() } ?: event.content).take(2_000)
            val excerpt = markdownDocument(markdownImage.replace(raw.withoutObjectMarkers(), ""),
                bodyStyle, SpanStyle(), SpanStyle(), LinkInteractionListener { }).text
                .replace(Regex("\\s+"), " ").trim()
            ArticlePreviewModel(event.tagValue("title")?.withoutObjectMarkers()?.takeIf { it.isNotBlank() }
                ?: event.content.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', ' ')?.take(160).orEmpty(),
                excerpt, MediaContent.mediaUrl(event.tagValue("image")))
        }
    }
    val preview = model
    if (preview == null) Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.CenterStart) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    } else if (compactOverview) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preview.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (preview.excerpt.isNotBlank() && preview.excerpt != preview.title) Text(preview.excerpt,
                    style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            preview.cover?.let { cover ->
                RemoteImage(cover, Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)), preview.title, crop = true)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preview.cover != null) {
                ArticleCover(preview.cover, preview.title,
                    Modifier.clip(RoundedCornerShape(12.dp)))
            } else Text(preview.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (isRecipe(event)) RecipeDetails(event)
            else {
                if (preview.excerpt.isNotBlank() && preview.excerpt != preview.title) Text(preview.excerpt, style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One image/title treatment for cards and the reader; large text can grow its container. */
@Composable
internal fun ArticleCover(cover: String, title: String?, modifier: Modifier = Modifier, reader: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        val minimumHeight = if (reader) (maxWidth * 0.9f).coerceIn(280.dp, 440.dp)
            else (maxWidth * 0.625f).coerceIn(180.dp, 320.dp)
        Box(Modifier.fillMaxWidth().heightIn(min = minimumHeight)) {
            RemoteImage(cover, Modifier.matchParentSize(), title, crop = true)
            if (!title.isNullOrBlank()) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to Color.Transparent, 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))))
                Text(title, Modifier.align(Alignment.BottomStart).padding(top = 64.dp)
                    .padding(if (reader) 24.dp else 16.dp),
                    style = if (reader) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, color = Color.White,
                    maxLines = if (reader) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class Project(val name: String, val description: String, val url: String)

@Composable
internal fun ProjectCard(event: Event) {
    val context = LocalContext.current
    val project by produceState<Project?>(null, event.id) {
        value = withContext(Dispatchers.Default) {
            val identifier = event.tagValue("d").orEmpty()
            val web = event.tags.asSequence().filter { it.firstOrNull() == "web" }.flatMap { it.drop(1).asSequence() }
                .firstOrNull { runCatching { java.net.URI(it).let { uri -> uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() } }.getOrDefault(false) }
            Project(event.tagValue("name")?.takeIf { it.isNotBlank() } ?: identifier,
                event.tagValue("description").orEmpty(),
                web ?: "https://njump.to/${NAddress.create(30617, event.pubKey, identifier, relay = null)}")
        }
    }
    project?.let { model ->
        Surface(onClick = { context.openWebLink(model.url) }, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.project), null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (model.description.isNotBlank()) Text(model.description, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.open_external), stringResource(R.string.ui_open_project), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
