package news.app

import news.app.server.Footnote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NewestPerSiteTest {

    private fun note(url: String, site: String?, at: Long?) =
        Footnote(url, site, "t", publishedAt = at?.let { Instant.fromEpochMilliseconds(it) })

    @Test
    fun oneLabelASiteOpeningItsNewestArticle() {
        val notes = listOf(note("a1", "a", 10), note("b1", "b", 5), note("a2", "a", 30), note("a3", "a", 20))
        // Each site once, in order of first citation; a's newest is a2.
        assertEquals(listOf("a2", "b1"), newestPerSite(notes).map { it.url })
    }

    @Test
    fun withoutTimesTheFirstCitedWins() {
        val notes = listOf(note("a1", "a", null), note("a2", "a", null), note("a3", "a", 5))
        assertEquals(listOf("a3"), newestPerSite(notes).map { it.url })
        assertEquals(listOf("a1"), newestPerSite(notes.take(2)).map { it.url })
    }

    @Test
    fun unknownSitesStayApart() {
        val notes = listOf(note("x", null, 1), note("y", null, 2))
        assertEquals(listOf("x", "y"), newestPerSite(notes).map { it.url })
    }
}
