package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.*

/** Decode references with Quartz; never treat arbitrary bech32-looking text as an identity. */
object NostrContent {
    data class Reference(val id: String, val profile: Boolean = false, val relays: List<String> = emptyList(), val author: String? = null, val kind: Int? = null, val external: String? = null, val hashtag: String? = null)
    data class Token(val start: Int, val end: Int, val text: String, val reference: Reference? = null, val url: String? = null)
    private val hex = Regex("[0-9a-f]{64}")
    // Emoji symbols, modifiers, variation selectors, ZWJ sequences and flag tag sequences
    // belong to the hashtag too. URLs still win so fragments are never published as tags.
    private val tokens = Regex("(?:https?://|blossom:)[^\\s<>]+|(?<![\\w/])(?:nostr:)?(?:npub|nprofile|note|nevent|naddr)1[023456789acdefghjklmnpqrstuvwxyz]+|#\\[\\d{1,4}]|(?<![\\p{L}\\p{N}\\p{M}\\p{So}\\p{Sk}_/])#[\\p{L}\\p{N}\\p{So}_][\\p{L}\\p{N}\\p{M}\\p{So}\\p{Sk}_\\u200D\\x{E0020}-\\x{E007F}]*", RegexOption.IGNORE_CASE)
    /** Match the same hashtags as the reader; consume whole URLs before considering their fragments. */
    internal fun hashtags(text: String): List<String> = tokens.findAll(text)
        .map { it.value }
        .filter { it.startsWith("#") && !it.startsWith("#[") }
        .map { it.drop(1).lowercase(java.util.Locale.ROOT) }
        .distinct()
        .toList()

    /** Direct search inputs are submitted explicitly, never sent to profile search providers. */
    internal fun isSearchReference(raw: String): Boolean {
        val value = raw.trim()
        return value.startsWith("nostr:", ignoreCase = true) ||
            (value.startsWith("#") && value.none(Char::isWhitespace)) ||
            Regex("^(npub|nprofile|note|nevent|naddr|nrelay|nsec)1", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    internal fun searchReference(raw: String): Reference? {
        val value = raw.trim()
        if (value.length > 8192) return null
        if (value.startsWith("#")) {
            val match = tokens.matchEntire(value) ?: return null
            if (match.value.startsWith("#[")) return null
            val tag = value.drop(1).lowercase(java.util.Locale.ROOT)
            return Reference(tag, hashtag = tag)
        }
        return decode(value)
    }

    fun decode(raw: String): Reference? = runCatching {
        val value = raw.lowercase().removePrefix("nostr:")
        val parsed = Nip19Parser.uriToRoute(value)?.takeIf { it.nip19raw == value && it.additionalChars.isNullOrEmpty() } ?: return null
        val result = when (val entity = parsed.entity) {
            is NPub -> Reference(entity.hex, profile = true)
            is NProfile -> Reference(entity.hex, profile = true, relays = entity.relay.map { it.url })
            is NNote -> Reference(entity.hex)
            is NEvent -> Reference(entity.hex, relays = entity.relay.map { it.url }, author = entity.author, kind = entity.kind,
                external = value.takeIf { entity.kind != null && entity.kind !in readableKinds })
            is NAddress -> if (entity.kind in readableKinds && entity.kind in 30000..39999) Reference("${entity.kind}:${entity.author}:${entity.dTag}", author = entity.author, kind = entity.kind,
                relays = ((if (entity.kind == 32267) listOf(ZAPSTORE_RELAY) else emptyList()) + entity.relay.map { it.url })) else Reference(entity.author, author = entity.author, kind = entity.kind, external = value)
            else -> return null
        }
        result.takeIf { hex.matches(it.id) || (it.addressFilter() != null) }?.let { it.copy(relays = it.relays.mapNotNull(Protocol::relay).distinct().take(2), author = it.author?.takeIf(hex::matches)) }
    }.getOrNull()

    fun tokens(text: String, event: Event): List<Token> = tokens.findAll(text).take(64).mapNotNull { match ->
        val raw = match.value
        if (raw.startsWith("http", true) || raw.startsWith("blossom:")) {
            val url = raw.trimEnd('.', ',', ')', ']', '!', '?')
            Token(match.range.first, match.range.first + url.length, url, url = url)
        } else {
            val reference = if (raw.startsWith("#[")) {
                val tag = event.tags.getOrNull(raw.drop(2).dropLast(1).toIntOrNull() ?: -1)
                tag?.takeIf { it.size >= 2 && it[0] in setOf("p", "e") && hex.matches(it[1]) }?.let {
                    Reference(it[1], profile = it[0] == "p", relays = listOfNotNull(it.getOrNull(2)?.let(Protocol::relay)))
                }
            } else if (raw.startsWith("#")) {
                val tag = raw.drop(1).lowercase(java.util.Locale.ROOT)
                Reference(tag, hashtag = tag)
            } else decode(raw)
            reference?.let { Token(match.range.first, match.range.last + 1, raw, it) }
        }
    }.toList()

    private val referenceCache = object : LinkedHashMap<String, List<Reference>>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Reference>>?) = size > 256
    }
    @Synchronized
    fun references(event: Event): List<Reference> = referenceCache.getOrPut(event.id) { parseReferences(event) }

    private fun parseReferences(event: Event): List<Reference> {
        if (event.kind in listOf(32267, 30617, 30030)) return emptyList()
        // Repost content is an encoded event, not user-facing text to tokenize.
        if (event.kind in listOf(3, 6, 16, 9735, 9321, 30000, 10003, 30003)) return activityReferences(event)
        return (listOfNotNull(replyReference(event)) + activityReferences(event) + tokens(event.content, event).mapNotNull { it.reference } +
        event.tags.asSequence().filter { it.size >= 2 && it[0] == "q" && hex.matches(it[1]) }.take(3).map {
            Reference(it[1], relays = listOfNotNull(it.getOrNull(2)?.let(Protocol::relay)), author = it.getOrNull(3)?.takeIf(hex::matches))
        }).filter { it.hashtag == null }.groupBy { it.id }.values.take(16).map { group ->
            group.first().copy(relays = group.flatMap { it.relays }.distinct().take(2), author = group.firstNotNullOfOrNull { it.author })
        }

    }

    fun replyReference(event: Event): Reference? {
        ThreadRelations.parent(event)?.let { return navigable(it) }
        if (event.kind in listOf(1111, 1244)) {
            val scope = event.tags.firstOrNull { it.size >= 2 && it[0] == "i" }
            val url = scope?.let { MediaContent.secureUrl(it[1]) ?: MediaContent.secureUrl(it.getOrNull(2)) }
            return url?.let { Reference(it, external = it) }
        }
        return null
    }

    /** Preserve unsupported parent links without treating them as readable thread events. */
    internal fun navigable(ref: Reference): Reference = ref.copy(
        relays = (ref.relays + if (ref.kind == 32267) listOf(ZAPSTORE_RELAY) else emptyList()).distinct(),
        external = if (ref.kind != null && ref.kind !in readableKinds) {
            val address = ref.id.split(':', limit = 3)
            if (address.size == 3) NAddress.create(ref.kind, address[1], address[2], relay = null)
            else NNote.create(ref.id)
        } else ref.external,
    )

    fun quotes(event: Event): List<Reference> {
        val parent = replyReference(event)?.id
        return references(event).filter { !it.profile && it.external == null && it.id != event.id && it.id != parent }.take(if (event.kind == 30023) 16 else 3)
    }
}
