package org.dergigi.feather

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import coil3.BitmapImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Size
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** Artwork uses the shared image LRU, with no extra video download or decoder. */
internal object VideoArtwork {
    private suspend fun encode(bitmap: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            output.toByteArray()
        }
    }

    suspend fun fallback(context: Context): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            canvas.drawColor(if (dark) Color.rgb(15, 24, 29) else Color.rgb(232, 244, 252))
            ContextCompat.getDrawable(context, R.drawable.feather)?.mutate()?.apply {
                setBounds(128, 128, 384, 384)
                draw(canvas)
            }
            encode(bitmap)
        } finally { bitmap.recycle() }
    }

    private suspend fun image(context: Context, resources: MediaStore.Resources, url: String?): ByteArray? {
        val safe = MediaContent.mediaUrl(url) ?: return null
        return try { withTimeoutOrNull(4_000) {
            val result = resources.images.execute(ImageRequest.Builder(context).data(safe)
                .size(512, 512).maxBitmapSize(Size(512, 512)).allowHardware(false).build())
            (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.let { encode(it.bitmap) }
        } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    suspend fun load(context: Context, clip: VisibleVideoPlayer, onFrame: (ByteArray) -> Unit): ByteArray? {
        val app = context.applicationContext as FeatherApplication
        val resources = app.media.resources.value ?: return null
        image(context, resources, clip.preview)?.let { return it }
        val key = "${clip.url}:lockscreen-frame-512-v1"
        val cached = withContext(Dispatchers.IO) {
            runCatching {
                resources.imageDisk.openSnapshot(key)?.use { snapshot ->
                    snapshot.data.toFile().takeIf { it.length() in 1..512_000 }?.readBytes()
                }
            }.getOrNull()
        }
        if (cached != null) return cached
        // The surface may still be attaching or waiting for the first decoded frame.
        repeat(8) {
            val frame = withContext(Dispatchers.Main.immediate) { clip.artworkFrame() }
            if (frame != null) {
                val bytes = try { encode(frame) } finally { frame.recycle() }
                withContext(Dispatchers.Main.immediate) { onFrame(bytes) }
                withContext(Dispatchers.IO) {
                    val editor = runCatching { resources.imageDisk.openEditor(key) }.getOrNull()
                    if (editor != null) try {
                        editor.metadata.toFile().writeText("")
                        editor.data.toFile().writeBytes(bytes)
                        currentCoroutineContext().ensureActive()
                        editor.commit()
                    } catch (e: Exception) {
                        runCatching { editor.abort() }
                        if (e is CancellationException) throw e
                    }
                }
                return bytes
            }
            delay(250)
        }
        val avatar = withContext(Dispatchers.Main.immediate) {
            clip.sourceNote?.let { app.repository.state.value.profiles[it.pubKey]?.picture }
        }
        return image(context, resources, avatar)
    }
}
