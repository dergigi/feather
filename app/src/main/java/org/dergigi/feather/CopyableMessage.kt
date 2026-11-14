package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** At most three transient notices; countdowns survive screen/modal host changes. */
internal object AppMessages {
    enum class Style { INFO, WARNING }
    data class Message(val id: Long, val text: String, val details: String, val actionLabel: String? = null, val action: (() -> Unit)? = null, val tapLabel: String? = null, val onTap: (() -> Unit)? = null, val style: Style = Style.WARNING, val remaining: Float = 1f, val paused: Boolean = false)
    data class Stack(val messages: List<Message> = emptyList(), val expandedId: Long? = null)
    val current = MutableStateFlow(Stack())
    data class ComposeAnchor(val owner: Any, val root: android.view.View, val bounds: IntRect)
    val composeAnchor = MutableStateFlow<ComposeAnchor?>(null)
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


    private fun enqueue(message: Message) {
        current.value = Stack((current.value.messages + message).takeLast(3), message.id)
    }
    @Synchronized fun show(text: String, details: String = text, style: Style = Style.WARNING) {
        if (text.isBlank()) return
        // Only merge plain notices: identical labels can belong to different undo/draft actions.
        val duplicate = current.value.messages.firstOrNull {
            it.action == null && it.onTap == null && it.text == text && it.details == details && it.style == style
        }
        if (duplicate != null) {
            val others = current.value.messages.filterNot { it.id == duplicate.id }
            current.value = Stack(others + duplicate.copy(remaining = 1f, paused = false), duplicate.id)
        } else enqueue(Message(++sequence, text, details, style = style))
    }
    @Synchronized fun showAction(text: String, label: String, action: () -> Unit) {
        enqueue(Message(++sequence, text, text, label, action, style = Style.INFO))
    }
    @Synchronized fun showTappable(text: String, details: String, label: String, onTap: () -> Unit): Long {
        val id = ++sequence
        enqueue(Message(id, text, details, tapLabel = label, onTap = onTap))
        return id
    }
    @Synchronized fun toggle(id: Long) {
        val stack = current.value
        if (stack.messages.none { it.id == id }) return
        current.value = stack.copy(expandedId = if (stack.expandedId == id) null else id)
    }
    @Synchronized fun togglePause(id: Long) {
        val stack = current.value
        current.value = stack.copy(messages = stack.messages.map {
            if (it.id == id) it.copy(paused = !it.paused) else it
        })
    }
    @Synchronized fun clearComposeAnchor(owner: Any) {
        if (composeAnchor.value?.owner === owner) composeAnchor.value = null
    }
    @Synchronized fun elapse(host: Any, id: Long, elapsedMillis: Long, timeoutMillis: Long) {
        if (topHost.value !== host) return
        val stack = current.value
        val notice = stack.messages.firstOrNull { it.id == id } ?: return
        if (notice.paused) return
        val remaining = (notice.remaining - elapsedMillis.toFloat() / timeoutMillis.coerceAtLeast(1)).coerceAtLeast(0f)
        if (remaining == 0f) dismiss(id)
        else current.value = stack.copy(messages = stack.messages.map { if (it.id == id) it.copy(remaining = remaining) else it })
    }
    @Synchronized fun dismiss(id: Long) {
        val stack = current.value
        current.value = stack.copy(messages = stack.messages.filterNot { it.id == id },
            expandedId = stack.expandedId.takeUnless { it == id })
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
    val stack by AppMessages.current.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = top === host && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val ids = stack.messages.map { it.id }
    LaunchedEffect(ids) { heights.keys.retainAll(ids.toSet()) }
    if (!active) return
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    val defaultHeight = with(density) { 56.dp.roundToPx() }
    // Separate, tightly sized popup windows leave the feed beside collapsed circles touchable.
    val newestFirst = stack.messages.asReversed()
    newestFirst.forEachIndexed { index, shown ->
        key(shown.id) {
            val offset = newestFirst.take(index).sumOf { (heights[it.id] ?: defaultHeight) + gap }
            FloatingNotice(host, shown, stack.expandedId == shown.id, offset, stack.messages.size,
                onHeight = { heights[shown.id] = it })
        }
    }
}

@Composable
private fun FloatingNotice(host: Any, shown: AppMessages.Message, expanded: Boolean, bottomOffset: Int,
    count: Int, onHeight: (Int) -> Unit) {
    val accessibility = LocalAccessibilityManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scroll = rememberScrollState()
    val interacting = pressed || scroll.isScrollInProgress
    val timeout = accessibility?.calculateRecommendedTimeoutMillis(6_000L,
        containsIcons = true, containsText = true, containsControls = true) ?: 6_000L
    LaunchedEffect(shown.id, shown.paused, interacting, timeout) {
        if (shown.paused || interacting || timeout == Long.MAX_VALUE) return@LaunchedEffect
        var previous = SystemClock.uptimeMillis()
        while (true) {
            delay(50)
            val now = SystemClock.uptimeMillis()
            AppMessages.elapse(host, shown.id, now - previous, timeout)
            previous = now
        }
    }
    val context = LocalContext.current
    fun copy() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Feather message", shown.details))
        if (shown.action == null) AppMessages.dismiss(shown.id)
    }
    val warning = shown.style == AppMessages.Style.WARNING
    val expandLabel = stringResource(if (warning) R.string.ui_show_warning else R.string.ui_show_information)
    val collapseLabel = stringResource(R.string.ui_collapse_message)
    val dismissLabel = stringResource(R.string.ui_dismiss_message)
    val timerLabel = stringResource(if (shown.paused || interacting || timeout == Long.MAX_VALUE)
        R.string.ui_notice_countdown_paused else R.string.ui_notice_countdown_running)
    val timerAction = stringResource(if (shown.paused) R.string.ui_resume_notice_countdown else R.string.ui_pause_notice_countdown)
    val canCopy = shown.details.isNotBlank() && shown.details != shown.text
    val density = LocalDensity.current
    val root = LocalView.current.rootView
    val anchor by AppMessages.composeAnchor.collectAsStateWithLifecycle()
    val fab = anchor?.takeIf { it.root === root }?.bounds
    val direction = LocalLayoutDirection.current
    val window = LocalWindowInfo.current.containerSize
    val safe = WindowInsets.safeDrawing
    val left = safe.getLeft(density, direction)
    val right = safe.getRight(density, direction)
    val top = safe.getTop(density)
    val bottom = maxOf(safe.getBottom(density), WindowInsets.ime.getBottom(density))
    val edge = with(density) { 24.dp.roundToPx() }
    val base = with(density) { 80.dp.roundToPx() }
    val topGap = with(density) { 16.dp.roundToPx() }
    val gap = with(density) { 12.dp.roundToPx() }
    val position = remember(left, right, top, bottom, edge, base, topGap, bottomOffset, fab) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
                if (layoutDirection == LayoutDirection.Ltr) left + edge
                else windowSize.width - right - edge - popupContentSize.width,
                ((fab?.bottom?.coerceAtMost(windowSize.height - bottom)
                    ?: (windowSize.height - bottom - base)) - bottomOffset - popupContentSize.height)
                    .coerceAtLeast(top + topGap))
        }
    }
    // Use the actual FAB bounds on this window; modal windows retain the safe fallback.
    val widthPixels = if (fab != null) {
        if (direction == LayoutDirection.Ltr) fab.left - gap - left - edge
        else window.width - right - edge - fab.right - gap
    } else window.width - left - right - 2 * edge - with(density) { 68.dp.roundToPx() }
    val expandedWidth = with(density) { widthPixels.toDp() }.coerceAtLeast(56.dp)
    val stackBottom = fab?.bottom?.coerceAtMost(window.height - bottom) ?: (window.height - bottom - base)
    val textHeight = with(density) { (stackBottom - top - topGap).toDp() - 64.dp * (count - 1) }
        .coerceIn(56.dp, 200.dp)
    val tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Popup(popupPositionProvider = position,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.onSizeChanged { onHeight(it.height) }
                .animateContentSize(tween(220), alignment = Alignment.BottomStart)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(CustomAccessibilityAction(dismissLabel) {
                        AppMessages.dismiss(shown.id); true
                    })
                }) {
            Row(modifier = if (expanded) Modifier.width(expandedWidth) else Modifier,
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(3.dp).clearAndSetSemantics { }) {
                        val stroke = 2.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(tint.copy(alpha = 0.12f), -90f, 360f, false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                        drawArc(tint.copy(alpha = if (shown.paused || interacting) 0.35f else 0.8f), -90f, 360f * shown.remaining, false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    IconButton(onClick = { AppMessages.toggle(shown.id) }, interactionSource = interaction,
                        modifier = Modifier.size(56.dp).semantics { stateDescription = "${shown.text}. $timerLabel" }) {
                        Icon(if (warning) Icons.Default.Warning else Icons.Default.Info,
                            if (expanded) collapseLabel else expandLabel, tint = tint)
                    }
                }
                if (expanded) {
                    Text(shown.text, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).heightIn(max = textHeight)
                            .verticalScroll(scroll)
                            .clickable(interactionSource = interaction, indication = LocalIndication.current,
                                onClickLabel = timerAction) { AppMessages.togglePause(shown.id) }
                            .semantics { stateDescription = timerLabel }
                            .padding(start = 12.dp, end = 8.dp, top = 16.dp, bottom = 16.dp))
                    Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (canCopy) IconButton(onClick = ::copy, interactionSource = interaction) {
                            Icon(painterResource(R.drawable.copy), stringResource(R.string.ui_copy_message))
                        }
                        if (shown.onTap != null) IconButton(onClick = {
                            AppMessages.dismiss(shown.id)
                            shown.onTap.invoke()
                        }, interactionSource = interaction) {
                            Icon(painterResource(R.drawable.reply), shown.tapLabel)
                        }
                        if (shown.action != null) IconButton(onClick = {
                            AppMessages.dismiss(shown.id)
                            shown.action.invoke()
                        }, interactionSource = interaction) {
                            Icon(painterResource(R.drawable.undo), shown.actionLabel)
                        }
                    }
                }
            }
        }
    }
}
