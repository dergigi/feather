package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun blossomListUrls(values: List<String>): List<String> {
    require(values.size in 1..16) { "Use between 1 and 16 servers. BUD-03 requires at least one server." }
    val urls = values.map { raw ->
        require(raw.length <= 2048 && raw.trim().startsWith("https://", true) && raw.trim().none { it.isWhitespace() || it == '\\' }) {
            "Enter a full HTTPS server URL."
        }
        blossomServer(raw.trim()) ?: error("Use HTTPS URLs without credentials, queries or fragments.")
    }
    require(urls.distinct().size == urls.size) { "Remove duplicate server URLs." }
    return urls
}

/** Preserve other clients' extension tags, including extra fields on retained server tags. */
internal fun blossomListTags(base: Event?, urls: List<String>): Array<Array<String>> = buildList {
    urls.forEach { url ->
        add(base?.tags?.firstOrNull { it.firstOrNull() == "server" && it.getOrNull(1)?.let(::blossomServer) == url }
            ?.copyOf()?.also { it[1] = url } ?: arrayOf("server", url))
    }
    addAll(base?.tags.orEmpty().filter { it.firstOrNull() != "server" })
}.toTypedArray()

/** Coverage describes a bounded read, never a guarantee of global absence or freshness. */
internal data class BlossomReadCoverage(val routes: List<String>, val completed: Set<String>, val witnessedNewest: Boolean) {
    init {
        check(completed.isNotEmpty() && completed.all { it in routes }) {
            "No relay completed the read. Your cached list is retained; retry when connected."
        }
    }
    val incomplete: Boolean get() = completed.size != routes.size || !witnessedNewest
}

internal fun newestBlossomEvent(known: Event?, candidate: Event?): Event? =
    if (candidate != null && Protocol.isNewer(candidate, known)) candidate else known

internal fun requireBlossomPublishSnapshot(base: BlossomLists.Snapshot, fresh: BlossomLists.Snapshot, allowIncomplete: Boolean) {
    check(base.owner == fresh.owner && base.event?.id == fresh.event?.id && base.relayId == fresh.relayId) {
        "Account lists changed. Close and reopen to review them."
    }
    check(!fresh.incomplete || allowIncomplete) {
        "Relay coverage is now incomplete. Close and reopen to review the warning before publishing."
    }
}

internal class BlossomLists(private val repo: FeatherRepository, private val requests: SignerRequests) {
    data class Snapshot(val owner: String, val event: Event?, val routes: List<String>, val relayId: String?,
        val relayCoverage: BlossomReadCoverage, val listCoverage: BlossomReadCoverage) {
        val incomplete: Boolean get() = relayCoverage.incomplete || listCoverage.incomplete
        val warning: String? get() = if (!incomplete) null else
            "Incomplete relay coverage (account relays ${relayCoverage.completed.size}/${relayCoverage.routes.size}, " +
                "server list ${listCoverage.completed.size}/${listCoverage.routes.size}). " +
                if (event == null) "No list was found, but offline relays may have one. Publishing creates or replaces your public list."
                else "Using the newest verified list known to Feather, including cache. Offline relays may have a newer list; publishing may replace unseen changes."
        val urls: List<String> get() = event?.tags.orEmpty().filter { it.firstOrNull() == "server" }.map { it.getOrNull(1).orEmpty() }
    }
    private val edits = Mutex()
    private val queries = ProfileRelayQuery()
    private fun current(owner: String) = check(repo.state.value.publicKey == owner) { "Account changed. Reopen Blossom settings." }

    private data class Discovery(val event: Event?, val coverage: BlossomReadCoverage)

