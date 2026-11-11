package news.app

import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.stories.Story
import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class UnreadGeneratedTest {
    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private fun r(site: String, url: String, hoursAgo: Int = 1) = ArticleSummary(site, url, url, publishedAt = now - hoursAgo.hours)
    private val written = StorySummary("t", listOf(Sentence("s.", emptyList())), writtenAt = now)

    private val ferry = Story(listOf(r("yellow", "y1"), r("white", "w1")))
    private val plain = Story(listOf(r("yellow", "y2"), r("gamma", "n2")))
    private val old = Story(listOf(r("yellow", "y3", 30), r("white", "w3", 30)))
    private val summaries = listOf(ferry, old).flatMap { s -> s.reports.map { it.url to written } }.toMap()

    @Test
    fun onlyGeneratedStoriesCountUntilOpened() {
        // A story without a generated article doesn't count; one older than "חדש עד" neither.
        assertEquals(listOf(ferry), unreadGenerated(listOf(ferry, plain, old), summaries, emptySet(), withinHours = 24, now = now))
        assertEquals(listOf(ferry, old), unreadGenerated(listOf(ferry, plain, old), summaries, emptySet(), withinHours = 0, now = now))
        // Opened (the app records all its reports, whichever was opened): read.
        assertEquals(emptyList(), unreadGenerated(listOf(ferry), summaries, setOf("y1", "w1"), withinHours = 24, now = now))
    }

    @Test
    fun unreadAgainOnlyWhenANewSiteJoins() {
        val opened = setOf("y1", "w1")
        // Another yellow report: same sites, still read.
        val more = Story(ferry.reports + r("yellow", "y9"))
        assertEquals(emptyList(), unreadGenerated(listOf(more), summaries + ("y9" to written), opened, 24, now))
        // A new site joins: unread again.
        val joined = Story(ferry.reports + r("gamma", "n9"))
        assertEquals(listOf(joined), unreadGenerated(listOf(joined), summaries + ("n9" to written), opened, 24, now))
    }
}
