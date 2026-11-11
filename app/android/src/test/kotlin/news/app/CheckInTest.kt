package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInTest {

    private fun item(url: String, source: String = "s") = ArticleSummary(source, url, url)

    @Test
    fun newIsWhatWasNeitherViewedNorRead() {
        val feed = listOf(item("a"), item("b"), item("c"))
        assertEquals(setOf("c"), newUrlsOf(feed, viewed = setOf("a", "gone"), read = setOf("b")))
        assertEquals(setOf("a", "b", "c"), newUrlsOf(feed, viewed = emptySet(), read = emptySet()))
    }

    @Test
    fun anOldHeadlineIsntNewThoughNeverViewed() {
        fun at(url: String, millis: Long) = item(url).copy(publishedAt = kotlin.time.Instant.fromEpochMilliseconds(millis))
        val feed = listOf(at("fresh", 9_000), at("old", 1_000), item("undated-new"), item("undated-old"))
        val firstSeen = mapOf("undated-new" to 8_000L, "undated-old" to 2_000L)
        assertEquals(setOf("fresh", "undated-new"), newUrlsOf(feed, emptySet(), emptySet(), since = 5_000, firstSeen = firstSeen))
    }

    @Test
    fun onlyNewKeepsWhatWasNewWhenSwitchedOnThoughViewedSince() {
        val feed = listOf(item("a"), item("b"), item("c"))
        val on = HeadlinesState(items = feed, newUrls = setOf("a", "b"), onlyNew = true, onlyNewUrls = setOf("a", "b"), view = HeadlinesView.LATEST)
        // b viewed since: no longer marked new, still in the list.
        val viewed = on.copy(pastUrls = setOf("b"))
        assertEquals(listOf("a", "b"), viewed.shownItems.map { it.url })
        assertEquals(setOf("a"), viewed.markedNew)
    }

    @Test
    fun theCountFollowsTheSitesOnScreen() {
        val feed = listOf(item("a", "x"), item("b", "y"), item("c", "y"))
        val state = HeadlinesState(items = feed, newUrls = setOf("a", "b"))
        assertEquals(2, state.visibleNewCount)
        assertEquals(1, state.copy(hiddenSources = setOf("x")).visibleNewCount)
        assertEquals(1, state.copy(sites = setOf("y")).visibleNewCount)
    }
}
