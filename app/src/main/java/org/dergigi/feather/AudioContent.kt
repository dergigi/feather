@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val audioSpeeds = listOf(.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
internal fun audioSpeedIcon(speed: Float) = when (speed) {
    .75f -> R.drawable.audio_speed_075
    1.25f -> R.drawable.audio_speed_125
    1.5f -> R.drawable.audio_speed_15
    1.75f -> R.drawable.audio_speed_175
    2f -> R.drawable.audio_speed_2
    2.5f -> R.drawable.audio_speed_25
    3f -> R.drawable.audio_speed_3
    else -> R.drawable.audio_speed_1
}
internal fun audioSpeedLabel(speed: Float) = "${speed.toString().removeSuffix(".0")}×"

internal val voiceKinds = listOf(1222, 1244)
internal data class AudioInfo(val url: String?, val mime: String?, val title: String?, val description: String,
    val image: String?, val duration: Long, val waveform: List<Float>)

/** Called off the display thread; never retrieve audio just to discover its duration. */
internal fun audioInfo(event: Event): AudioInfo {
    val url = MediaContent.mediaUrl(event.content.trim())
    val imeta = event.tags.firstOrNull { it.firstOrNull() == "imeta" && it.contains("url $url") }
        ?.drop(1)?.mapNotNull { val split = it.indexOf(' '); if (split > 0) it.take(split) to it.substring(split + 1) else null }?.toMap().orEmpty()
    val duration = (imeta["duration"] ?: event.tagValue("duration"))?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0 && it <= 604800 }?.let { (it * 1000).toLong() } ?: 0L
    val waveform = imeta["waveform"].orEmpty().splitToSequence(' ').take(100).mapNotNull(String::toFloatOrNull)
        .filter { it.isFinite() && it >= 0 }.toList()
    return AudioInfo(url, imeta["m"]?.takeIf { it.startsWith("audio/") },
        event.tagValue("title")?.take(300), "",
        MediaContent.mediaUrl(event.tagValue("image")), duration, waveform)
}

/** One application-scoped controller; the service owns the player and notification. */
class AudioPlayback internal constructor(private val context: Context) {
    var player by mutableStateOf<androidx.media3.session.MediaController?>(null)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    internal fun setSpeed(value: Float) {
        if (value !in audioSpeeds) return
        speed = value
        player?.setPlaybackSpeed(value)
    }
    var activeId by mutableStateOf<String?>(null)
        private set
    var requested by mutableStateOf(false)
        private set
    var connecting by mutableStateOf(false)
        private set
    internal var sourceNote: Event? = null
        private set
    private data class HeartRequest(val token: String, val note: Event, val owner: String, val created: Long)
    private var heartRequest: HeartRequest? = null
    internal fun requestHeart(owner: String): String? {
        val note = sourceNote ?: return null
        val token = java.util.UUID.randomUUID().toString()
        heartRequest = HeartRequest(token, note, owner, android.os.SystemClock.elapsedRealtime())
        return token
    }
    internal fun pendingHeartToken(owner: String?): String? = heartRequest?.takeIf {
        it.owner == owner && it.note.id == sourceNote?.id && android.os.SystemClock.elapsedRealtime() - it.created < 60_000
    }?.token
    internal fun consumeHeart(token: String?): Pair<Event, String>? {
        val request = heartRequest ?: return null
        if (token != request.token) return null
        heartRequest = null
        return (request.note to request.owner).takeIf {
            sourceNote?.id == request.note.id && android.os.SystemClock.elapsedRealtime() - request.created < 60_000
        }
    }
    private var connection: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    internal fun toggle(id: String, info: AudioInfo, author: String, note: Event?) {
        if (info.url == null || connecting) return
        val current = player
        if (current != null) { toggleConnected(current, id, info, author, note); return }
        connecting = true
        val future = androidx.media3.session.MediaController.Builder(context,
            androidx.media3.session.SessionToken(context, android.content.ComponentName(context, AudioPlaybackService::class.java)))
            .setListener(object : androidx.media3.session.MediaController.Listener {
                override fun onDisconnected(controller: androidx.media3.session.MediaController) {
                    if (player === controller) { player = null; activeId = null; requested = false; connection = null }
                }
            }).buildAsync()
        connection = future
        future.addListener({
            connecting = false
            try {
                val controller = future.get()
                player = controller
                // Preserve a speed chosen before playback; reconnecting adopts the service's speed.
                if (controller.currentMediaItem == null) controller.setPlaybackSpeed(speed)
                speed = controller.playbackParameters.speed
                controller.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        requested = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                        activeId = player.currentMediaItem?.mediaId
                        speed = player.playbackParameters.speed
                    }
                })
                toggleConnected(controller, id, info, author, note)
            } catch (error: Exception) {
                androidx.media3.session.MediaController.releaseFuture(future)
                connection = null; player = null; requested = false
                AppMessages.show("Audio: ${walletErrorText(error.cause?.message ?: error.message ?: "Could not connect to player")}")
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun toggleConnected(current: Player, id: String, info: AudioInfo, author: String, note: Event?) {
        sourceNote = note
        AudioPlaybackService.sourceChanged()
        if (current.currentMediaItem?.mediaId != id) {
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(info.title ?: "Voice note").setArtist(author)
                .setArtworkUri(info.image?.let(android.net.Uri::parse)).build()
            current.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(info.url!!).setMimeType(info.mime).setMediaMetadata(metadata).build())
            current.prepare(); current.play()
        } else if (current.playWhenReady) current.pause()
        else {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            if (current.playbackState == Player.STATE_IDLE) current.prepare()
            current.play()
        }
        activeId = id
        requested = current.playWhenReady
    }
}

