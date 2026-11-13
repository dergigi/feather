package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.*
import org.junit.Test

class BlossomListsTest {
    @Test fun validatesAndCanonicalizesUrlsWithoutChangingOrder() {
        assertEquals(listOf("https://second.example", "https://first.example/media"),
            blossomListUrls(listOf(" https://SECOND.example/ ", "https://first.example/media/")))
    }

    @Test fun rejectsUnsafeMalformedDuplicateAndEmptyLists() {
        listOf(emptyList(), List(17) { "https://server$it.example" },
            listOf("https://a.example", "https://A.example/"),
            listOf("http://a.example"), listOf("a.example"), listOf("https://user:pass@a.example"),
            listOf("https://a.example?q=x"), listOf("https://a.example/#x"), listOf("https://a.example:99999"),
            listOf("https://a.example/space here"), listOf("https://a.example\\path")).forEach {
            assertTrue(it.toString(), runCatching { blossomListUrls(it) }.isFailure)
        }
    }

    private val routes = listOf("primal", "offline-account", "offline-seed")
    private val partial = BlossomReadCoverage(routes, setOf("primal"), true)
    private val complete = BlossomReadCoverage(routes, routes.toSet(), true)
    private fun event(id: String = "1", time: Long = 100) = Event(id.repeat(64), "a".repeat(64), time, 10063,
        arrayOf(arrayOf("server", "https://a.example")), "", "0".repeat(128))
    private fun snapshot(event: Event? = event(), coverage: BlossomReadCoverage = partial) =
        BlossomLists.Snapshot("a".repeat(64), event, routes, "relays", coverage, coverage)

    @Test fun mixedOnlineOfflineExistingListAllowsInformedReplacement() {
        val base = snapshot()
        assertTrue(base.incomplete)
        assertNotNull(base.warning)
        assertTrue(base.warning!!.contains("1/3"))
        assertTrue(runCatching { requireBlossomPublishSnapshot(base, base, false) }.isFailure)
        requireBlossomPublishSnapshot(base, base, true)
    }

    @Test fun mixedOnlineOfflineAbsenceIsUncertainAndAllowsExplicitCreation() {
        val base = snapshot(null)
        assertTrue(base.warning!!.contains("No list was found"))
        assertTrue(runCatching { requireBlossomPublishSnapshot(base, base, false) }.isFailure)
        requireBlossomPublishSnapshot(base, base, true)
        assertTrue(base.urls.isEmpty()) // No silent default servers.
    }

    @Test fun noUsableResponseFailsForExistingAndAbsentLists() {
        listOf<Event?>(null, event()).forEach { cached ->
            assertTrue(runCatching { snapshot(cached, BlossomReadCoverage(routes, emptySet(), cached == null)) }.isFailure)
        }
    }

    @Test fun cachedNewestSurvivesAbsentAndOlderRelayResults() {
        val cached = event(time = 200)
        assertSame(cached, newestBlossomEvent(cached, null))
        assertSame(cached, newestBlossomEvent(cached, event("2", 100)))
        val unseen = BlossomReadCoverage(routes, routes.toSet(), false)
        val base = snapshot(cached, unseen)
        assertTrue(base.incomplete)
        requireBlossomPublishSnapshot(base, base, true)
        assertEquals("0".repeat(64), newestBlossomEvent(cached, event("0", 200))!!.id)
    }

    @Test fun newerEventFromRelayWithoutEoseStillBlocksStaleReplacement() {
        val base = snapshot()
        // Primal completes, but a different relay delivers a verified newer event
        // before timing out. Event selection is independent of EOSE coverage.
        val newest = newestBlossomEvent(base.event, event("2", 201))
        val fresh = snapshot(newest, BlossomReadCoverage(routes, setOf("primal"), false))
        assertEquals("2".repeat(64), fresh.event!!.id)
        assertTrue(fresh.incomplete)
        assertTrue(runCatching { requireBlossomPublishSnapshot(base, fresh, true) }.isFailure)
    }

    @Test fun coverageChangesAloneAreNotStaleEventConflicts() {
        val base = snapshot()
        val fresh = snapshot(coverage = complete)
        requireBlossomPublishSnapshot(base, fresh, true)
        requireBlossomPublishSnapshot(fresh, base, true)
        // Newly incomplete coverage requires reviewing its warning before any send.
        assertTrue(runCatching { requireBlossomPublishSnapshot(fresh, base, false) }.isFailure)
    }

    @Test fun newerEventsRelayListsAndAccountChangesRemainConflicts() {
        val base = snapshot()
        listOf(snapshot(event("2", 101)), snapshot(null), base.copy(relayId = "new-relays"),
            base.copy(owner = "b".repeat(64))).forEach { changed ->
            assertTrue(runCatching { requireBlossomPublishSnapshot(base, changed, true) }.isFailure)
        }
    }

    @Test fun reordersServersAndPreservesExtensionTagsAndFields() {
        val base = Event("1".repeat(64), "a".repeat(64), 100, 10063, arrayOf(
            arrayOf("server", "https://a.example/", "extension"), arrayOf("custom", "keep"),
            arrayOf("server", "https://b.example")), "", "0".repeat(128))
        val tags = blossomListTags(base, listOf("https://b.example", "https://a.example", "https://c.example"))
        assertArrayEquals(arrayOf("server", "https://b.example"), tags[0])
        assertArrayEquals(arrayOf("server", "https://a.example", "extension"), tags[1])
        assertArrayEquals(arrayOf("server", "https://c.example"), tags[2])
        assertArrayEquals(arrayOf("custom", "keep"), tags[3])
        assertEquals("https://a.example/", base.tags[0][1])
    }
}
