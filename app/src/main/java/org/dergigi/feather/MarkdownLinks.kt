package org.dergigi.feather

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.*
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import org.intellij.markdown.MarkdownElementTypes.IMAGE
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL

internal data class MarkdownEmbeds(
    val event: Event,
    val state: FeedState,
    val onReference: (NostrContent.Reference) -> Unit,
)
internal val LocalMarkdownEmbeds = staticCompositionLocalOf<MarkdownEmbeds?> { null }

/** Keep the renderer's formatting, inline images and table layout; add the shared link gesture. */
internal val articleLinkComponents = markdownComponents(
    paragraph = { ArticleLinkText(it.content, it.node, it.typography.paragraph) },
    heading1 = { ArticleLinkText(it.content, it.node, it.typography.h1, heading = true) },
    heading2 = { ArticleLinkText(it.content, it.node, it.typography.h2, heading = true) },
    heading3 = { ArticleLinkText(it.content, it.node, it.typography.h3, heading = true) },
    heading4 = { ArticleLinkText(it.content, it.node, it.typography.h4, heading = true) },
    heading5 = { ArticleLinkText(it.content, it.node, it.typography.h5, heading = true) },
    heading6 = { ArticleLinkText(it.content, it.node, it.typography.h6, heading = true) },
    setextHeading1 = { ArticleLinkText(it.content, it.node, it.typography.h1, heading = true) },
    setextHeading2 = { ArticleLinkText(it.content, it.node, it.typography.h2, heading = true) },
    table = { model ->
        MarkdownTable(model.content, model.node, model.typography.table,
            headerBlock = { content, node, width, style -> ArticleLinkTableRow(content, node, width, style, header = true) },
            rowBlock = { content, node, width, style -> ArticleLinkTableRow(content, node, width, style, header = false) })
    },
)

@Composable
@Suppress("DEPRECATION") // Preserve the renderer's table color compatibility.
private fun ArticleLinkText(content: String, node: ASTNode, style: TextStyle, heading: Boolean = false, tableCell: Boolean = false) {
    val settings = annotatorSettings()
    val child = if (heading) node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT)
        ?: node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT) ?: node else node
    val text = buildAnnotatedString {
        pushStyle(style.toSpanStyle())
        buildMarkdownAnnotatedString(content = content, node = child, annotatorSettings = settings)
        pop()
    }
    val embeds = LocalMarkdownEmbeds.current
    val embeddedTokens = remember(text, embeds?.event?.id, heading, tableCell) {
        if (embeds == null || heading || tableCell) emptyList()
        else {
            val allowed = NostrContent.references(embeds.event).filter {
                !it.profile && it.hashtag == null && it.external == null && it.id != embeds.event.id
            }.take(if (embeds.event.kind == 30023) 16 else 3).map { it.id }.toSet()
            val links = text.getLinkAnnotations(0, text.length).mapNotNull { range ->
                val ref = (range.item as? LinkAnnotation.Url)?.url?.let(NostrContent::decode)
                ref?.let { NostrContent.Token(range.start, range.end, text.text.substring(range.start, range.end), it) }
            }
            (links + NostrContent.tokens(text.text, embeds.event))
                .filter { token -> token.reference?.id in allowed &&
                    text.spanStyles.none { span -> span.start < token.end && span.end > token.start && span.item.fontFamily == FontFamily.Monospace } }
                .sortedBy { it.start }.fold(emptyList<NostrContent.Token>()) { tokens, next ->
                    if (tokens.lastOrNull()?.end?.let { it > next.start } == true) tokens else tokens + next
                }
        }
    }
    if (embeds != null && embeddedTokens.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var cursor = 0
            embeddedTokens.forEach { token ->
                if (token.start > cursor) {
                    val before = text.subSequence(cursor, token.start)
                    if (before.text.isNotBlank()) ArticleAnnotatedText(before, style)
                }
                val reference = token.reference!!
                EmbeddedNote(reference, embeds.state.embedded[reference.id], embeds.state.profiles, reference.id in embeds.state.unavailableEmbeds,
                    embeds.event.id, embeds.onReference)
                cursor = token.end
            }
            if (cursor < text.length) {
                val after = text.subSequence(cursor, text.length)
                if (after.text.isNotBlank()) ArticleAnnotatedText(after, style)
            }
        }
    } else ArticleAnnotatedText(text, style, heading, tableCell)
}

@Composable
@Suppress("DEPRECATION")
private fun ArticleAnnotatedText(source: AnnotatedString, style: TextStyle, heading: Boolean = false, tableCell: Boolean = false) {
    val embeds = LocalMarkdownEmbeds.current
    val open by rememberUpdatedState(embeds?.onReference)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(source, embeds?.event?.id, embeds?.state?.profiles, linkColor) {
        if (embeds == null) source else {
            val mentions = NostrContent.tokens(source.text, embeds.event).filter { token ->
                token.reference?.profile == true &&
                    source.spanStyles.none { it.start < token.end && it.end > token.start && it.item.fontFamily == FontFamily.Monospace } &&
                    source.getLinkAnnotations(token.start, token.end).all { range ->
                        val url = (range.item as? LinkAnnotation.Url)?.url
                        url != null && NostrContent.decode(url)?.id == token.reference?.id
                    }
            }
            if (mentions.isEmpty()) source else buildAnnotatedString {
                var cursor = 0
                mentions.forEach { token ->
                    append(source.subSequence(cursor, token.start))
                    val ref = token.reference!!
                    val start = length
                    withLink(LinkAnnotation.Clickable(ref.copyableLink(token.text),
                        TextLinkStyles(SpanStyle(color = linkColor))) { open?.invoke(ref) }) {
                        append("@${embeds.state.profiles[ref.id]?.name ?: FeatherRepository.shortKey(ref.id)}")
                    }
                    // Preserve surrounding emphasis, font size and other Markdown styling.
                    source.spanStyles.filter { it.start < token.end && it.end > token.start }.forEach {
                        addStyle(it.item, start, length)
                    }
                    cursor = token.end
                }
                append(source.subSequence(cursor, source.length))
            }
        }
    }
    val layout = remember { TextLinkLayout() }
    val references = LocalReferenceLinkHandler.current
    val modifier = (if (heading) Modifier.semantics { heading() } else Modifier)
        .then(linkTextModifier(text, layout) { references.find(it).takeIf(String::isNotEmpty) ?: it })
    if (tableCell) MarkdownBasicText(text = text, modifier = modifier, style = style,
        color = LocalMarkdownColors.current.tableText, maxLines = 1, overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout.result = it })
    else MarkdownText(content = text, modifier = modifier, style = style, onTextLayout = { result, _ -> layout.result = result })
}

@Composable
private fun ArticleLinkTableRow(content: String, node: ASTNode, width: Dp, style: TextStyle, header: Boolean) {
    val padding = LocalMarkdownDimens.current.tableCellPadding
    val components = LocalMarkdownComponents.current
    Row(Modifier.widthIn(width).then(if (header) Modifier.height(IntrinsicSize.Max) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        node.children.filter { it.type == CELL }.forEach { cell ->
            Column(Modifier.padding(padding).weight(1f)) {
                if (cell.children.any { it.type == IMAGE }) {
                    MarkdownElement(node = cell, components = components, content = content, includeSpacer = false)
                } else ArticleLinkText(content, cell, if (header) style.copy(fontWeight = FontWeight.Bold) else style, tableCell = true)
            }
        }
    }
}
