package org.dergigi.feather

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.util.Locale

internal val LocalTranslations = staticCompositionLocalOf<Translations?> { null }
internal data class TranslatedNote(val language: String, val text: String, val showing: Boolean = true)
internal data class TranslationSheet(val text: String? = null, val error: Boolean = false, val install: Boolean = false)

/** Small session cache, shared across reader screens; translated text is never an Event. */
internal class Translations(private val context: Context, private val scope: CoroutineScope) {
    private val notes = mutableStateMapOf<String, TranslatedNote>()
    var pending by mutableStateOf<String?>(null)
        private set
    var sheet by mutableStateOf<TranslationSheet?>(null)
        private set
    private var selectionGeneration = 0
    private var request: Job? = null
    private var translatingSelection = false
    fun result(note: Event): TranslatedNote? = notes[note.id]?.takeIf { it.language == Locale.getDefault().language }
    fun text(note: Event): String? = result(note)?.takeIf { it.showing }?.text
    fun toggle(note: Event) {
        result(note)?.let { notes[note.id] = it.copy(showing = !it.showing) } ?: translate(note)
    }
    fun dismiss() {
        sheet = null
        selectionGeneration++
        if (translatingSelection) request?.cancel()
    }
    fun translate(note: Event, selection: String? = null) {
        if (pending != null) return
        if (selection == null && result(note) != null) { toggle(note); return }
        val source = selection ?: note.content
        if (source.length > 20_000) {
            AppMessages.notify(context, context.getString(R.string.ui_translate_shorter))
            return
        }
        val input = source.trim()
        if (input.isBlank()) return
        val language = Locale.getDefault().language
        val generation = ++selectionGeneration
        pending = note.id
        if (selection != null) sheet = TranslationSheet()
        translatingSelection = selection != null
        request = scope.launch {
            try {
                val translated = translateOffline(context, input, language)
                if (selection != null) {
                    if (generation == selectionGeneration) sheet = TranslationSheet(text = translated)
                } else {
                    // Bound both entries and retained characters, including large article results.
                    notes.remove(note.id)
                    while (notes.isNotEmpty() && (notes.size >= 32 || notes.values.sumOf { it.text.length } + translated.length > 200_000))
                        notes.remove(notes.keys.first())
                    notes[note.id] = TranslatedNote(language, translated)
                }
            } catch (_: TranslatorUnavailable) {
                if (generation == selectionGeneration) sheet = TranslationSheet(error = true, install = true)
            } catch (_: TimeoutCancellationException) {
                if (generation == selectionGeneration) sheet = TranslationSheet(error = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == selectionGeneration) sheet = TranslationSheet(error = true)
            } finally { pending = null; translatingSelection = false }
        }
    }
    fun openTranslator() {
        val launch = context.packageManager.getLaunchIntentForPackage(TRANSLATOR_PACKAGE)
        if (launch != null) runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.openWebLink(TRANSLATOR_WEBSITE) }
        else context.openWebLink(TRANSLATOR_WEBSITE)
    }
}

@Composable
internal fun TranslationControl(note: Event) {
    val translations = LocalTranslations.current ?: return
    val result = translations.result(note)
    if (translations.pending == note.id) {
        Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (result != null) {
        IconButton(onClick = { translations.toggle(note) }, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.translate), stringResource(if (result.showing) R.string.ui_show_original else R.string.ui_translate),
                Modifier.size(18.dp), tint = if (result.showing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranslationHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val translations = remember(context) { Translations(context, scope) }
    CompositionLocalProvider(LocalTranslations provides translations) {
        content()
        translations.sheet?.let { sheet ->
            ModalBottomSheet(onDismissRequest = translations::dismiss) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
                    .heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (sheet.error) {
                        Text(stringResource(if (sheet.install) R.string.ui_translator_install else R.string.ui_translator_setup),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { translations.openTranslator(); translations.dismiss() }) {
                            Text(stringResource(if (sheet.install) R.string.ui_install_translator else R.string.ui_open_translator))
                        }
                    } else if (sheet.text != null) {
                        SelectionContainer { Text(sheet.text, style = MaterialTheme.typography.bodyLarge) }
                    } else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
