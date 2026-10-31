@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.size.Dimension
import coil3.size.Size
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.*


data class MediaEnvironment(val resources: MediaStore.Resources?, val active: Boolean, val playback: VisibleVideos?, internal val audio: AudioPlayback? = null)
val LocalMedia = staticCompositionLocalOf { MediaEnvironment(null, false, null) }

@Composable
fun MediaHost(store: MediaStore, reading: Boolean, content: @Composable () -> Unit) {
    val resources by store.resources.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val galleryVisible = LocalGalleryVisible.current
    val active = reading && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !galleryVisible
    val context = LocalContext.current
    val playback = remember(resources, reading) { resources?.takeIf { reading }?.let { VisibleVideos(context, it) } }
    val audio = (context.applicationContext as FeatherApplication).audio
    DisposableEffect(playback, audio, lifecycle, galleryVisible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { playback?.enabled(false, keepSelected = !galleryVisible && audio?.requested != true); store.background() }
            if (event == Lifecycle.Event.ON_START) playback?.enabled(!galleryVisible && audio?.requested != true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(playback) { onDispose { playback?.close() } }
    val audioRequested = audio?.requested == true
    SideEffect { playback?.enabled(active && !audioRequested, keepSelected = !lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !galleryVisible && !audioRequested) }
    CompositionLocalProvider(LocalMedia provides MediaEnvironment(resources, active, playback, audio)) {
        content()
        playback?.fullscreenPlayer?.let { FullscreenVideo(it) }
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier, description: String? = null, crop: Boolean = true, avatar: Boolean = false, widthPx: Int? = null, fillWidth: Boolean = false, onImageSize: ((Int, Int) -> Unit)? = null) {
    val media = LocalMedia.current
    val context = LocalContext.current
    val safeUrl = remember(url) { MediaContent.mediaUrl(url) }
    val loader = media.resources?.images ?: return
    if (safeUrl == null) return
    val week = if (avatar) System.currentTimeMillis() / (7 * 24 * 60 * 60 * 1000L) else 0L
    var retries by remember(safeUrl, media.active) { mutableIntStateOf(0) }
    var failed by remember(safeUrl, media.active) { mutableStateOf(false) }
    LaunchedEffect(failed, media.active, retries) {
        if (failed && media.active && retries < 2) { delay(if (retries == 0) 1_000 else 4_000); failed = false; retries++ }
    }
    val request = remember(safeUrl, media.active, week, retries, widthPx) {
        ImageRequest.Builder(context).data(safeUrl)
            .diskCacheKey(if (avatar) "$safeUrl:avatar:$week" else safeUrl)
            .networkCachePolicy(if (media.active) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .apply { widthPx?.let { size(Size(Dimension(it.coerceAtLeast(1)), Dimension.Undefined)) } }
            .build()
    }
    var decoded by remember(safeUrl) { mutableStateOf<coil3.Image?>(null) }
    var visible by remember(safeUrl) { mutableStateOf(false) }
    val view = LocalView.current
    ImageAnimation(decoded, visible && media.active && media.playback?.fullscreen != true)
    key(safeUrl, media.active, retries) {
        AsyncImage(model = request, imageLoader = loader, contentDescription = description, modifier = modifier.onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = bounds.bottom > 0 && bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
            },
            onError = { failed = true }, onSuccess = {
                failed = false
                decoded = it.result.image
                onImageSize?.invoke(it.result.image.width, it.result.image.height)
            },
            contentScale = when { fillWidth -> ContentScale.FillWidth; crop -> ContentScale.Crop; else -> ContentScale.Fit })
    }
}

@Composable
fun NoteMedia(note: Event, attachments: List<MediaContent.Attachment>, mediaKey: String = note.id) {
    val media = LocalMedia.current
    val openGallery = LocalOpenGallery.current
    val videoRatios by VideoRatios.ratios.collectAsState()
    val gallery = remember(note.id) { MediaContent.attachments(note, limit = 32).filter { it.kind == MediaContent.Kind.IMAGE }.map { it.url } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        attachments.forEach { attachment ->
            key(attachment.url) {
                val identity = "$mediaKey:${attachment.url}"
                if (attachment.kind == MediaContent.Kind.AUDIO) {
                    AudioPlayerContent("audio:${note.id}:${attachment.url}",
                        AudioInfo(attachment.url, attachment.mime, null, "", null, 0L, emptyList()),
                        compact = true, author = FeatherRepository.shortKey(note.pubKey), sourceNote = note)
                } else {
                val videos = media.playback
                val playback = videos?.clip(identity)
                DisposableEffect(identity, videos) { onDispose { videos?.remove(identity) } }
                if (attachment.kind == MediaContent.Kind.IMAGE) {
                    InlineImage(attachment) { openGallery(gallery, gallery.indexOf(attachment.url), note) }
                } else Box(Modifier.fillMaxWidth().aspectRatio(videoRatios[attachment.url] ?: attachment.ratio).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                    .onGloballyPositioned { coordinates ->
                        if (attachment.kind == MediaContent.Kind.VIDEO) videos?.report(identity, attachment.url, coordinates.boundsInWindow(), coordinates.size.height.toFloat(), attachment.mime, note, attachment.preview)
                    }) {
                        attachment.preview?.let { RemoteImage(it, Modifier.fillMaxSize(), attachment.alt ?: "Video preview") }
                        if (playback != null && playback.player != null) key(playback) {
                            var feedback by remember(identity) { mutableStateOf<VideoFeedback?>(null) }
                            var feedbackTick by remember(identity) { mutableIntStateOf(0) }
                            var controlsVisible by remember(identity) { mutableStateOf(false) }
                            LaunchedEffect(feedbackTick) { delay(900); feedback = null }
                            AndroidView(factory = { GestureVideoView(it, playback, false) { value -> feedback = value; feedbackTick++ }.apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                setFullscreenButtonClickListener { isFullscreen -> if (isFullscreen) playback.enterFullscreen() }
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> controlsVisible = visibility == android.view.View.VISIBLE })
                            } },
                                modifier = Modifier.fillMaxSize(),
                                onRelease = playback::unbindInline,
                                update = playback::bindInline)
                            VideoGestureFeedback(feedback, Modifier.align(Alignment.Center))
                            if (!controlsVisible) IconButton(onClick = playback::enterFullscreen, modifier = Modifier.align(Alignment.BottomEnd)) {
                                Icon(painterResource(R.drawable.fullscreen), stringResource(R.string.ui_fullscreen_video), tint = Color.White)
                            }
                            IconButton(onClick = playback::toggleMute, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(painterResource(if (playback.muted) R.drawable.volume_off else R.drawable.volume_up), if (playback.muted) stringResource(R.string.ui_unmute_video) else stringResource(R.string.ui_mute_video), tint = Color.White)
                            }
                        }
                }
                }
            }
        }
    }
}

/** Decode at the column width; source dimensions determine height, including portrait photos. */
@Composable
private fun InlineImage(attachment: MediaContent.Attachment, onOpen: () -> Unit) {
    var ratio by remember(attachment.url) { mutableFloatStateOf(ImageRatios.get(attachment.url) ?: attachment.ratio) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        RemoteImage(attachment.url,
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClickLabel = "Open image gallery", onClick = onOpen),
            attachment.alt ?: "Image", crop = false, widthPx = widthPx, fillWidth = true,
            onImageSize = { width, height ->
                ImageRatios.put(attachment.url, width, height)?.let { ratio = it }
            })
    }
}