@Composable
internal fun AudioContent(note: Event, compact: Boolean, author: String = FeatherRepository.shortKey(note.pubKey)) {
    val info by produceState<AudioInfo?>(null, note.id) { value = withContext(Dispatchers.Default) { audioInfo(note) } }
    val data = info ?: return
    AudioPlayerContent(note.id, data, compact, author, sourceNote = note)
}

@Composable
internal fun AudioPlayerContent(id: String, data: AudioInfo, compact: Boolean, author: String, smallArtwork: Boolean = false, onOpenSource: (() -> Unit)? = null, sourceNote: Event? = null) {
    val media = LocalMedia.current
    val audio = media.audio
    val player = audio?.player?.takeIf { audio.activeId == id }
    var position by remember(id) { mutableLongStateOf(0) }
    var duration by remember(id, data.duration) { mutableLongStateOf(data.duration) }
    var buffering by remember(id) { mutableStateOf(false) }
    var dragging by remember(id) { mutableStateOf<Float?>(null) }
    var speedMenu by remember(id) { mutableStateOf(false) }
    LaunchedEffect(player, media.active) {
        if (player == null) { position = 0; buffering = false; return@LaunchedEffect }
        while (media.active) {
            position = player.currentPosition.coerceAtLeast(0)
            if (player.duration > 0) duration = player.duration
            buffering = player.playbackState == Player.STATE_BUFFERING
            delay(250)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (smallArtwork) Row(verticalAlignment = Alignment.Top) {
            Text(data.title ?: author, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 8.dp))
            onOpenSource?.let { open ->
                IconButton(onClick = open) {
                    Icon(painterResource(R.drawable.open_external), stringResource(R.string.ui_open_podcast_source), Modifier.size(18.dp))
                }
            }
        }
        if (!smallArtwork) data.image?.let { PodcastArtwork(it, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), data.title ?: "Podcast artwork") }
        if (!smallArtwork) data.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) }
        if (data.description.isNotBlank()) Text(data.description, style = MaterialTheme.typography.bodyLarge,
            maxLines = if (compact) 4 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        if (data.url != null) {
            val controls: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp)) {
                    if (data.waveform.isNotEmpty()) {
                        val color = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp)) {
                            val max = data.waveform.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                            val step = size.width / data.waveform.size
                            data.waveform.forEachIndexed { index, amplitude ->
                                val h = (amplitude / max * size.height).coerceAtLeast(2f)
                                drawLine(color, Offset((index + .5f) * step, (size.height - h) / 2),
                                    Offset((index + .5f) * step, (size.height + h) / 2), strokeWidth = (step * .55f).coerceAtLeast(1f))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = media.active && media.resources != null && audio != null && !audio.connecting, onClick = { audio?.toggle(id, data, author, sourceNote) }) {
                            if (audio?.connecting == true || (buffering && audio?.requested == true)) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else if (player != null && audio?.requested == true) Icon(painterResource(R.drawable.pause), stringResource(R.string.ui_pause_audio))
                            else Icon(Icons.Default.PlayArrow, stringResource(R.string.ui_play_audio))
                        }
                        Slider(value = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { player?.seekTo((it * duration).toLong()) }; dragging = null },
                            enabled = player != null && duration > 0, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(enabled = audio != null && !audio.connecting,
                                onClick = { speedMenu = true },
                                modifier = Modifier.semantics { contentDescription = "Playback speed ${audioSpeedLabel(audio?.speed ?: 1f)}" }) {
                                Text(audioSpeedLabel(audio?.speed ?: 1f), style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                audioSpeeds.forEach { speed ->
                                    DropdownMenuItem(text = { Text(audioSpeedLabel(speed)) },
                                        trailingIcon = { if (audio?.speed == speed) Icon(Icons.Default.Check, stringResource(R.string.ui_selected)) },
                                        onClick = { audio?.setSpeed(speed); speedMenu = false })
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (smallArtwork) Text(author, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        Text(if (duration > 0) "${audioTime(position)} / ${audioTime(duration)}" else audioTime(position),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
            }
            if (smallArtwork && data.image != null) {
                // Measure controls first: artwork matches their height without intrinsic
                // measurements of the slider or a second-frame size/state update.
                Layout(content = {
                    Box { PodcastArtwork(data.image, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), data.title ?: "Podcast artwork") }
                    Box { controls() }
                }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
                    val coverWidth = minOf(72.dp.roundToPx(), constraints.maxWidth / 4)
                    val gap = 8.dp.roundToPx()
                    val playerWidth = (constraints.maxWidth - coverWidth - gap).coerceAtLeast(0)
                    val controlsPlaceable = measurables[1].measure(constraints.copy(minWidth = playerWidth,
                        maxWidth = playerWidth, minHeight = 0))
                    val cover = measurables[0].measure(Constraints.fixed(coverWidth, controlsPlaceable.height))
                    layout(constraints.maxWidth, controlsPlaceable.height) {
                        cover.placeRelative(0, 0)
                        controlsPlaceable.placeRelative(coverWidth + gap, 0)
                    }
                }
            } else controls()
        }
    }
}

@Composable
private fun PodcastArtwork(url: String, modifier: Modifier, description: String) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (!loaded) Icon(painterResource(R.drawable.photos), null, Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        RemoteImage(url, Modifier.fillMaxSize(), description, onImageSize = { _, _ -> loaded = true })
    }
}

private fun audioTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
