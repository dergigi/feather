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

    @Test fun incompleteReadsCannotAuthorizeReplacementOrCreation() {
        val routes = listOf("write", "seed")
        assertTrue(runCatching { requireBlossomReadCompletion(routes, listOf("write"), setOf("seed"), true, setOf("seed")) }.isFailure)
        assertTrue(runCatching { requireBlossomReadCompletion(routes, listOf("write"), setOf("write"), false, emptySet()) }.isFailure)
        assertTrue(runCatching { requireBlossomReadCompletion(routes, listOf("write"), routes.toSet(), true, emptySet()) }.isFailure)
        assertTrue(runCatching { requireBlossomReadCompletion(routes, listOf("write"), setOf("write"), true, setOf("seed")) }.isFailure)
        requireBlossomReadCompletion(routes, listOf("write"), setOf("write"), true, setOf("write"))
        requireBlossomReadCompletion(routes, routes, routes.toSet(), false, emptySet())
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
