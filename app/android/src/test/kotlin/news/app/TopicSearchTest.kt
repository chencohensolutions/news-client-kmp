package news.app

import news.app.stories.Story
import news.app.stories.TopicMatch
import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TopicSearchTest {

    private fun at(url: String, minute: Int, source: String = "s") =
        ArticleSummary(source, url, url, publishedAt = Instant.fromEpochSeconds(minute * 60L))

    private fun rows(state: HeadlinesState) = state.shownAll.map { s -> s.reports.map { it.url } }

    private fun match(vararg urls: String) = urls.associateWith { TopicMatch("t", 60) }

    // A story of three sites (its lead "a", the newest), a story of two, and two headlines in none.
    private val items = listOf(at("a", 9, "x"), at("b", 8, "y"), at("c", 7, "z"), at("d", 6, "x"), at("e", 5, "y"), at("f", 4), at("g", 3))
    private val state = HeadlinesState(
        items = items,
        stories = StoriesState.Ready(listOf(Story(items.take(3)), Story(listOf(items[3], items[4]))), items, 0.9f),
        view = HeadlinesView.ALL,
    )

    @Test
    fun withNoTopicOnTheFeedIsAsBefore() {
        assertNull(state.topicMatches)
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e"), listOf("f"), listOf("g")), rows(state))
    }

    @Test
    fun aStoryWithAMatchingReportShowsWholeAndUnchanged() {
        // "c" matches: its story shows with its own lead "a" and every report, in its order; "c" is not led to the top.
        assertEquals(listOf(listOf("a", "b", "c")), rows(state.copy(topicMatches = match("c"))))
        // Two matches in one story: still that one row.
        assertEquals(listOf(listOf("a", "b", "c")), rows(state.copy(topicMatches = match("b", "c"))))
    }

    @Test
    fun aMatchInAStoryIsNotShownAgainOnItsOwn() {
        val shown = rows(state.copy(topicMatches = match("b", "e", "g")))
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e"), listOf("g")), shown)
        // Every headline in one row only.
        assertEquals(shown.flatten().distinct(), shown.flatten())
    }

    @Test
    fun nothingMatchingShowsNothing() {
        assertEquals(emptyList(), rows(state.copy(topicMatches = emptyMap())))
    }

    @Test
    fun theReaderAndTheNewCountFollowThePickedRows() {
        val searching = state.copy(topicMatches = match("e"), newUrls = setOf("a", "d"))
        assertEquals(listOf(listOf("d", "e")), searching.readingOrder.map { p -> p.map { it.url } })
        // Only "d" of the new is in the picked rows.
        assertEquals(1, searching.visibleNewCount)
        assertEquals(2, searching.copy(topicMatches = null).visibleNewCount)
    }

    @Test
    fun theOtherFiltersStillApply() {
        // Site y: the first story is led by its y report, as the site filter does; the search then picks it.
        assertEquals(listOf(listOf("b", "a", "c")), rows(state.copy(sites = setOf("y"), topicMatches = match("c"))))
        // Only the new: a story with no new report is out, whatever matches in it; one with a new report is led by it,
        // as that filter does.
        assertEquals(listOf(listOf("e", "d")), rows(state.copy(onlyNew = true, newUrls = setOf("e"), onlyNewUrls = setOf("e"), topicMatches = match("a", "e"))))
    }

    @Test
    fun topicsSurviveSavingWithTheirOrderAndSwitches() {
        val topics = listOf(SavedTopic("איראן", true), SavedTopic("supreme court", false), SavedTopic("אוכל, שתייה", true))
        assertEquals(topics, decodeTopics(encodeTopics(topics)))
        // A tab or a line break in a topic can't break the lines.
        assertEquals(listOf(SavedTopic("a b c", true)), decodeTopics(encodeTopics(listOf(SavedTopic("a\tb\nc", true)))))
        assertEquals(emptyList(), decodeTopics(null))
        assertEquals(listOf(SavedTopic("x", false)), decodeTopics("junk\n0\tx\n2\ty\n1\t"))
    }
}
