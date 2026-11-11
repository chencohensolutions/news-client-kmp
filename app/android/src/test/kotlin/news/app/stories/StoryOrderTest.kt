package news.app.stories

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class StoryOrderTest {

    private fun item(url: String, at: String?) = ArticleSummary("s", url, url, publishedAt = at?.let(Instant::parse))

    @Test
    fun newestStoryFirstAndUntimedLastInFeedOrder() {
        val old = Story(listOf(item("a", "2026-10-30T06:00:00Z"), item("b", "2026-10-30T07:00:00Z")))
        val new = Story(listOf(item("c", null), item("d", "2026-10-30T09:00:00Z")))
        val untimed1 = Story(listOf(item("e", null), item("f", null)))
        val untimed2 = Story(listOf(item("g", null), item("h", null)))
        val feed = listOf("g", "h", "e", "f").map { url -> item(url, null) }
        assertEquals(listOf("d", "b", "g", "e"), newestFirst(listOf(untimed1, old, untimed2, new), feed).map { it.lead.url })
    }

    @Test
    fun theNewestReportLeadsAndTheRestFollowNewestFirst() {
        val story = Story(listOf(item("w", "2026-10-30T05:00:00Z"), item("x", null), item("y", "2026-10-30T06:00:00Z"), item("z", "2026-10-30T08:00:00Z")))
        assertEquals(listOf("z", "y", "w", "x"), newestFirst(listOf(story), emptyList()).single().reports.map { it.url })
    }

    @Test
    fun oneSitesStoriesGoByThatSitesReports() {
        fun at(site: String, url: String, time: String) = ArticleSummary(site, url, url, publishedAt = Instant.parse(time))
        // Story a: yellow's report is old, but another site reported it since. Story b: yellow's report is newer.
        val a = Story(listOf(at("gamma", "a1", "2026-10-30T09:00:00Z"), at("yellow", "a2", "2026-10-30T05:00:00Z")))
        val b = Story(listOf(at("yellow", "b1", "2026-10-30T07:00:00Z")))
        assertEquals(listOf("a1", "b1"), latestFirst(listOf(b, a), emptyList()).map { it.lead.url })
        assertEquals(listOf("b1", "a1"), latestFirst(listOf(a, b), emptyList()) { it.sourceId == "yellow" }.map { it.lead.url })
    }

    @Test
    fun storiesAreGroupedFromTheLastDaysOnly() {
        val now = Instant.parse("2026-11-03T12:00:00Z")
        val items = listOf(item("new", "2026-11-02T12:00:00Z"), item("old", "2026-10-20T12:00:00Z"), item("untimed", null))
        assertEquals(listOf("new", "untimed"), withinDays(items, 7, now).map { it.url })
        assertEquals(items, withinDays(items, 0, now))
    }
}
