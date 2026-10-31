package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl

// Tabs describe existing public activity, never actions that publish events.
internal val videoKinds = listOf(21, 22, 34235, 34236)
internal val readableKinds = listOf(1, 1111, 17, 1068, 1621, 32267, 20, 9802, 30023, 30030) + videoKinds + voiceKinds
internal val profileKinds = listOf(1, 1111, 17, 1068, 1621, 6, 16, 7, 9802, 20, 10003, 30003, 32267, 30023, 3, 30000, 30617, 30030, 9735, 9321) + videoKinds + voiceKinds

enum class ProfileTab(val kinds: List<Int>, val label: String) {
    SEARCH(emptyList(), "Search"), NOTIFICATIONS(emptyList(), "Notifications"),
    POSTS(listOf(1, 6, 16), "Posts"), REPLIES(listOf(1, 1111, 1244), "Replies"),
    REPOSTS(listOf(6, 16), "Reposts"), ZAPS(listOf(9735, 9321), "Zap activity"),
    REACTIONS(listOf(7), "Reactions"), HIGHLIGHTS(listOf(9802), "Highlights"),
    PHOTOS(listOf(1, 20), "Photos"), VIDEOS(listOf(1, 21, 22, 34235, 34236), "Videos"),
    POLLS(listOf(1068), "Polls"), VOICE(voiceKinds, "Voice"), PODCASTS(podcastKinds, "Podcasts"),
    BOOKMARKS(listOf(10003, 30003), "Bookmarks"), APPS(listOf(32267), "Apps"),
    ARTICLES(listOf(30023), "Articles"), PEOPLE(listOf(3, 30000), "People"), PROJECTS(listOf(30617), "Projects"), EMOJI_PACKS(listOf(30030), "Emoji packs"),
    ISSUES(listOf(1621), "Issues"), HASHTAGS(listOf(1), "Hashtags"), RECIPES(listOf(30023), "Recipes");
    internal val orderPriority: Int
        get() = when (this) {
            SEARCH -> -2
            NOTIFICATIONS -> -1
            POSTS -> 0
            PHOTOS -> 1
            VIDEOS -> 2
            ZAPS -> 3
            HIGHLIGHTS -> 4
            ARTICLES -> 5
            REPLIES -> 6
            REPOSTS -> 7
            PODCASTS -> 8
            VOICE -> 9
            POLLS -> 10
            REACTIONS -> 11
            HASHTAGS -> 12
            BOOKMARKS -> 13
            PEOPLE -> 14
            APPS -> 15
            PROJECTS -> 16
            ISSUES -> 17
            EMOJI_PACKS -> 18
            RECIPES -> 19
        }

    internal val profileOrderPriority: Int
        get() = if (this == REPLIES) 1 else orderPriority * 2

    fun matches(event: Event): Boolean = event.kind in kinds && when (this) {
        HASHTAGS -> event.tags.any { it.size >= 2 && it[0] == "t" && normalizeFollowedTag(it[1]) != null }
        POSTS -> if (event.kind == 1) !Protocol.isReply(event) else repostTarget(event) != null
        REPLIES -> Protocol.isReply(event)
        PODCASTS -> isPodcastActivity(event)
        RECIPES -> isRecipe(event)
        ARTICLES -> !isRecipe(event)
        ZAPS -> zapActivity(event) != null
        REPOSTS -> repostTarget(event) != null
        EMOJI_PACKS -> !event.tagValue("d").isNullOrBlank() && collectionEmojis(event).isNotEmpty()
        PHOTOS -> MediaContent.attachments(event, 32).any { it.kind == MediaContent.Kind.IMAGE }
        VIDEOS -> MediaContent.attachments(event, 32).any { it.kind == MediaContent.Kind.VIDEO }
        BOOKMARKS -> bookmarkReferences(event).isNotEmpty()
        PEOPLE -> peopleReferences(event).isNotEmpty()
        APPS, PROJECTS -> !event.tagValue("d").isNullOrBlank()
        else -> true
    }
}

