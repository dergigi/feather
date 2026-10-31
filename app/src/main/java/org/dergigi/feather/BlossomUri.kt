package org.dergigi.feather

import android.content.Context
import coil3.intercept.Interceptor as ImageInterceptor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest

/** BUD-10 references stay intact in content, sharing, and media cache keys. */
internal data class BlossomUri(val hash: String, val extension: String, val servers: List<String>, val authors: List<String>, val size: Long?) {
    val fileName get() = "$hash.$extension"
    companion object {
        private val identifier = Regex("blossom:([0-9a-f]{64})\\.([a-zA-Z0-9]{1,16})(?:\\?([^#\\s]*))?")
        private val pubkey = Regex("[0-9a-f]{64}")
        fun parse(raw: String): BlossomUri? = runCatching {
            if (raw.length > 8192) return null
            val match = identifier.matchEntire(raw) ?: return null
            val params = match.groupValues[3].split('&').take(64).map { entry ->
                val parts = entry.split('=', limit = 2)
                parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
            val sizes = params.filter { it.first == "sz" }.map { it.second }
            val size = sizes.firstOrNull()?.toLongOrNull()
            if (sizes.isNotEmpty() && (size == null || size <= 0 || sizes.any { it.toLongOrNull() != size })) return null
            val servers = params.filter { it.first == "xs" }.mapNotNull { (_, value) ->
                val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
                // Retain Feather's HTTPS-only media policy. BUD-10 servers live at their origin.
                url?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
                    ?.newBuilder()?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()
            }.distinct().take(8)
            BlossomUri(match.groupValues[1], match.groupValues[2].lowercase(), servers,
                params.filter { it.first == "as" && pubkey.matches(it.second) }.map { it.second }.distinct().take(4), size)
        }.getOrNull()

        // Transport-only address: intercepted before DNS. Never used for copying or sharing.
        fun transport(raw: String): String = if (parse(raw) != null) "https://blossom.invalid/" + raw.removePrefix("blossom:") else raw
        fun original(url: okhttp3.HttpUrl): String? = if (url.host == "blossom.invalid")
            "blossom:" + url.encodedPath.removePrefix("/") + (url.encodedQuery?.let { "?$it" } ?: "") else null
    }
}

/** Mapping happens before Coil's fetcher; the disk/memory identities remain the original URI. */
internal class BlossomImageInterceptor : ImageInterceptor {
    override suspend fun intercept(chain: ImageInterceptor.Chain): coil3.request.ImageResult {
        val raw = chain.request.data.toString()
        if (BlossomUri.parse(raw) == null) return chain.proceed()
        val request = chain.request.newBuilder().data(BlossomUri.transport(raw))
            .diskCacheKey(chain.request.diskCacheKey ?: raw).memoryCacheKey(chain.request.memoryCacheKey ?: raw).build()
        return chain.withRequest(request).proceed()
    }
}

/** Shared by visible images, prefetch, audio, video and saving. Runs only on network workers. */
internal class BlossomTransport(private val context: Context) : Interceptor {
    private data class Servers(val values: List<String>, val expires: Long)
    private val lists = object : LinkedHashMap<String, Servers>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Servers>?) = size > 64
    }
    private val discovery = Mutex()
    private fun servers(author: String): List<String> = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(7_000) { discovery.withLock {
            val now = System.currentTimeMillis()
            lists[author]?.takeIf { it.expires > now }?.let { return@withLock it.values }
            val found = try {
                withTimeoutOrNull(6_000) { (context.applicationContext as FeatherApplication).repository.blossomReadServers(author) }.orEmpty()
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { emptyList() }
            lists[author] = Servers(found, now + if (found.isEmpty()) 60_000 else 600_000)
            found
        } }.orEmpty()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val raw = BlossomUri.original(chain.request().url) ?: return chain.proceed(chain.request())
        val blob = BlossomUri.parse(raw) ?: throw IOException("Invalid Blossom URI")
        val tried = mutableSetOf<String>()
        val started = System.nanoTime()
        var failure: IOException? = null
        fun attempt(server: String): Response? {
            if (!tried.add(server) || tried.size > 12 || chain.call().isCanceled() ||
                System.nanoTime() - started > 30_000_000_000L) return null
            val url = server.toHttpUrlOrNull()?.newBuilder()?.addPathSegment(blob.fileName)?.build() ?: return null
            try {
                val response = chain.withConnectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .withReadTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .proceed(chain.request().newBuilder().url(url).header("Accept-Encoding", "identity").build())
                if (!response.isSuccessful) { response.close(); return null }
                val body = response.body ?: run { response.close(); return null }
                val length = if (response.code == 206) response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() else body.contentLength().takeIf { it >= 0 }
                if (blob.size != null && length != null && blob.size != length) { response.close(); return null }
                // Ranged video reads can only validate total length. Full downloads also verify the hash.
                if (response.code == 200 && chain.request().method == "GET") {
                    return response.newBuilder().body(verifiedBody(body, blob)).build()
                }
                return response
            } catch (e: IOException) { failure = e; return null }
        }
        for (server in blob.servers) attempt(server)?.let { return it }
        for (author in blob.authors) {
            if (chain.call().isCanceled() || System.nanoTime() - started > 30_000_000_000L) break
            for (server in servers(author).take(8)) attempt(server)?.let { return it }
        }
        for (server in listOf("https://blossom.primal.net/", "https://cdn.satellite.earth/")) attempt(server)?.let { return it }
        throw IOException("Could not locate Blossom file", failure)
    }

    private fun verifiedBody(body: ResponseBody, blob: BlossomUri): ResponseBody {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var finished = false
        val source = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val start = sink.size
                val count = super.read(sink, byteCount)
                if (count > 0) {
                    val bytes = Buffer()
                    sink.copyTo(bytes, start, count)
                    digest.update(bytes.readByteArray())
                    total += count
                    if (blob.size != null && total > blob.size) throw IOException("Blossom size mismatch")
                } else if (count == -1L && !finished) {
                    finished = true
                    if (blob.size != null && total != blob.size) throw IOException("Blossom size mismatch")
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (hash != blob.hash) throw IOException("Blossom hash mismatch")
                }
                return count
            }
        }.buffer()
        return object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }
    }
}
