package org.dergigi.feather

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal fun blossomServer(raw: String): String? = raw.toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
}?.toString()?.trimEnd('/')

internal data class ImageUploadProgress(val size: Long? = null, val percent: Int? = null, val stage: String = "Opening attachment", val originalSize: Long? = null)
internal data class UploadedAttachment(val url: String, val originalSize: Long, val size: Long, val compressed: Boolean = false,
    val mime: String = "", val hash: String = "", val width: Int = 0, val height: Int = 0)

/** One bounded attachment at a time; only explicitly selected account servers receive bytes. */
internal class BlossomUpload(private val context: Context) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    data class Result(val url: String, val failures: List<String>)

    suspend fun selectedSize(uri: Uri): Long? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf { size -> size > 0 } else null
            }
        }.getOrNull()
    }

    private fun isScreenshot(uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (!it.moveToFirst()) false else {
                val name = it.getString(0).orEmpty().lowercase()
                listOf("screenshot", "screen_shot", "screen shot", "screen capture", "スクリーンショット").any(name::contains)
            }
        } ?: false
    }.getOrDefault(false)

    suspend fun upload(uri: Uri, servers: List<String>, signer: NostrSignerExternal, requests: SignerRequests, onProgress: (ImageUploadProgress) -> Unit, imagesOnly: Boolean = false, onUploaded: suspend (UploadedAttachment) -> Unit): Result = withContext(Dispatchers.IO) {
        require(servers.isNotEmpty()) { "No Blossom servers found on your profile (kind 10063)." }
        // MIME types from file providers are hints, not reliable media identification.
        // Stage at most the video limit, then inspect the bytes before signing or uploading.
        val stagingLimit = 250L * 1024 * 1024
        selectedSize(uri)?.let { require(it <= stagingLimit) { "Attachment exceeds 250 MB" } }
        onProgress(ImageUploadProgress(stage = "Clearing temporary files"))
        context.cacheDir.listFiles()?.filter { it.name.startsWith("blossom-") && it.name.endsWith(".image") }?.forEach { it.delete() }
        onProgress(ImageUploadProgress(stage = "Creating temporary file"))
        val original = File.createTempFile("blossom-", ".image", context.cacheDir)
        val file = File.createTempFile("blossom-", ".image", context.cacheDir)
        try {
            var size = 0L
            onProgress(ImageUploadProgress(stage = "Opening attachment"))
            context.contentResolver.openInputStream(uri)?.use { input ->
                onProgress(ImageUploadProgress(stage = "Reading attachment"))
                var reportedSize = 0L
                original.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        require(size <= stagingLimit) { "Attachment exceeds 250 MB" }
                        output.write(buffer, 0, read)
                        if (size - reportedSize >= 256 * 1024) {
                            onProgress(ImageUploadProgress(size, stage = "Reading attachment"))
                            reportedSize = size
                        }
                    }
                }
            } ?: error("Could not read selected attachment")
            require(size > 0) { "Selected attachment is empty" }
            onProgress(ImageUploadProgress(size, stage = "Checking media format"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.absolutePath, bounds)
            val image = bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true
            require(!imagesOnly || image) { "Picture posts require images" }
            val label = if (image) "image" else "video"
            val maxBytes = (if (image) 20L else 250L) * 1024 * 1024
            val originalSize = size
            onProgress(ImageUploadProgress(size, stage = "Removing metadata"))
            val prepared = if (image) ImagePrivacy.clean(original, file, bounds.outMimeType!!, preferLossless = isScreenshot(uri)) {
                onProgress(ImageUploadProgress(originalSize, stage = "Compressing image"))
            }
                else ImagePrivacy.Result(VideoPrivacy.clean(original, file)) // Rejects files without a real video track.
            val mime = prepared.mime
            // Describe the exact uploaded bytes after rotation/resizing, without decoding pixels.
            val uploadedBounds = if (image) BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, this)
            } else null
            val width = uploadedBounds?.outWidth?.coerceAtLeast(0) ?: 0
            val height = uploadedBounds?.outHeight?.coerceAtLeast(0) ?: 0
            original.delete()
            currentCoroutineContext().ensureActive()
            size = file.length()
            require(size in 1..maxBytes) { "Processed $label exceeds ${maxBytes / 1024 / 1024} MB" }
            fun progress(stage: String, percent: Int? = null) = onProgress(ImageUploadProgress(size, percent, stage, originalSize.takeIf { prepared.compressed }))
            progress("Hashing $label")
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val now = System.currentTimeMillis() / 1000 - 1
            val tags = (listOf(arrayOf("t", "upload"), arrayOf("x", hash), arrayOf("expiration", (now + 1800).toString())) +
                servers.map { arrayOf("server", it.toHttpUrlOrNull()!!.host) }.distinctBy { it[1] }).toTypedArray()
            val content = "Upload $label to your Blossom servers"
            progress("Waiting for signer")
            val auth = withTimeout(180_000) { requests.run { signer.sign<Event>(now, 24242, tags, content) } }
            require(auth.pubKey == signer.pubKey && auth.kind == 24242 && auth.createdAt == now &&
                auth.content == content && auth.tags.contentDeepEquals(tags) && auth.verify()) { "Invalid Blossom upload authorization" }
            // Deployed Blossom servers decode standard padded Base64 (without line breaks).
            val token = "Nostr " + Base64.encodeToString(auth.toJson().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            var first: String? = null
            val failures = mutableListOf<String>()
            for (server in servers) {
                currentCoroutineContext().ensureActive()
                progress(if (first == null) "Connecting" else "Mirroring")
                val url = try {
                    val source = first
                    if (source == null) put(server, "upload", progressBody(file, mime) { percent -> progress(if (percent == 99) "Waiting for server" else "Uploading", percent) }, token, hash, size)
                    else try {
                        put(server, "mirror", JSONObject().put("url", source).toString().toRequestBody("application/json".toMediaType()), token, hash, size)
                    } catch (e: CancellationException) { throw e }
                    catch (e: BlossomHttpFailure) {
                        if (e.code !in listOf(404, 405, 501)) throw e
                        put(server, "upload", progressBody(file, mime) { percent -> progress(if (percent == 99) "Waiting for server" else "Uploading", percent) }, token, hash, size)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    failures += "${server.toHttpUrlOrNull()!!.host}: ${walletErrorReason(e)}"
                    continue
                }
                progress("Uploaded", 100)
                if (first == null) { first = url; onUploaded(UploadedAttachment(url, originalSize, size, prepared.compressed, mime, hash, width, height)) }
            }
            Result(first ?: error("Media upload failed: ${failures.joinToString("; ")}"), failures)
        } finally { original.delete(); file.delete() }
    }

    private suspend fun put(server: String, path: String, body: RequestBody, token: String, hash: String, size: Long): String {
        val request = Request.Builder().url("$server/$path").header("Authorization", token)
            .header("X-SHA-256", hash).put(body).build()
        val call = http.newCall(request)
        call.timeout().timeout(if (path == "mirror") 15 else 300, TimeUnit.SECONDS)
        return call.awaitUpload().use { response ->
            if (!response.isSuccessful) {
                val reason = response.header("X-Reason")?.takeIf { it.isNotBlank() }
                    ?: response.peekBody(4096).string().let { raw ->
                        val json = runCatching { JSONObject(raw) }.getOrNull()
                        json?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: raw.takeUnless { it.trimStart().startsWith("<") }
                    }
                val detail = reason?.let(::walletErrorText)?.takeIf { it.isNotBlank() }
                throw BlossomHttpFailure(response.code, "HTTP ${response.code}${detail?.let { ": $it" }.orEmpty()}")
            }
            val source = response.body.source()
            source.request(32769)
            require(source.buffer.size <= 32768) { "Upload response too large" }
            val descriptor = JSONObject(source.readUtf8())
            require(descriptor.getString("sha256") == hash && descriptor.getLong("size") == size) { "Upload descriptor does not match attachment" }
            val url = descriptor.getString("url").toHttpUrlOrNull()
            require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                url.toString().length <= 2048 && url.pathSegments.last().startsWith(hash)) { "Invalid Blossom media URL" }
            url.toString()
        }
    }
}

private class BlossomHttpFailure(val code: Int, message: String) : IOException(message)

private suspend fun Call.awaitUpload(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
        }
    })
}

/** Report actual request-body bytes, at most once per percentage point. */
private fun progressBody(file: File, mime: String, progress: (Int) -> Unit): RequestBody {
    val body = file.asRequestBody(mime.toMediaType())
    return object : RequestBody() {
        override fun contentType() = body.contentType()
        override fun contentLength() = body.contentLength()
        override fun writeTo(sink: BufferedSink) {
            var sent = 0L
            var previous = -1
            val counting = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    sent += byteCount
                    // Reserve completion until the server's descriptor is validated.
                    val percent = (sent * 100 / contentLength()).toInt().coerceIn(0, 99)
                    if (percent != previous) { previous = percent; progress(percent) }
                }
            }.buffer()
            progress(0)
            body.writeTo(counting)
            counting.flush()
        }
    }
}
