@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.CircularProgressIndicator
import android.content.Context
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class VideoFeedback(val kind: String, val value: String)

/** Native Media3 controls, with gestures on the video surface only. */
internal class GestureVideoView(
    context: Context,
    private val playback: VisibleVideoPlayer,
    private val fullscreenGestures: Boolean,
    private val feedback: (VideoFeedback) -> Unit,
) : PlayerView(context) {
    var brightnessWindow: Window? = null
    var systemBrightness = .5f
    private var nativeTouch = false
    private var axis = 0
    private var startX = 0f
    private var startY = 0f
    private var startPosition = 0L
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var startVolume = 0f
    private var lastVolumeStep = -1
    private var startBrightness = .5f
    private var seekPosition: Long? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private val controls = intArrayOf(
        androidx.media3.ui.R.id.exo_bottom_bar,
        androidx.media3.ui.R.id.exo_center_controls,
        androidx.media3.ui.R.id.exo_progress,
        androidx.media3.ui.R.id.exo_minimal_controls,
    )
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val engine = player ?: return false
            if (!engine.isCurrentMediaItemSeekable) return false
            val delta = if (e.x < width / 2f) -10_000L else 10_000L
            engine.seekTo(clampPosition(engine.currentPosition + delta))
            feedback(VideoFeedback("seek", if (delta < 0) "−10 s" else "+10 s"))
            return true
        }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!fullscreenGestures || current.pointerCount != 1) return false
            val dx = current.x - startX
            val dy = current.y - startY
            if (axis == 0 && maxOf(abs(dx), abs(dy)) > slop) axis = if (abs(dx) > abs(dy)) 1 else 2
            if (axis == 0) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            hideController()
            if (axis == 1) {
                val engine = player ?: return false
                if (!engine.isCurrentMediaItemSeekable || engine.duration <= 0) return false
                // One screen width scrubs up to two minutes; commit once when the finger lifts.
                val span = minOf(engine.duration, 120_000L)
                val target = clampPosition(startPosition + (dx / width.coerceAtLeast(1) * span).toLong())
                seekPosition = target
                feedback(VideoFeedback("seek", timeLabel(target)))
            } else if (startX < width / 2f) {
                val brightness = (startBrightness - dy / height.coerceAtLeast(1)).coerceIn(.01f, 1f)
                brightnessWindow?.let { window -> window.attributes = window.attributes.apply { screenBrightness = brightness } }
                feedback(VideoFeedback("brightness", "${(brightness * 100).roundToInt()}%"))
            } else {
                val volume = (startVolume - dy / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val step = (volume * maxVolume).roundToInt()
                // A volume gesture is an explicit request for sound, even after muted autoplay.
                // Adjust the device media stream as well as clearing the player's mute/gain.
                playback.setVolume(1f)
                if (step != lastVolumeStep) {
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
                    lastVolumeStep = step
                }
                val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                feedback(VideoFeedback("volume", "${(actual * 100f / maxVolume).roundToInt()}%"))
            }
            return true
        }
    })

    init {
        controllerAutoShow = false
        setKeepContentOnPlayerReset(true)
        controllerShowTimeoutMs = 3_000
        setShowNextButton(false)
        setShowPreviousButton(false)
        setShowSubtitleButton(fullscreenGestures)
        setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            nativeTouch = isControllerFullyVisible && controls.any { id ->
                val control = findViewById<View>(id)
                val rect = android.graphics.Rect()
                control != null && control.isShown && control.getGlobalVisibleRect(rect) && rect.contains(event.rawX.toInt(), event.rawY.toInt())
            }
            axis = 0; seekPosition = null
            startX = event.x; startY = event.y
            startPosition = player?.currentPosition ?: 0L
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            lastVolumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            startVolume = lastVolumeStep.toFloat() / maxVolume
            startBrightness = brightnessWindow?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: systemBrightness
        }
        if (nativeTouch) return super.dispatchTouchEvent(event)
        if (event.pointerCount > 1) { seekPosition = null; return true }
        gesture.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            seekPosition?.let { player?.seekTo(it) }
            seekPosition = null
            parent?.requestDisallowInterceptTouchEvent(false)
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            seekPosition = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun clampPosition(position: Long): Long {
        val duration = player?.duration ?: 0L
        return if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
    }
}