    private suspend fun discover(owner: String, kind: Int, routes: List<String>): Discovery {
        require(routes.isNotEmpty() && routes.size <= 32) { "Too many account relays to safely refresh this list." }
        repo.awaitListEditingForeground()
        current(owner)
        var latest = repo.followEvent(owner, kind)?.takeIf { it.pubKey == owner && it.kind == kind && it.verify() }
        val lock = Mutex()
        val witnessed = mutableSetOf<String>()
        val completed = withTimeout(60_000) {
            queries.completed(routes, listOf(Filter(authors = listOf(owner), kinds = listOf(kind), limit = 1).toJson())) { relay, event ->
                require(event.kind == kind && event.pubKey == owner && event.verify() && event.tags.size <= 5000 &&
                    event.content.length <= 32768 && event.createdAt <= System.currentTimeMillis() / 1000 + 600) { "Invalid account list response" }
                current(owner)
                // A verified event remains evidence even if this socket never reaches EOSE.
                lock.withLock {
                    if (newestBlossomEvent(latest, event)?.id != latest?.id) { latest = event; witnessed.clear() }
                    if (event.id == latest?.id) witnessed.add(relay)
                }
                repo.cacheFollowEvent(event, relay)
                true
            }
        }
        current(owner)
        val cached = repo.followEvent(owner, kind)?.takeIf { it.pubKey == owner && it.kind == kind && it.verify() }
        return lock.withLock {
            if (newestBlossomEvent(latest, cached)?.id != latest?.id) { latest = cached; witnessed.clear() }
            Discovery(latest, BlossomReadCoverage(routes, completed, latest == null || witnessed.any { it in completed }))
        }
    }

    suspend fun load(owner: String): Snapshot = withContext(Dispatchers.IO) {
        current(owner)
        val known = repo.followEvent(owner, 10002)?.let(Outbox::parse)
        val seeds = RelayDefaults.seeds.take(3)
        val authoritative = known?.write.orEmpty()
        val relayRoutes = (authoritative + seeds).distinct()
        val relayRead = discover(owner, 10002, relayRoutes)
        val relayEvent = relayRead.event
        val relays = relayEvent?.let(Outbox::parse)
        // Query every bounded route, but offline peers do not veto usable responses.
        val routes = (relays?.write.orEmpty() + seeds).distinct()
        val listRead = discover(owner, 10063, routes)
        val event = listRead.event
        require(event?.tags.orEmpty().count { it.firstOrNull() == "server" } <= 16) {
            "Your list has more than 16 servers. Use another client to edit it; Feather will not truncate it."
        }
        Snapshot(owner, event, routes, relayEvent?.id, relayRead.coverage, listRead.coverage)
    }

    suspend fun publish(base: Snapshot, values: List<String>, identity: NostrSignerExternal, allowIncomplete: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        edits.withLock {
            val owner = base.owner
            current(owner)
            require(identity.pubKey == owner)
            val urls = blossomListUrls(values)
            val fresh = load(owner)
            requireBlossomPublishSnapshot(base, fresh, allowIncomplete)
            val tags = blossomListTags(fresh.event, urls)
            require(tags.size <= 5000) { "Server list is too large." }
            val timestamp = maxOf(System.currentTimeMillis() / 1000, (fresh.event?.createdAt ?: 0) + 1)
            check(timestamp <= System.currentTimeMillis() / 1000 + 60) { "List is dated in the future. Check your clock." }
            val signed = requests.run {
                current(owner)
                withTimeout(180_000) { identity.sign<Event>(timestamp, 10063, tags, "") }
            }
            require(signed.pubKey == owner && signed.kind == 10063 && signed.createdAt == timestamp && signed.content.isEmpty() &&
                signed.tags.contentDeepEquals(tags) && signed.verify()) { "Signer returned an invalid Blossom list." }
            // Amber may remain open while another client changes the account's lists.
            val after = load(owner)
            requireBlossomPublishSnapshot(fresh, after, allowIncomplete)
            currentCoroutineContext().ensureActive()
            current(owner)
            check(repo.publishFollowEvent(signed, after.routes, fresh.event?.id, after.relayId)) {
                "No relay confirmed publication. Delivery is uncertain; reopen to refresh before trying again."
            }
            current(owner)
            val saved = repo.followEvent(owner, 10063)
            check(!Protocol.isNewer(signed, saved)) { "Publication confirmed, but the local cache could not be updated. Reopen to refresh." }
            after.copy(event = saved)
        }
    }
}
