package news.app.server

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ServerFeedTest {

    private val t0 = Instant.parse("2026-11-10T08:00:00Z")

    private fun row(site: String, url: String, firstSeen: Int, lastSeen: Int, title: String = url) =
        ServerHeadline(ArticleSummary(site, url, title), t0 + firstSeen.minutes, t0 + lastSeen.minutes)

    @Test
    fun aHeadlineSeenAgainReplacesTheOneKeptButKeepsWhenItWasFirstSeen() {
        val known = listOf(row("yellow", "y1", 0, 0, "old title"), row("yellow", "y2", 0, 0))
        // The server answers its own firstSeen; the phone keeps the earliest it knew, should they differ.
        val merged = ServerFeed.merge(known, listOf(row("yellow", "y1", 5, 30, "new title"), row("white", "w1", 30, 30)))
        assertEquals(listOf("y1", "y2", "w1"), merged.map { it.summary.url })
        assertEquals("new title", merged[0].summary.title)
        assertEquals(t0, merged[0].firstSeen)
        assertEquals(t0 + 30.minutes, merged[0].lastSeen)
    }

    @Test
    fun theNextAskIsFromTheNewestSighting() {
        assertEquals(null, ServerFeed.since(emptyList()))
        assertEquals(t0 + 40.minutes, ServerFeed.since(listOf(row("a", "a1", 0, 40), row("b", "b1", 0, 10))))
    }

    @Test
    fun theFeedIsEachSitesPageAtItsLastRead() {
        val rows = listOf(
            // yellow read at 60: y1 and y3 are on its page; y2 left it at 20.
            row("yellow", "y1", 0, 60), row("yellow", "y2", 0, 20), row("yellow", "y3", 50, 58),
            // white failed since 10: its page as it was then stays.
            row("white", "w1", 0, 10), row("white", "w2", 5, 10),
        )
        // Newest first within a site (by when the server first saw them), the sites interleaved, by site id.
        assertEquals(listOf("w2", "y3", "w1", "y1"), ServerFeed.current(rows).map { it.url })
        assertEquals(listOf("y3", "y1"), ServerFeed.current(rows, skip = setOf("white")).map { it.url })
    }

    @Test
    fun headlinesNotSeenForDaysAreForgotten() {
        val now = t0 + 4.days
        val rows = listOf(row("a", "old", 0, 0), row("a", "recent", 0, (2 * 24 * 60)))
        assertEquals(listOf("recent"), ServerFeed.prune(rows, now).map { it.summary.url })
    }

    @Test
    fun aStorysGeneratedArticleIsFoundByAnyOfItsReports() {
        val written = StorySummary("t", listOf(Sentence("s.", listOf(Footnote("y1")))), writtenAt = Instant.parse("2026-11-10T08:00:00Z"))
        val fromServer = listOf(ServerStory(listOf("y1", "w1", "gone"), written), ServerStory(listOf("y2", "n1")))
        val shown = listOf(
            news.app.stories.Story(listOf(ArticleSummary("white", "w1", "w1"), ArticleSummary("yellow", "y1", "y1"))),
            news.app.stories.Story(listOf(ArticleSummary("yellow", "y2", "y2"), ArticleSummary("gamma", "n1", "n1"))),
        )
        val summaries = ServerFeed.summaries(fromServer, shown)
        assertEquals(setOf("w1", "y1"), summaries.keys)
        assertEquals(written, summaries["y1"])
    }

    @Test
    fun theServersStoriesAreShownOverTheFeed() {
        val items = listOf(ArticleSummary("yellow", "y1", "y1"), ArticleSummary("white", "w1", "w1"), ArticleSummary("yellow", "y2", "y2"), ArticleSummary("gamma", "n1", "n1"))
        val groups = listOf(
            listOf("y1", "w1", "gone"),     // a report the feed doesn't show is left out
            listOf("y2", "y1"),             // one site only: not a story
            listOf("n1", "missing"),        // one report left: not a story
            listOf("w1", "n1", "n1"),       // a report twice counts once
        )
        assertEquals(listOf(listOf("y1", "w1"), listOf("w1", "n1")), ServerFeed.stories(groups, items).map { s -> s.reports.map { it.url } })
    }
}
