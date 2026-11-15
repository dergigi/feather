// Highlight layout and passage matching adapted from ants-android (MIT, copyright 2024 dergigi).
package org.dergigi.feather

import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.quartz.nip01Core.core.Event
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val highlightGold = Color(0xFFF6DE74)
private data class HighlightPassage(val text: AnnotatedString, val ranges: List<IntRange>)

// Retain original offsets while treating runs of whitespace like the web renderer.
private fun normalized(text: String): Pair<String, List<Int>> {
    val out = StringBuilder()
    val offsets = ArrayList<Int>()
    text.forEachIndexed { index, c ->
        if (!c.isWhitespace() || out.lastOrNull() != ' ') {
            out.append(if (c.isWhitespace()) ' ' else c); offsets.add(index)
        }
    }
    return out.toString() to offsets
}

private fun passage(content: AnnotatedString, context: AnnotatedString, compact: Boolean): HighlightPassage {
    val (haystack, offsets) = normalized(context.text)
    val needle = normalized(content.text).first.trim()
    val ranges = mutableListOf<IntRange>()
    if (needle.isNotEmpty()) {
        var cursor = 0
        while (cursor < haystack.length) {
            val start = haystack.indexOf(needle, cursor)
            if (start < 0) break
            val end = offsets[start + needle.length - 1] + 1
            ranges.add(offsets[start] until end)
            cursor = start + needle.length
        }
    }
    // Never lose the actual highlight when a publisher supplied unrelated context.
    if (ranges.isEmpty()) {
        val text = if (compact && content.length > 1400) content.subSequence(0, 1400) + AnnotatedString("…") else content
        return HighlightPassage(text, if (text.isEmpty()) emptyList() else listOf(0 until text.length))
    }
    if (!compact || context.length <= 1600) return HighlightPassage(context, ranges)
    val start = (ranges.first().first - 180).coerceAtLeast(0)
    val end = (start + 1600).coerceAtMost(context.length)
    val prefix = if (start > 0) "…" else ""
    val text = AnnotatedString(prefix) + context.subSequence(start, end) + AnnotatedString(if (end < context.length) "…" else "")
    return HighlightPassage(text, ranges.mapNotNull {
        val a = maxOf(it.first, start); val b = minOf(it.last + 1, end)
        if (a < b) (a - start + prefix.length) until (b - start + prefix.length) else null
    })
}

@Composable
internal fun HighlightContent(event: Event, profiles: Map<String, Profile>, compact: Boolean, onReference: (NostrContent.Reference) -> Unit) {
    val context = LocalContext.current
    val currentNavigate by rememberUpdatedState(onReference)
    val uriHandler = remember(context) { object : UriHandler {
        override fun openUri(uri: String) {
            if (Uri.parse(uri).scheme?.lowercase() in listOf("http", "https")) context.openWebLink(uri)
            else NostrContent.decode(uri)?.let(currentNavigate)
        }
    } }
    // Standalone cards have no Markdown composable providing the library's
    // typography/annotator locals. Use the non-composable API with explicit styles.
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceContainerHighest)
    val linkListener = remember(uriHandler) { LinkInteractionListener { link ->
        if (link is LinkAnnotation.Url) uriHandler.openUri(link.url)
    } }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val textColor = MaterialTheme.colorScheme.onSurface
    val rendered by produceState<Pair<AnnotatedString, AnnotatedString?>?>(null, event.id, compact, profiles, style, codeStyle, linkStyle, textColor) {
        value = withContext(Dispatchers.Default) {
            try {
                fun markdown(raw: String) = markdownDocument(raw.withoutObjectMarkers(), style, linkStyle, codeStyle, linkListener)
                val passage = passage(markdown(event.content.trim()), markdown(event.tagValue("context").orEmpty()), compact)
                fun mentions(text: AnnotatedString): AnnotatedString {
                    val protected = text.getLinkAnnotations(0, text.length)
                    return buildAnnotatedString {
                        var offset = 0
                        NostrContent.tokens(text.text, event).filter { token -> protected.none { it.start < token.end && it.end > token.start } }.forEach { token ->
                            append(text.subSequence(offset, token.start))
                            val start = length
                            val ref = token.reference
                            if (ref != null) {
                                withLink(LinkAnnotation.Clickable(ref.copyableLink(token.text), TextLinkStyles(linkStyle)) { currentNavigate(ref) }) {
                                    append(if (ref.hashtag != null) token.text else if (ref.profile) "@${profiles[ref.id]?.name ?: FeatherRepository.shortNpub(ref.id)}" else "↗ ${FeatherRepository.shortNpub(ref.id)}")
                                }
                            } else if (token.url != null) {
                                withLink(LinkAnnotation.Url(token.url, TextLinkStyles(linkStyle))) { append(token.text) }
                            }
                            text.spanStyles.filter { it.start < token.end && it.end > token.start }.forEach { addStyle(it.item, start, length) }
                            offset = token.end
                        }
                        append(text.subSequence(offset, text.length))
                    }
                }
                val styled = mentions(buildAnnotatedString {
                    append(passage.text)
                    passage.ranges.forEach { addStyle(SpanStyle(background = highlightGold.copy(alpha = 0.30f), color = textColor), it.first, it.last + 1) }
                })
                val comment = event.tagValue("comment")?.takeIf { it.isNotBlank() }?.let {
                    val parsed = mentions(markdown(it))
                    if (compact && parsed.length > 600) parsed.subSequence(0, 600) + AnnotatedString("…") else parsed
                }
                styled to comment
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Formatting and remapping remote annotations are also fallible.
                Log.w("HighlightContent", "Unable to render highlight ${event.id}", error)
                val raw = event.content.withoutObjectMarkers().trim().let { if (compact && it.length > 1400) it.take(1400) + "…" else it }
                AnnotatedString(raw, SpanStyle(background = highlightGold.copy(alpha = 0.30f),
                    color = textColor)) to
                    event.tagValue("comment")?.takeIf(String::isNotBlank)?.let { AnnotatedString((if (compact) it.take(600) else it).withoutObjectMarkers()) }
            }
        }
    }
    val (styled, comment) = rendered ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        comment?.let {
            LinkText(it, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        LinkText(styled, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            modifier = Modifier.fillMaxWidth())

        val source = remember(event.id) { highlightSource(event) }
        val author = highlightAuthor(event)
        if (source != null) {
            val linkStyle = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
            LinkText(buildAnnotatedString {
                append("Highlight from ")
                if (source.label in listOf("blog post", "nostr post")) append("a ")
                withLink(LinkAnnotation.Clickable(source.reference?.copyableLink() ?: source.url ?: source.label, linkStyle) { source.reference?.let(currentNavigate) ?: source.url?.let(uriHandler::openUri) }) { append(source.label) }
                if (author != null) {
                    append(" by ")
                    withLink(LinkAnnotation.Clickable("nostr:${Protocol.npub(author)}", linkStyle) { currentNavigate(NostrContent.Reference(author, profile = true)) }) { append(profiles[author]?.name ?: FeatherRepository.shortNpub(author)) }
                }
            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
