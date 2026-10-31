package org.dergigi.feather

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import coil3.size.Dimension
import coil3.size.Size
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

private data class PrefetchImage(val url: String, val avatar: Boolean, val small: Boolean = false)
private data class PrefetchInput(val notes: List<Event>, val profiles: Map<String, Profile>, val visible: List<String>, val embedded: Map<String, Event>, val playing: Set<String>)
private data class PrefetchPlan(val upcoming: List<PrefetchImage>, val visible: Set<PrefetchImage>, val videos: List<String>, val playing: Set<String>)

/** Parsing and URL planning run off the UI thread. Only two speculative requests run at once. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun PrefetchMedia(notes: List<Event>, profiles: Map<String, Profile>, state: LazyListState, widthPx: Int, embedded: Map<String, Event> = emptyMap(), itemNotes: Map<String, List<String>> = emptyMap()) {
    val media = LocalMedia.current
    val context = LocalContext.current.applicationContext
    val currentNotes by rememberUpdatedState(notes)
    val currentProfiles by rememberUpdatedState(profiles)
    val currentEmbedded by rememberUpdatedState(embedded)
    val currentItemNotes by rememberUpdatedState(itemNotes)
    LaunchedEffect(state, media.resources, media.active, widthPx, media.playback, media.playback?.fullscreen) {
        val resources = media.resources ?: return@LaunchedEffect
        if (!media.active || media.playback == null || media.playback.fullscreen) return@LaunchedEffect
        val prefetch = MediaPrefetcher(this, resources, context, widthPx)
        val videos = VideoPrefetcher(this, resources)
        snapshotFlow { PrefetchInput(currentNotes, currentProfiles, state.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.flatMap { currentItemNotes[it] ?: listOf(it) }, currentEmbedded, media.playback.preparedUrls) }
            .mapLatest { input -> withContext(Dispatchers.Default) { plan(input) } }
            .distinctUntilChanged()
            .collect { prefetch.update(it); videos.update(it.videos, it.playing) }
    }
}

private fun plan(input: PrefetchInput): PrefetchPlan {
    val visibleIds = input.visible.toSet()
    val first = input.notes.indexOfFirst { it.id in visibleIds }
    val last = input.notes.indexOfLast { it.id in visibleIds }
    fun images(event: Event): List<PrefetchImage> {
        val note = if (event.kind in listOf(6, 16)) input.embedded[repostTarget(event)?.id] ?: return emptyList() else event
        if (note.kind in listOf(3, 30000, 30617)) return emptyList()
        if (note.kind == 30023) return listOfNotNull(MediaContent.mediaUrl(note.tagValue("image"))?.let { PrefetchImage(it, false) })
        if (note.kind == 32267) return listOfNotNull(MediaContent.mediaUrl(note.tagValue("icon"))?.let { PrefetchImage(it, false, small = true) })
        return listOfNotNull(input.profiles[note.pubKey]?.picture?.let { PrefetchImage(it, true) },
            customReactionUrl(note)?.let { PrefetchImage(it, false, small = true) }) +
            MediaContent.attachments(note).mapNotNull {
                val url = if (it.kind == MediaContent.Kind.IMAGE) it.url else it.preview
                url?.let { value -> PrefetchImage(value, false) }
            } + YouTubeLinks.parse(note.content).map { PrefetchImage(it.thumbnail, false) }
    }
    val ahead = input.notes.drop(last + 1).take(12)
    val behind = input.notes.take(first.coerceAtLeast(0)).takeLast(2).asReversed()
    val upcoming = ahead.take(6) + behind
    val videoNotes = input.notes.filter { it.id in visibleIds } + ahead + behind
    val videoUrls = videoNotes.flatMap { event ->
        val note = if (event.kind in listOf(6, 16)) input.embedded[repostTarget(event)?.id] else event
        if (note == null) emptyList() else {
            val originals = listOf(note) + NostrContent.quotes(note).mapNotNull { input.embedded[it.id] }
            originals.flatMap { MediaContent.attachments(it).filter { it.kind == MediaContent.Kind.VIDEO }.map { it.url } }
        }
    }.distinct().filter { it !in input.playing }.take(3)
    return PrefetchPlan(upcoming.flatMap(::images).filter { MediaContent.mediaUrl(it.url) != null }.distinct().take(40),
        input.notes.filter { it.id in visibleIds }.flatMap(::images).toSet(), videoUrls, input.playing)
}

/** All queue bookkeeping stays on the effect's main dispatcher; fetching/decoding runs on IO. */
private class MediaPrefetcher(
    private val scope: CoroutineScope,
    private val resources: MediaStore.Resources,
    private val context: Context,
    private val widthPx: Int,
) {
    private val running = mutableMapOf<PrefetchImage, Job>()
    private val finished = linkedMapOf<PrefetchImage, Long>()
    private var wanted: List<PrefetchImage> = emptyList()

    fun update(plan: PrefetchPlan) {
        wanted = plan.upcoming
        val useful = (plan.upcoming + plan.visible).toSet()
        // Keep an in-flight fetch when its note enters the viewport or stays ahead.
        running.filterKeys { it !in useful }.values.toList().forEach { it.cancel() }
        pump()
    }

    private fun pump() {
        if (!scope.isActive) return
        val now = SystemClock.elapsedRealtime()
        wanted.filter { it !in running && (finished[it] ?: 0) <= now }.take((2 - running.size).coerceAtLeast(0)).forEach { image ->
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val week = System.currentTimeMillis() / (7 * 24 * 60 * 60 * 1000L)
                        resources.prefetchImages.execute(ImageRequest.Builder(context).data(image.url)
                            .diskCacheKey(if (image.avatar) "${image.url}:avatar:$week" else image.url)
                            .size(if (image.avatar) Size(256, 256) else if (image.small) Size(128, 128) else Size(Dimension(widthPx.coerceAtLeast(1)), Dimension.Undefined)).build())
                    }
                    if (!image.avatar && !image.small && result is SuccessResult) ImageRatios.put(image.url, result.image.width, result.image.height)
                    finished.remove(image)
                    finished[image] = SystemClock.elapsedRealtime() + if (result is SuccessResult) 300_000 else 30_000
                    while (finished.size > 128) finished.remove(finished.keys.first())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finished[image] = SystemClock.elapsedRealtime() + 30_000
                    while (finished.size > 128) finished.remove(finished.keys.first())
                } finally {
                    running.remove(image)
                    pump()
                }
            }
            running[image] = job
            job.start()
        }
    }
}
