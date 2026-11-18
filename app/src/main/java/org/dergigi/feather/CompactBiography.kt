package org.dergigi.feather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CompactBiography(text: AnnotatedString, hasQuotes: Boolean, onExpand: () -> Unit) {
    InlineExpandableText(text, 3, false, onExpand, MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        "Expand biography", "Collapse biography", hasQuotes)
}

/** Keep the chevron beside the ellipsis, preserving links and selectable text. */
@Composable
internal fun InlineExpandableText(text: AnnotatedString, lines: Int, expanded: Boolean, onToggle: () -> Unit,
    style: TextStyle, expandLabel: String, collapseLabel: String, forceExpandable: Boolean = false,
    inlineContent: Map<String, InlineTextContent> = emptyMap()) {
    val measurer = rememberTextMeasurer()
    val placeholder = Placeholder(24.sp, 24.sp, PlaceholderVerticalAlign.Center)
    BoxWithConstraints {
        val width = constraints.maxWidth
        val preview = remember(text, expanded, forceExpandable, width, style, measurer, lines, inlineContent) {
            val limits = Constraints(maxWidth = width)
            val full = measurer.measure(text, style = style, maxLines = lines, constraints = limits,
                placeholders = inlinePlaceholders(text, inlineContent))
            if (!full.hasVisualOverflow && !forceExpandable) text else {
                fun candidate(end: Int): AnnotatedString {
                    var cut = end
                    // Never cut through an emoji's alternate text/placeholder range.
                    inlinePlaceholders(text, inlineContent).firstOrNull { cut > it.start && cut < it.end }?.let { cut = it.start }
                    if (cut > 0 && cut < text.length && text[cut - 1].isHighSurrogate() && text[cut].isLowSurrogate()) cut--
                    while (cut > 0 && text[cut - 1].isWhitespace()) cut--
                    return buildAnnotatedString {
                        append(text.subSequence(0, cut))
                        if (end < text.length) append("…") else append(" ")
                        appendInlineContent("expand", "\uFFFC")
                    }
                }
                if (expanded) candidate(text.length) else {
                    fun fits(value: AnnotatedString): Boolean = !measurer.measure(value, style = style,
                        maxLines = lines, constraints = limits,
                        placeholders = inlinePlaceholders(value, inlineContent) +
                            AnnotatedString.Range(placeholder, value.length - 1, value.length)).hasVisualOverflow
                    var low = 0
                    var high = if (full.hasVisualOverflow) full.getLineEnd(full.lineCount - 1) else text.length
                    while (low < high) {
                        val middle = (low + high + 1) / 2
                        if (fits(candidate(middle))) low = middle else high = middle - 1
                    }
                    candidate(low)
                }
            }
        }
        val label = if (expanded) collapseLabel else expandLabel
        LinkText(preview, style = style, maxLines = if (expanded) Int.MAX_VALUE else lines, inlineContent = inlineContent + mapOf(
            "expand" to InlineTextContent(placeholder) {
                Box(Modifier.clickable(onClickLabel = label, onClick = onToggle), contentAlignment = Alignment.Center) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        ))
    }
}