/** Only public tags are inspected. Encrypted list content is never parsed or requested. */
internal fun bookmarkReferences(event: Event): List<NostrContent.Reference> {
    if (event.kind !in listOf(10003, 30003)) return emptyList()
    return event.tags.mapNotNull { tag ->
        val value = tag.getOrNull(1) ?: return@mapNotNull null
        val relay = tag.getOrNull(2)?.let(Protocol::relay)
        when (tag[0]) {
            "e" -> Protocol.publicKey(value)?.let { NostrContent.Reference(it, relays = listOfNotNull(relay)) }
            "a" -> runCatching {
                val parts = value.split(':', limit = 3)
                require(parts.size == 3)
                val kind = parts[0].toInt()
                val author = Protocol.publicKey(parts[1]) ?: return@mapNotNull null
                NostrContent.Reference(value, author = author, kind = kind, relays = listOfNotNull(relay),
                    external = NAddress.create(kind, author, parts[2], relay = relay?.normalizeRelayUrl()).takeUnless { kind == 32267 })
            }.getOrNull()
            else -> null
        }
    }.distinctBy { it.id }
}

/** Old versions retained by paging must never resurrect removed public bookmarks. */
internal fun latestProfileEvents(events: List<Event>): List<Event> = events
    .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
    .distinctBy { event -> when {
        event.kind in listOf(3, 10003) -> "${event.kind}:${event.pubKey}"
        event.kind in listOf(30000, 30003, 30023, 30030, 30617, 34235, 34236, 32267) -> "${event.kind}:${event.pubKey}:${event.tagValue("d").orEmpty()}"
        else -> event.id
    } }

