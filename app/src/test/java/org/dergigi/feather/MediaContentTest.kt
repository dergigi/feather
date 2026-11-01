package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.*
import org.junit.Test

class MediaContentTest {
    private fun note(content: String, vararg tags: Array<String>) = Event("1".repeat(64), "a".repeat(64), 100, 1, arrayOf(*tags), content, "0".repeat(128))
    @Test fun recognizesImagesAndVideosWithQueryStrings() {
        val media = MediaContent.attachments(note("https://cdn.example/photo.JPG?x=2 https://cdn.example/movie.mp4 https://example.com/page"))
        assertEquals(listOf(MediaContent.Kind.IMAGE, MediaContent.Kind.VIDEO), media.map { it.kind })
    }
    @Test fun metadataSupportsExtensionlessMediaButMustMatchContent() {
        val event = note("Hello https://cdn.example/hash", arrayOf("imeta", "url https://cdn.example/hash", "m video/mp4", "dim 1080x1920", "image https://cdn.example/poster.jpg", "alt A bird"), arrayOf("imeta", "url https://cdn.example/absent", "m image/jpeg"))
        val media = MediaContent.attachments(event)
        assertEquals(1, media.size)
        assertEquals(MediaContent.Kind.VIDEO, media.single().kind)
        assertEquals(9f / 16f, media.single().ratio)
        assertEquals("https://cdn.example/poster.jpg", media.single().preview)
        assertEquals("Hello", MediaContent.text(event, media))
    }
    @Test fun malformedDimensionsAndUntrustedSchemesStayBounded() {
        val event = note("http://cdn.example/no.jpg https://u:p@cdn.example/no.jpg https://cdn.example/yes.png", arrayOf("imeta", "url https://cdn.example/yes.png", "dim NaNx0", "image file:///secret"))
        val media = MediaContent.attachments(event)
        assertEquals(1, media.size)
        assertTrue(media.single().ratio.isFinite())
        assertNull(media.single().preview)
        assertNull(MediaContent.secureUrl("file:///secret"))
    }
    @Test fun attachmentsDeduplicateAndLimitAutomaticDownloads() {
        val event = note((1..10).joinToString(" ") { "https://cdn.example/$it.jpg https://cdn.example/$it.jpg" })
        val media = MediaContent.attachments(event)
        assertEquals(4, media.size)
        assertEquals(4, media.map { it.url }.distinct().size)
        assertTrue(MediaContent.text(event, media).contains("https://cdn.example/5.jpg"))
    }
    @Test fun removingRenderedMediaKeepsOtherLinksAndPunctuation() {
        val event = note("Photo https://cdn.example/a.jpg, page https://cdn.example/a.jpg.html")
        assertEquals("Photo , page https://cdn.example/a.jpg.html", MediaContent.text(event, MediaContent.attachments(event)))
    }
    @Test fun splitBudgetsAlwaysSumToSelectedStorageLimit() {
        MediaContent.cacheOptionsMb.forEach { mb ->
            assertEquals(mb * 1024L * 1024, MediaContent.imageBudget(mb) + MediaContent.videoBudget(mb))
            assertTrue(MediaContent.videoBudget(mb) > MediaContent.imageBudget(mb))
        }
    }
    @Test fun autoplayRequiresMostlyVisibleMediaWithinViewport() {
        assertNull(MediaContent.visibleScore(900f, 1200f, 300f, 100f, 1000f))
        assertNull(MediaContent.visibleScore(-400f, -100f, 300f, 100f, 1000f))
        assertNull(MediaContent.visibleScore(100f, 400f, 0f, 100f, 1000f))
        assertNotNull(MediaContent.visibleScore(300f, 600f, 300f, 100f, 1000f))
        assertTrue(MediaContent.visibleScore(400f, 700f, 300f, 100f, 1000f)!! < MediaContent.visibleScore(100f, 400f, 300f, 100f, 1000f)!!)
    }
}
