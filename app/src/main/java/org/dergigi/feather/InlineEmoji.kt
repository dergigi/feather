package org.dergigi.feather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val emojiShortcode = Regex(":([a-zA-Z0-9_-]{1,64}):")
private val emojiName = Regex("[a-zA-Z0-9_-]{1,64}")
private data class InlineEmojiMatch(val start: Int, val end: Int, val shortcode: String, val url: String)
internal data class EmojiTextContent(val text: AnnotatedString, val inlineContent: Map<String, InlineTextContent>)

/** Only the event's own NIP-30 tags define images; names alone never trigger a lookup. */
private fun inlineEmojiMatches(text: AnnotatedString, event: Event): List<InlineEmojiMatch> {
    val urls = linkedMapOf<String, String>()
    for (tag in event.tags) {
        if (tag.size < 3 || tag[0] != "emoji" || !emojiName.matches(tag[1])) continue
        val url = tag[2].takeIf { it.length <= 2048 }?.let(MediaContent::secureUrl) ?: continue
        urls.putIfAbsent(tag[1], url)
        if (urls.size == 32) break
    }
    if (urls.isEmpty()) return emptyList()
    return emojiShortcode.findAll(text.text).take(128).mapNotNull { match ->
        val url = urls[match.groupValues[1]] ?: return@mapNotNull null
        val start = match.range.first
        val end = match.range.last + 1
        if (text.getLinkAnnotations(start, end).isNotEmpty() || text.spanStyles.any {
            it.start < end && it.end > start && it.item.fontFamily == FontFamily.Monospace
        }) return@mapNotNull null
        InlineEmojiMatch(start, end, match.value, url)
    }.take(64).toList()
}

/** Alternate text stays the full shortcode for selection/copy and failed image loads. */
@Composable
internal fun noteEmojiContent(text: AnnotatedString, note: Event): EmojiTextContent {
    val media = LocalMedia.current
    val canLoad = media.resources != null
    var matches by remember(text, note.id) { mutableStateOf(emptyList<InlineEmojiMatch>()) }
    var failed by remember(note.id, media.active) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(text, note.id) {
        matches = withContext(Dispatchers.Default) { inlineEmojiMatches(text, note) }
    }
    return remember(note.id, text, matches, failed, canLoad, media.active) {
        val visible = if (canLoad) matches.filter { it.url !in failed } else emptyList()
        if (visible.isEmpty()) EmojiTextContent(text, emptyMap()) else {
            val content = visible.distinctBy { it.shortcode }.associate { match ->
                val id = "emoji:${match.shortcode}"
                id to InlineTextContent(Placeholder(1.5.em, 1.5.em, PlaceholderVerticalAlign.TextCenter)) {
                    var loaded by remember(match.url) { mutableStateOf(false) }
                    val fontSize = LocalTextStyle.current.fontSize.let { if (it.isSp) it else 16.sp }
                    val width = with(LocalDensity.current) { (fontSize * 1.5f).roundToPx() }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (!loaded) Text("…")
                        RemoteImage(match.url, Modifier.fillMaxSize(), match.shortcode, crop = false, widthPx = width,
                            onImageSize = { _, _ -> loaded = true }, onFailure = { failed = failed + match.url })
                    }
                }
            }
            val rendered = buildAnnotatedString {
                var cursor = 0
                for (match in visible) {
                    append(text.subSequence(cursor, match.start))
                    appendInlineContent("emoji:${match.shortcode}", match.shortcode)
                    cursor = match.end
                }
                append(text.subSequence(cursor, text.length))
            }
            EmojiTextContent(rendered, content)
        }
    }
}

/** Use the same placeholder ranges for premeasurement and actual Text layout. */
internal fun inlinePlaceholders(text: AnnotatedString, content: Map<String, InlineTextContent>) =
    text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, text.length).mapNotNull { range ->
        content[range.item]?.let { AnnotatedString.Range(it.placeholder, range.start, range.end) }
    }
