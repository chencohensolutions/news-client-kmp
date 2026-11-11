package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class OlderFeedTest {

    private fun at(url: String, minute: Int, source: String = "s") =
        ArticleSummary(source, url, url, publishedAt = Instant.fromEpochSeconds(minute * 60L))

    @Test
    fun olderHeadlinesShowAmongTheFeedsByTime() {
        val state = HeadlinesState(items = listOf(at("new", 10), at("mid", 5)), older = listOf(at("old", 1), at("older", 7)))
        assertEquals(listOf("new", "older", "mid", "old"), state.copy(view = HeadlinesView.LATEST).readingOrder.map { it.single().url })
    }

    @Test
    fun aHeadlineStillInTheFeedIsShownOnceAsTheFeedHasIt() {
        val live = at("a", 10).copy(title = "from the feed")
        val state = HeadlinesState(items = listOf(live), older = listOf(at("a", 10), at("b", 1)))
        assertEquals(listOf(live, at("b", 1)), state.allItems)
    }

    @Test
    fun aHiddenSitesOlderHeadlinesAreHiddenToo() {
        val state = HeadlinesState(items = listOf(at("a", 10)), older = listOf(at("b", 1, source = "off")), hiddenSources = setOf("off"))
        assertEquals(listOf("a"), state.visibleItems.map { it.url })
    }
}
