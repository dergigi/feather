package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event

/** NIP-65 routing. Unknown, invalid, or unselected routes fall back to automatically selected base relays. */
object Outbox {
    data class RelayList(val read: List<String>, val write: List<String>)

    fun parse(event: Event): RelayList {
        fun urls(marker: String) = event.tags.asSequence()
            .filter { it.size >= 2 && it[0] == "r" && (it.size == 2 || it[2] == marker) }
            .mapNotNull { Protocol.relay(it[1]) }.distinct().toList()
        return RelayList(urls("read").take(4), urls("write").take(4))
    }

    fun readRoutes(authors: List<String>, lists: Map<String, RelayList>, fallback: List<String>, maxDiscovered: Int = 8, allowed: Set<String>? = null): Map<String, List<String>> {
        val keys = authors.distinct()
        val candidates = keys.flatMap { lists[it]?.write.orEmpty() }.distinct().filter { it !in fallback && (allowed == null || it in allowed) }.sorted().toMutableSet()
        val selected = fallback.toMutableSet()
        // Prefer relays covering the most authors; stable tie-breaking avoids connection churn.
        val coverage = keys.associateWith { key -> lists[key]?.write.orEmpty().count { it in selected } }.toMutableMap()
        // Index each relay's authors once: scoring is a scan of the authors advertising that
        // relay, not a rescan of every author for every candidate on every discovery round.
        val relayKeys = HashMap<String, MutableList<String>>()
        for (key in keys) for (relay in lists[key]?.write.orEmpty()) relayKeys.getOrPut(relay) { mutableListOf() }.add(key)
        repeat(maxDiscovered) {
            val best = candidates.maxByOrNull { url -> relayKeys[url].orEmpty().count { coverage.getOrDefault(it, 0) < 2 } }
            if (best != null && relayKeys[best].orEmpty().any { coverage.getOrDefault(it, 0) < 2 }) {
                selected += best
                candidates -= best
                relayKeys[best].orEmpty().forEach { if (it in coverage) coverage[it] = coverage.getValue(it) + 1 }
            }
        }
        val result = linkedMapOf<String, MutableList<String>>()
        keys.forEach { key ->
            val routes = lists[key]?.write.orEmpty().filter { it in selected }.take(2).ifEmpty { fallback }
            routes.forEach { result.getOrPut(it) { mutableListOf() }.add(key) }
        }
        return result
    }

}