internal fun Event.tagValue(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

/** NIP-18 text-note reposts resolve via their e/p tags; content may be empty. */
internal fun repostTarget(event: Event): NostrContent.Reference? {
    if (event.kind !in listOf(6, 16)) return null
    val tag = event.tags.firstOrNull { it.size >= 2 && it[0] == "e" } ?: return null
    val id = Protocol.publicKey(tag[1]) ?: return null
    return NostrContent.Reference(id, relays = listOfNotNull(tag.getOrNull(2)?.let(Protocol::relay)),
        author = event.tagValue("p")?.let(Protocol::publicKey), kind = if (event.kind == 6) 1 else event.tagValue("k")?.toIntOrNull())
}

internal fun reactionTarget(event: Event): NostrContent.Reference? {
    if (event.kind != 7) return null
    val tag = event.tags.lastOrNull { it.size >= 2 && it[0] == "e" } ?: return null
    val id = Protocol.publicKey(tag[1]) ?: return null
    val kind = event.tagValue("k")?.toIntOrNull()
    return NostrContent.Reference(id, relays = listOfNotNull(tag.getOrNull(2)?.let(Protocol::relay)),
        author = event.tags.lastOrNull { it.size >= 2 && it[0] == "p" }?.get(1)?.let(Protocol::publicKey),
        kind = kind, external = if (kind != null && kind !in readableKinds) com.vitorpamplona.quartz.nip19Bech32.entities.NNote.create(id) else null)
}

internal fun highlightAuthor(event: Event): String? = if (event.kind != 9802) null else (
    event.tags.firstOrNull { it.firstOrNull() == "p" && it.getOrNull(3) == "author" }?.getOrNull(1)
        ?: event.tags.firstOrNull { it.firstOrNull() == "p" && it.getOrNull(3) !in listOf("mention", "editor") }?.getOrNull(1)
        ?: event.tagValue("a")?.split(':', limit = 3)?.getOrNull(1)
    )?.let(Protocol::publicKey)

internal data class HighlightSource(val label: String, val url: String? = null, val reference: NostrContent.Reference? = null)
internal fun highlightSource(event: Event): HighlightSource? {
    event.tags.firstOrNull { it.firstOrNull() == "r" && it.getOrNull(2) != "mention" }?.getOrNull(1)?.let { raw ->
        val uri = runCatching { java.net.URI(raw) }.getOrNull()
        if (uri?.scheme in listOf("https", "http") && !uri?.host.isNullOrBlank()) return HighlightSource(uri!!.host.removePrefix("www."), url = raw)
    }
    event.tagValue("e")?.let(Protocol::publicKey)?.let { return HighlightSource("nostr post", reference = NostrContent.Reference(it)) }
    return runCatching {
        val parts = event.tagValue("a")?.split(':', limit = 3) ?: return null
        val kind = parts[0].toInt()
        val author = Protocol.publicKey(parts[1]) ?: return null
        val pointer = NAddress.create(kind, author, parts[2], relay = null)
        HighlightSource(if (kind == 30023) "blog post" else "nostr post", reference = NostrContent.Reference(author, external = pointer))
    }.getOrNull()
}

internal fun activityReferences(event: Event): List<NostrContent.Reference> = when (event.kind) {
    3, 30000 -> peopleReferences(event)
    10003, 30003 -> bookmarkReferences(event)
    6, 16 -> listOfNotNull(repostTarget(event))
    7 -> listOfNotNull(reactionTarget(event))
    9735, 9321 -> zapActivityReferences(event)
    9802 -> listOfNotNull(highlightAuthor(event)?.let { NostrContent.Reference(it, profile = true) }) +
        listOf("context", "comment").flatMap { NostrContent.tokens(event.tagValue(it).orEmpty(), event).mapNotNull { token -> token.reference } }
    else -> emptyList()
}

internal data class ProfilePhotos(
    val rows: List<List<Pair<Event, MediaContent.Attachment>>> = emptyList(),
    val urls: List<String> = emptyList(),
    val sources: List<Event> = emptyList(),
)

/** Public follow/list members only; encrypted content stays opaque. */
internal fun peopleReferences(event: Event): List<NostrContent.Reference> {
    if (event.kind !in listOf(3, 30000) || (event.kind == 30000 && event.tagValue("d") == "mute")) return emptyList()
    return event.tags.asSequence().filter { it.firstOrNull() == "p" }.mapNotNull { tag ->
        tag.getOrNull(1)?.let(Protocol::publicKey)?.let { key ->
            NostrContent.Reference(key, profile = true, relays = listOfNotNull(tag.getOrNull(2)?.let(Protocol::relay)))
        }
    }.distinctBy { it.id }.take(5000).toList()
}

/** Resolve public address tags by kind + author + identifier, never as event IDs. */
internal fun NostrContent.Reference.addressFilter(): com.vitorpamplona.quartz.nip01Core.relay.filters.Filter? {
    val parts = id.split(':', limit = 3)
    if (parts.size != 3) return null
    val addressKind = parts[0].toIntOrNull() ?: return null
    val addressAuthor = Protocol.publicKey(parts[1]) ?: return null
    if (addressKind !in readableKinds || addressKind !in 30000..39999) return null
    return com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(
        kinds = listOf(addressKind), authors = listOf(addressAuthor), tags = mapOf("d" to listOf(parts[2])), limit = 1)
}

/** Home uses the same categories as profiles, with reposts included on the Posts page. */
internal fun ProfileTab.feedKinds(): List<Int> = kinds
internal fun ProfileTab.matchesFeed(event: Event): Boolean =
    if (this == ProfileTab.HASHTAGS) event.kind == 1 else matches(event)

/** Lightning receipts use p/P; nutzaps use the sender author and recipient p tag. */
internal fun com.vitorpamplona.quartz.nip01Core.relay.filters.Filter.activityFilters(): List<com.vitorpamplona.quartz.nip01Core.relay.filters.Filter> {
    if (17 in kinds.orEmpty()) {
        val ordinary = kinds.orEmpty().filter { it != 17 }
        return (if (ordinary.isEmpty()) emptyList() else listOf(
            com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(authors = authors, kinds = ordinary,
                tags = tags, limit = limit, until = until, since = since)).flatMap { it.activityFilters() }) + podcastTagFilters()
    }
    // Hashtag readers retain the explicit event-author and topic intersection.
    if (tags?.containsKey("t") == true) return listOf(this)
    val people = authors ?: return listOf(this)
    if (kinds.orEmpty().none { it == 9735 || it == 9321 }) return listOf(this)
    return buildList {
        val ordinary = kinds.orEmpty().filter { it != 9735 }
        if (ordinary.isNotEmpty()) add(com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(authors = people, kinds = ordinary, limit = limit, until = until, tags = tags))
        if (9735 in kinds.orEmpty()) for (tag in listOf("p", "P")) add(com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(kinds = listOf(9735), tags = mapOf(tag to people), limit = limit, until = until))
        if (9321 in kinds.orEmpty()) add(com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(kinds = listOf(9321), tags = mapOf("p" to people), limit = limit, until = until))
    }
}

internal fun Event.involves(key: String?): Boolean = key != null &&
    (pubKey == key || (kind == 9321 && tagValue("p") == key) || (kind == 9735 && (tagValue("p") == key || tagValue("P") == key)))
