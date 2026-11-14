package org.dergigi.feather

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import coil3.BitmapImage
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Size
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Small local derivatives share the image LRU and storage budget with their originals. */
class AvatarThumbnails(private val disk: DiskCache) : Interceptor {
    private data class Write(val key: String, val bitmap: Bitmap)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Write>(capacity = 4)
    private val pending = mutableSetOf<String>()
    private var closed = false
    private val worker = scope.launch {
        for (write in queue) {
            try {
                save(write.key, write.bitmap)
                save(latestKey(write.key.substringBeforeLast(":avatar:")), write.bitmap)
            }
            finally { synchronized(pending) { pending.remove(write.key) } }
        }
    }

    private fun enqueue(key: String, image: BitmapImage) {
        // Keep a shared bitmap alive, never copy/recycle Coil's image on the display path.
        if (!image.shareable) return
        synchronized(pending) {
            if (closed || !pending.add(key)) return
            if (queue.trySend(Write(key, image.bitmap)).isFailure) pending.remove(key)
        }
        // A full queue skips this optional derivative; another load can try again.
    }

    suspend fun close() {
        synchronized(pending) { closed = true }
        queue.cancel()
        scope.cancel()
        worker.join() // Finish/abort an active editor before the shared disk cache closes.
        synchronized(pending) { pending.clear() }
    }

    private fun latestKey(url: String) = "$url:avatar:latest:thumbnail-256-v1"

    /** A display-only fallback: it never prevents the normal weekly refresh. */
    suspend fun cachedPlaceholder(url: String, week: Long): Bitmap? = withContext(Dispatchers.IO) {
        // Also reuse thumbnails written before the stable fallback key was introduced.
        val keys = listOf(latestKey(url)) + (0L..8L).map { "$url:avatar:${week - it}:thumbnail-256-v1" }
        for (key in keys) {
            ensureActive()
            val bitmap = runCatching {
                disk.openSnapshot(key)?.use { snapshot ->
                    BitmapFactory.decodeFile(snapshot.data.toString())
                }
            }.getOrNull()
            if (bitmap != null) return@withContext bitmap
        }
        null
    }

    override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
        val request = chain.request
        val originalKey = request.diskCacheKey?.takeIf { ":avatar:" in it } ?: return chain.proceed()
        val key = "$originalKey:thumbnail-256-v1"
        // Coil requires the original size resolver inside interceptors; resize via withSize below.
        val sized = request.newBuilder().maxBitmapSize(Size(512, 512)).allowHardware(false).memoryCacheKey(key).build()
        val snapshot = withContext(Dispatchers.IO) { runCatching { disk.openSnapshot(key) }.getOrNull() }
        if (snapshot != null) {
            try {
                val cached = chain.withRequest(sized.newBuilder().data(snapshot.data.toFile()).diskCachePolicy(CachePolicy.DISABLED).build())
                    .withSize(Size(256, 256)).proceed()
                if (cached is SuccessResult) return cached
            } finally { withContext(NonCancellable + Dispatchers.IO) { snapshot.close() } }
            withContext(Dispatchers.IO) { runCatching { disk.remove(key) } }
        }
        val result = chain.withRequest(sized).withSize(Size(256, 256)).proceed()
        if (result is SuccessResult) (result.image as? BitmapImage)?.let { enqueue(key, it) }
        return result
    }

    private suspend fun save(key: String, bitmap: Bitmap) {
        val editor = runCatching { disk.openEditor(key) }.getOrNull() ?: return
        try {
            val edge = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - edge) / 2
            val top = (bitmap.height - edge) / 2
            val thumbnail = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                Canvas(thumbnail).drawBitmap(bitmap, Rect(left, top, left + edge, top + edge), Rect(0, 0, 256, 256), Paint(Paint.FILTER_BITMAP_FLAG))
                editor.metadata.toFile().writeText("")
                editor.data.toFile().outputStream().use { check(thumbnail.compress(Bitmap.CompressFormat.WEBP, 85, it)) }
                currentCoroutineContext().ensureActive()
                editor.commit()
            } finally { thumbnail.recycle() }
        } catch (e: Exception) {
            runCatching { editor.abort() }
            if (e is CancellationException) throw e
            // A cache write must never prevent showing the already decoded original.
        }
    }
}
