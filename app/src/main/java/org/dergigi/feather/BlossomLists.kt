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

/** An offline relay or an unwitnessed cached winner is not evidence of an empty/current list. */
internal fun requireBlossomReadCompletion(routes: List<String>, required: List<String>, completed: Set<String>, hasList: Boolean, witnessed: Set<String>) {
    check(completed.containsAll(required) && completed.isNotEmpty()) {
        "Could not confirm the latest list on every required account relay. No update sent; retry when connected."
    }
    check(hasList || completed.size == routes.size) { "Could not confirm an absent list. No update sent." }
    check(!hasList || witnessed.any { it in completed }) { "Relays did not return the latest known list. No update sent." }
}

internal class BlossomLists(private val repo: FeatherRepository, private val requests: SignerRequests) {
    data class Snapshot(val owner: String, val event: Event?, val routes: List<String>, val relayId: String?) {
        val urls: List<String> get() = event?.tags.orEmpty().filter { it.firstOrNull() == "server" }.map { it.getOrNull(1).orEmpty() }
    }
    private val edits = Mutex()
    private val queries = ProfileRelayQuery()
    private fun current(owner: String) = check(repo.state.value.publicKey == owner) { "Account changed. Reopen Blossom settings." }

    private suspend fun discover(owner: String, kind: Int, routes: List<String>, required: List<String> = routes): Event? {
        require(routes.isNotEmpty() && routes.size <= 32) { "Too many account relays to safely refresh this list." }
        repo.awaitListEditingForeground()
        current(owner)
        var latest = repo.followEvent(owner, kind)
        val lock = Mutex()
        val witnessed = mutableSetOf<String>()
        val completed = withTimeout(60_000) {
            queries.completed(routes, listOf(Filter(authors = listOf(owner), kinds = listOf(kind), limit = 1).toJson())) { relay, event ->
                require(event.kind == kind && event.pubKey == owner && event.verify() && event.tags.size <= 5000 &&
                    event.content.length <= 32768 && event.createdAt <= System.currentTimeMillis() / 1000 + 600) { "Invalid account list response" }
                current(owner)
                lock.withLock {
                    if (Protocol.isNewer(event, latest)) { latest = event; witnessed.clear() }
                    if (event.id == latest?.id) witnessed.add(relay)
                }
                repo.cacheFollowEvent(event, relay)
                true
            }
        }
        current(owner)
        val cached = repo.followEvent(owner, kind)
        return lock.withLock {
            if (cached != null && Protocol.isNewer(cached, latest)) { latest = cached; witnessed.clear() }
            requireBlossomReadCompletion(routes, required, completed, latest != null, witnessed)
            latest
        }
    }

    suspend fun load(owner: String): Snapshot = withContext(Dispatchers.IO) {
        current(owner)
        val known = repo.followEvent(owner, 10002)?.let(Outbox::parse)
        val seeds = RelayDefaults.seeds.take(3)
        val authoritative = known?.write.orEmpty()
        val relayRoutes = (authoritative + seeds).distinct()
        val relayEvent = discover(owner, 10002, relayRoutes, authoritative.ifEmpty { seeds })
        val relays = relayEvent?.let(Outbox::parse)
        // Arbitrary seed outages must not block accounts with advertised write relays.
        val routes = relays?.write.orEmpty().ifEmpty { seeds }.distinct()
        val event = discover(owner, 10063, routes)
        require(event?.tags.orEmpty().count { it.firstOrNull() == "server" } <= 16) {
            "Your list has more than 16 servers. Use another client to edit it; Feather will not truncate it."
        }
        Snapshot(owner, event, routes, relayEvent?.id)
    }

    suspend fun publish(base: Snapshot, values: List<String>, identity: NostrSignerExternal): Snapshot = withContext(Dispatchers.IO) {
        edits.withLock {
            val owner = base.owner
            current(owner)
            require(identity.pubKey == owner)
            val urls = blossomListUrls(values)
            val fresh = load(owner)
            check(fresh.event?.id == base.event?.id) { "Your Blossom list changed in another client. Close and reopen to review it." }
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
            check(after.event?.id == fresh.event?.id && after.relayId == fresh.relayId) { "Account lists changed while signing. Reopen to review them." }
            currentCoroutineContext().ensureActive()
            current(owner)
            check(repo.publishFollowEvent(signed, after.routes, fresh.event?.id, after.relayId)) {
                "No relay confirmed publication. Delivery is uncertain; reopen to refresh before trying again."
            }
            current(owner)
            val saved = repo.followEvent(owner, 10063)
            check(!Protocol.isNewer(signed, saved)) { "Publication confirmed, but the local cache could not be updated. Reopen to refresh." }
            Snapshot(owner, saved, after.routes, after.relayId)
        }
    }
}
