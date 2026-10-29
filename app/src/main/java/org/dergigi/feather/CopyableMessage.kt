package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** One transient message, never an unbounded queue of stale errors. */
internal object AppMessages {
    data class Message(val id: Long, val text: String, val details: String, val actionLabel: String? = null, val action: (() -> Unit)? = null, val tapLabel: String? = null, val onTap: (() -> Unit)? = null)
    val current = MutableStateFlow<Message?>(null)
    val topHost = MutableStateFlow<Any?>(null)
    private val hosts = mutableListOf<Any>()
    private var sequence = 0L
    private val toastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var toast: android.widget.Toast? = null

    fun notify(context: Context, text: String) {
        if (text.isBlank()) return
        val app = context.applicationContext
        toastHandler.post {
            toast?.cancel()
            toast = android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_SHORT).also { it.show() }
        }
    }


    @Synchronized fun show(text: String, details: String = text) {
        if (text.isBlank() || (current.value?.text == text && current.value?.details == details)) return
        current.value = Message(++sequence, text, details)
    }
    @Synchronized fun showAction(text: String, label: String, action: () -> Unit) {
        current.value = Message(++sequence, text, text, label, action)
    }
    @Synchronized fun showTappable(text: String, details: String, label: String, onTap: () -> Unit): Long {
        val id = ++sequence
        current.value = Message(id, text, details, tapLabel = label, onTap = onTap)
        return id
    }
    @Synchronized fun dismiss(id: Long) {
        if (current.value?.id == id) current.value = null
    }
    @Synchronized fun attach(host: Any) { hosts.add(host); topHost.value = hosts.last() }
    @Synchronized fun detach(host: Any) { hosts.remove(host); topHost.value = hosts.lastOrNull() }
}

/** Also installed inside modal windows so errors remain tappable above their content. */
@Composable
internal fun CopyableMessageHost() {
    val host = remember { Any() }
    DisposableEffect(host) {
        AppMessages.attach(host)
        onDispose { AppMessages.detach(host) }
    }
    val top by AppMessages.topHost.collectAsStateWithLifecycle()
    val message by AppMessages.current.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = top === host && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message?.id, active) {
        val shown = message ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        delay(accessibility?.calculateRecommendedTimeoutMillis(6_000L,
            containsIcons = true, containsText = true, containsControls = true) ?: 6_000L)
        AppMessages.dismiss(shown.id)
    }
    if (!active) return
    val shown = message ?: return
    val context = LocalContext.current
    fun copy() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Feather message", shown.details))
        AppMessages.dismiss(shown.id)
    }
    Popup(alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Snackbar(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .padding(16.dp).widthIn(max = 560.dp).clickable(onClickLabel = shown.tapLabel ?: stringResource(R.string.ui_copy_message)) {
                val onTap = shown.onTap
                if (onTap == null) copy()
                else { AppMessages.dismiss(shown.id); onTap() }
            },
            action = {
                if (shown.action != null) TextButton(onClick = {
                    AppMessages.dismiss(shown.id)
                    shown.action.invoke()
                }) { Text(shown.actionLabel.orEmpty()) }
                else IconButton(onClick = ::copy) { Icon(painterResource(R.drawable.copy), stringResource(R.string.ui_copy_message)) }
            }) {
            Text(shown.text, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}
