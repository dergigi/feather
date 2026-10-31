@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore as AndroidMediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.util.UUID

/** Called on IO. Reuse cached video ranges and stream missing bytes without buffering the file in RAM. */
internal object VideoStore {
    private val exportLock = Mutex()
    private data class Download(val file: File, val mime: String, val name: String)

    private suspend fun fetch(context: Context, url: String): Download {
        require(MediaContent.mediaUrl(url) != null) { "Only HTTPS videos are supported." }
        val media = (context.applicationContext as FeatherApplication).media
        val resources = media.resources.value ?: throw IOException("Media cache unavailable.")
        val maxBytes = minOf(media.budgetMb.value.toLong(), 512L) * 1024 * 1024
        val folder = File(context.cacheDir, "shared-videos").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        // Keep fresh shares readable by receiving apps, but bound temporary export storage too.
        val available = (maxBytes - (folder.listFiles()?.sumOf { it.length() } ?: 0L)).coerceAtLeast(0L)
        if (available == 0L) throw IOException("Temporary video storage is full. Please try again later.")
        val file = File(folder, "feather-${UUID.randomUUID()}.tmp")
        val source = resources.videoSource.createDataSource()
        try {
            val length = source.open(DataSpec(Uri.parse(url)))
            if (length > available) throw IOException("Video exceeds available temporary storage.")
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == C.RESULT_END_OF_INPUT) break
                    total += count
                    if (total > available) throw IOException("Video exceeds available temporary storage.")
                    output.write(buffer, 0, count)
                }
                if (total == 0L) throw IOException("Empty video response.")
            }
            currentCoroutineContext().ensureActive()
            val metadata = MediaMetadataRetriever()
            val mime = try {
                metadata.setDataSource(file.absolutePath)
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.takeIf { it.startsWith("video/") }
                    ?: throw IOException("This URL did not return a downloadable video file.")
            } finally { metadata.release() }
            val suffix = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
            return Download(file, mime, "${file.nameWithoutExtension}.$suffix")
        } catch (e: Exception) { file.delete(); throw e }
        finally { source.close() }
    }

    suspend fun save(context: Context, url: String) = exportLock.withLock {
        val video = fetch(context, url)
        try {
            val values = ContentValues().apply {
                put(AndroidMediaStore.Video.Media.DISPLAY_NAME, video.name)
                put(AndroidMediaStore.Video.Media.MIME_TYPE, video.mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(AndroidMediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Feather")
                    put(AndroidMediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(AndroidMediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create video in Movies.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    video.file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            output.write(buffer, 0, size)
                        }
                    }
                } ?: throw IOException("Could not save video.")
                if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(AndroidMediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
        } finally { video.file.delete() }
    }

    suspend fun share(context: Context, url: String): Intent = exportLock.withLock {
        val video = fetch(context, url)
        val namedFile = File(video.file.parentFile, video.name)
        if (!video.file.renameTo(namedFile)) { video.file.delete(); throw IOException("Could not prepare video for sharing.") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", namedFile)
        Intent(Intent.ACTION_SEND).apply {
            type = video.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
