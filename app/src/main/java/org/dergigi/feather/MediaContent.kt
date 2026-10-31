package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaContent {
    enum class Kind { IMAGE, VIDEO, AUDIO }
    data class Attachment(val url: String, val kind: Kind, val ratio: Float = 16f / 9f, val preview: String? = null, val alt: String? = null, val mime: String? = null)
    val cacheOptionsMb = listOf(128, 256, 512, 1024, 2048)
    fun imageBudget(mb: Int): Long = mb * 1024L * 1024 / 4
    fun videoBudget(mb: Int): Long = mb * 1024L * 1024 - imageBudget(mb)
    private val urls = Regex("(?:https://|blossom:)[^\\s<>]+", RegexOption.IGNORE_CASE)
    fun secureUrl(value: String?): String? = value?.let { runCatching {
        val uri = URI(it)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null)
        it
    }.getOrNull() }

    internal fun mediaUrl(value: String?): String? = value?.takeIf { BlossomUri.parse(it) != null } ?: secureUrl(value)

    fun attachments(event: Event, limit: Int = 4): List<Attachment> {
        if (event.kind in voiceKinds) return emptyList()
        val metadata = event.tags.filter { it.firstOrNull() == "imeta" }.take(32).mapNotNull { tag ->
            val fields = tag.drop(1).mapNotNull { entry ->
                val split = entry.indexOf(' ')
                if (split <= 0) null else entry.take(split) to entry.substring(split + 1)
            }.toMap()
            fields["url"]?.let { it to fields }
        }.toMap()
        val candidates = metadata.keys.asSequence() + urls.findAll(event.content).map { it.value.trimEnd('.', ',', ')', ']', '!', '?') }
        return candidates.distinct().mapNotNull { url ->
            if (mediaUrl(url) == null) return@mapNotNull null
            val fields = metadata[url].orEmpty()
            val mime = fields["m"]?.substringBefore(';')?.trim()?.lowercase() ?: DetectedMedia.mime(url)
            val extension = BlossomUri.parse(url)?.extension ?: runCatching { URI(url).path.substringAfterLast('.').lowercase() }.getOrDefault("")
            val kind = when {
                mime?.startsWith("image/") == true -> Kind.IMAGE
                mime?.startsWith("video/") == true -> Kind.VIDEO
                mime?.startsWith("audio/") == true -> Kind.AUDIO
                mime in setOf("application/x-mpegurl", "application/vnd.apple.mpegurl") -> Kind.VIDEO
                extension in setOf("jpg", "jpeg", "png", "webp", "gif", "avif") -> Kind.IMAGE
                extension in setOf("mp4", "m4v", "mov", "webm", "m3u8") -> Kind.VIDEO
                extension in setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "aiff", "aif", "amr") -> Kind.AUDIO
                event.kind == 20 && url in metadata -> Kind.IMAGE
                event.kind in videoKinds && url in metadata -> Kind.VIDEO
                else -> return@mapNotNull null
            }
            val dimensions = fields["dim"]?.split('x')?.mapNotNull(String::toFloatOrNull)
            val ratio = dimensions?.takeIf { it.size == 2 && it.all { dim -> dim.isFinite() && dim > 0 } }?.let { it[0] / it[1] }?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
            Attachment(url, kind, ratio, mediaUrl(fields["image"] ?: fields["thumb"]), fields["alt"]?.take(500), mime)
        }.take(if (event.kind in videoKinds) 1 else limit.coerceIn(1, 32)).toList()
    }
    fun text(event: Event, attachments: List<Attachment>): String {
        val mediaUrls = attachments.map { it.url }.toSet()
        return urls.replace(event.content) { match ->
            val url = match.value.trimEnd('.', ',', ')', ']', '!', '?')
            if (url in mediaUrls) match.value.removePrefix(url) else match.value
        }.withoutObjectMarkers().trim().replace(Regex("\n{3,}"), "\n\n")
    }

    /** Keep partially visible videos eligible, including videos taller than the viewport. */
    fun visibleScore(top: Float, bottom: Float, height: Float, viewportTop: Float, viewportBottom: Float): Float? {
        if (height <= 0 || viewportBottom <= viewportTop) return null
        val visible = (minOf(bottom, viewportBottom) - maxOf(top, viewportTop)).coerceAtLeast(0f)
        if (visible <= 0f) return null
        return kotlin.math.abs((top + bottom) / 2 - (viewportTop + viewportBottom) / 2)
    }
}

/** Dimensions only: no bitmap retention, disk access, or decoding on the display path. */
internal object ImageRatios {
    private val ratios = object : LinkedHashMap<String, Float>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 256
    }
    @Synchronized fun get(url: String): Float? = ratios[url]
    @Synchronized fun put(url: String, width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        val ratio = width.toFloat() / height
        ratios[url] = ratio
        return ratio
    }
}

/** Header discoveries are small, bounded, and shared by every rendering path. */
internal object DetectedMedia {
    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())
    val types = mutable.asStateFlow()
    fun mime(url: String): String? = mutable.value[url]
    @Synchronized fun put(url: String, mime: String) {
        if (mutable.value[url] == mime) return
        mutable.value = (mutable.value - url + (url to mime)).entries.toList().takeLast(256).associate { it.toPair() }
    }
}
