package org.dergigi.feather

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

internal fun Context.copyLink(url: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Link", url))
    // Android 13+ already provides its own clipboard confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun LinkAnnotation.copyDestination(): String? = when (this) {
    is LinkAnnotation.Url -> url
    is LinkAnnotation.Clickable -> tag.takeIf {
        it.startsWith("https://", true) || it.startsWith("http://", true) || it.startsWith("nostr:", true) || it.startsWith("blossom:")
    }
    else -> null
}

internal class TextLinkLayout {
    var result: TextLayoutResult? = null

    fun linkAt(position: Offset): LinkAnnotation? {
        val layout = result ?: return null
        if (position.x < 0 || position.y < 0 || position.x >= layout.size.width || position.y >= layout.size.height) return null
        val offset = layout.getOffsetForPosition(position)
        val line = layout.getLineForVerticalPosition(position.y)
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        // The offset is a cursor position: the right half of a glyph can point to the next
        // character. Check both adjacent glyphs, without accepting nearby whitespace.
        for (character in intArrayOf(offset, offset - 1)) {
            if (character !in start until end || character !in layout.layoutInput.text.indices ||
                !layout.getBoundingBox(character).contains(position)) continue
            return layout.layoutInput.text.getLinkAnnotations(character, character + 1).firstOrNull()?.item
                ?.takeIf { it.copyDestination() != null }
        }
        return null
    }
}

/** Intercept only link hits; ordinary text keeps Compose selection, including quote replies. */
@Composable
internal fun linkTextModifier(text: AnnotatedString, layout: TextLinkLayout, resolveDestination: (String) -> String = { it }): Modifier {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val copy: (String) -> Unit = { context.copyLink(it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val copyLatest by rememberUpdatedState(copy)
    val resolveLatest by rememberUpdatedState(resolveDestination)
    val openLatest by rememberUpdatedState<(LinkAnnotation) -> Unit>({ link ->
        link.linkInteractionListener?.onClick(link) ?: if (link is LinkAnnotation.Url) uriHandler.openUri(link.url) else Unit
    })
    val actions = remember(text, context) {
        text.getLinkAnnotations(0, text.length).mapNotNull { range ->
            range.item.copyDestination()?.let { url ->
                url to CustomAccessibilityAction("Copy link: ${text.subSequence(range.start, range.end).text}") {
                    context.copyLink(resolveLatest(url)); true
                }
            }
        }.distinctBy { it.first }.take(64).map { it.second }
    }
    return Modifier.semantics { if (actions.isNotEmpty()) customActions = actions }.pointerInput(layout) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val link = layout.linkAt(down.position) ?: return@awaitEachGesture
            val url = resolveLatest(link.copyDestination() ?: return@awaitEachGesture)
            // Reserve a link press before native link clicks/text selection see it. Movement is
            // not consumed while waiting, so swipes and scrolling can still win at touch slop.
            down.consume()
            val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed } ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                    if (!change.pressed) {
                        change.consume()
                        released = true
                    } else if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
                        return@withTimeoutOrNull false
                    }
                }
                true
            }
            when (tapped) {
                true -> openLatest(link)
                false -> Unit
                null -> {
                    copyLatest(url)
                    // Swallow the release after copying so it cannot open the link too.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
    }
}

@Composable
internal fun LinkText(text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, onTextLayout: (TextLayoutResult) -> Unit = {},
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = emptyMap()) {
    val layout = remember { TextLinkLayout() }
    Text(text, modifier.then(linkTextModifier(text, layout)), color = color, style = style,
        maxLines = maxLines, overflow = overflow, inlineContent = inlineContent,
        onTextLayout = { layout.result = it; onTextLayout(it) })
}