private fun timeLabel(ms: Long): String {
    val seconds = ms / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal fun VideoGestureFeedback(value: VideoFeedback?, modifier: Modifier = Modifier) {
    if (value == null) return
    Row(modifier.background(Color.Black.copy(alpha = .7f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(when (value.kind) {
            "brightness" -> R.drawable.brightness
            "volume" -> R.drawable.volume_up
            else -> R.drawable.seek
        }), value.kind, tint = Color.White)
        Text(value.value, color = Color.White)
    }
}

@Composable
internal fun FullscreenVideo(playback: VisibleVideoPlayer) {
    val engine = playback.player ?: return
    Dialog(onDismissRequest = playback::exitFullscreen, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CopyableMessageHost()
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val context = LocalContext.current
        val systemBrightness by produceState(.5f, context) {
            value = withContext(Dispatchers.IO) {
                runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(.5f)
            }
        }
        var feedback by remember { mutableStateOf<VideoFeedback?>(null) }
        var feedbackTick by remember { mutableIntStateOf(0) }
        var controlsVisible by remember { mutableStateOf(true) }
        LaunchedEffect(feedbackTick) { delay(900); feedback = null }
        DisposableEffect(window, engine) {
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            engine.addListener(listener)
            listener.onIsPlayingChanged(engine.isPlaying)
            onDispose { engine.removeListener(listener); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()) {
            AndroidView(
                factory = { GestureVideoView(it, playback, true) { value -> feedback = value; feedbackTick++ }.apply {
                    brightnessWindow = window
                    // Media3 also invokes this listener when state is set programmatically.
                    setFullscreenButtonClickListener { isFullscreen -> if (!isFullscreen) playback.exitFullscreen() }
                    setFullscreenButtonState(true)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> controlsVisible = visibility == View.VISIBLE })
                    playback.bindFullscreen(this)
                    showController()
                } },
                modifier = Modifier.fillMaxSize(),
                update = { it.systemBrightness = systemBrightness },
                onRelease = playback::unbindFullscreen,
            )
            if (controlsVisible) {
                IconButton(onClick = playback::exitFullscreen, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Default.Close, stringResource(R.string.ui_exit_fullscreen), tint = Color.White)
                }
            }
            VideoActions(playback, Modifier.align(Alignment.TopEnd).statusBarsPadding(), controlsVisible)
            VideoGestureFeedback(feedback, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun VideoActions(playback: VisibleVideoPlayer, modifier: Modifier, visible: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val url = playback.player?.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
    var busy by remember { mutableStateOf(false) }
    fun toast(message: String) { AppMessages.show(message) }
    fun export(share: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    if (share) VideoStore.share(context, url) else { VideoStore.save(context, url); null }
                }
                if (intent != null) context.startActivity(Intent.createChooser(intent, "Share video"))
                else AppMessages.notify(context, "Saved video to Movies.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { toast(e.message ?: "Couldn't download video. Please try again.") }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export(false) else AppMessages.notify(context, "Storage permission is needed to save videos on this Android version.")
    }
    if (visible) Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        IconButton(onClick = playback::toggleMute) {
            Icon(painterResource(if (playback.muted) R.drawable.volume_off else R.drawable.volume_up),
                if (playback.muted) stringResource(R.string.ui_unmute_video) else stringResource(R.string.ui_mute_video), tint = Color.White)
        }
        IconButton(enabled = !busy, onClick = {
            if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) export(false)
            else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }) { Icon(painterResource(R.drawable.download), stringResource(R.string.ui_download_video), tint = Color.White) }
        IconButton(enabled = !busy, onClick = { export(true) }) { Icon(Icons.Default.Share, stringResource(R.string.ui_share_video), tint = Color.White) }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Video URL", url))
        }) { Icon(painterResource(R.drawable.copy), stringResource(R.string.ui_copy_video_url), tint = Color.White) }
    }
}
