package org.dergigi.feather

import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.quartz.nip01Core.core.Event

internal val LocalSelectionReply = staticCompositionLocalOf<(Event, String) -> Unit> { { _, _ -> } }
private data object TranslateSelectionAction
private data object ReplySelectionAction

/** Keep Android's selection actions, with reply and offline translation scoped to this event. */
@Composable
internal fun NoteSelection(note: Event, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    key(note.id) {
        val selection = rememberSelectionState()
        val reply = LocalSelectionReply.current
        val replyLabel = stringResource(R.string.ui_reply)
        val translations = LocalTranslations.current
        val translateLabel = stringResource(R.string.ui_translate)
        val shorterTranslation = stringResource(R.string.ui_translate_shorter)
        val shorterQuote = stringResource(R.string.ui_shorter_quote)
        SelectionContainer(state = selection, modifier = Modifier.appendTextContextMenuComponents {
            if (selection.selectedTexts.any { it.text.isNotBlank() }) {
                if (translations != null && translations.pending == null) item(TranslateSelectionAction, translateLabel, R.drawable.translate) {
                    val parts = selection.selectedTexts
                    if (parts.sumOf { it.length.toLong() } + (parts.size - 1).coerceAtLeast(0) > 20_000) {
                        AppMessages.notify(context, shorterTranslation)
                    } else {
                        val text = parts.joinToString("\n") { it.text }
                        close()
                        selection.clear()
                        translations.translate(note, text)
                    }
                }
                item(ReplySelectionAction, replyLabel, R.drawable.reply) {
                    // Bound the copy before joining potentially large article selections on main.
                    val parts = selection.selectedTexts
                    val length = parts.sumOf { it.length.toLong() } + (parts.size - 1).coerceAtLeast(0)
                    if (length > 8000) {
                        AppMessages.notify(context, shorterQuote)
                        return@item
                    }
                    val text = parts.joinToString("\n") { it.text }
                    if (text.isNotBlank()) {
                        close()
                        selection.clear()
                        reply(note, text)
                    }
                }
            }
        }, content = content)
    }
}
