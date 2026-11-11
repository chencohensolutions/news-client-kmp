package news.app

import news.app.stories.Story
import news.core.model.ArticleSummary
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingTest {

    private fun item(url: String, source: String = "s", category: String? = null) = ArticleSummary(source, url, url, category = category)

    @Test
    fun readingOrderFollowsTheView() {
        val items = listOf(item("a", category = "world"), item("b", category = "politics"), item("c", "hidden"), item("d"))
        val state = HeadlinesState(items = items, hiddenSources = setOf("hidden"))
        assertEquals(listOf("a", "b", "d"), state.copy(view = HeadlinesView.LATEST).readingOrder.map { it.single().url })
        // Categories go in Categories.IDS order (politics before world), uncategorized last.
        assertEquals(listOf("b", "a", "d"), state.copy(view = HeadlinesView.CATEGORIES).readingOrder.map { it.single().url })
        // Before stories are ready, Stories reads like Latest.
        assertEquals(listOf("a", "b", "d"), state.copy(view = HeadlinesView.STORIES).readingOrder.map { it.single().url })
        val stories = StoriesState.Ready(listOf(Story(listOf(items[3], items[0])), Story(listOf(items[1]))), items, 0.9f)
        // Untimed reports keep feed order.
        assertEquals(listOf(listOf("a", "d"), listOf("b")), state.copy(view = HeadlinesView.STORIES, stories = stories).readingOrder.map { p -> p.map { it.url } })
    }

    @Test
    fun allShowsTheStoriesAndEveryHeadlineInNoneNewestFirst() {
        fun at(url: String, minute: Int) = item(url).copy(publishedAt = Instant.fromEpochSeconds(minute * 60L))
        val items = listOf(at("a", 1), at("b", 4), at("c", 3), at("d", 2), item("e"))
        val stories = StoriesState.Ready(listOf(Story(listOf(items[0], items[3]))), items, 0.9f)
        val all = HeadlinesState(items = items, stories = stories, view = HeadlinesView.ALL)
        // The story (newest report at minute 2) among the single headlines by time; the untimed one last, as in Latest.
        assertEquals(listOf(listOf("b"), listOf("c"), listOf("d", "a"), listOf("e")), all.readingOrder.map { p -> p.map { it.url } })
        // Before the stories are ready, every headline is its own.
        assertEquals(listOf("b", "c", "d", "a", "e"), all.copy(stories = StoriesState.Working).readingOrder.map { it.single().url })
        // Only the new: a story with a new report stays, whole ("d" is new)...
        assertEquals(listOf(listOf("b"), listOf("d", "a"), listOf("e")), all.copy(newUrls = setOf("b", "d", "e"), onlyNew = true, onlyNewUrls = setOf("b", "d", "e")).readingOrder.map { p -> p.map { it.url } })
        // ...and one left out doesn't come back as single headlines.
        assertEquals(listOf(listOf("b"), listOf("e")), all.copy(newUrls = setOf("b", "e"), onlyNew = true, onlyNewUrls = setOf("b", "e")).readingOrder.map { p -> p.map { it.url } })
    }

    @Test
    fun theHeaderFiltersShowSomeSitesCategoriesOrOnlyTheNew() {
        val items = listOf(item("a", "x", "politics"), item("b", "y", "politics"), item("c", "x"), item("d", "z", "sport"))
        val stories = StoriesState.Ready(listOf(Story(listOf(items[0], items[1])), Story(listOf(items[2], items[3]))), items, 0.9f)
        val state = HeadlinesState(items = items, stories = stories)
        // Sites: a story with a report from one shows whole, led by that report; stories are still grouped from all.
        assertEquals(listOf("a", "c"), state.copy(sites = setOf("x")).shownItems.map { it.url })
        assertEquals(listOf(listOf("b", "a")), state.copy(sites = setOf("y")).shownStories.map { s -> s.reports.map { it.url } })
        assertEquals(listOf("a", "b", "c", "d"), state.copy(sites = setOf("y")).visibleItems.map { it.url })
        // Categories the same way; "other" for headlines with none.
        assertEquals(listOf("a", "b"), state.copy(categories = setOf("politics")).shownItems.map { it.url })
        assertEquals(listOf("c"), state.copy(categories = setOf(OTHER_CATEGORY)).shownItems.map { it.url })
        assertEquals(listOf(listOf("d", "c")), state.copy(categories = setOf("sport")).shownStories.map { s -> s.reports.map { it.url } })
        // In "הכל" a story led by a chosen site's report keeps its place by its newest report, not re-sorted.
        assertEquals(listOf(listOf("b", "a")), state.copy(sites = setOf("y"), view = HeadlinesView.ALL).readingOrder.map { p -> p.map { it.url } })
        // Only the new, without changing what stories are grouped from.
        val onlyNew = state.copy(newUrls = setOf("b"), onlyNew = true, onlyNewUrls = setOf("b"))
        assertEquals(listOf("b"), onlyNew.shownItems.map { it.url })
        // The new report leads its story.
        assertEquals(listOf(listOf("b", "a")), onlyNew.shownStories.map { s -> s.reports.map { it.url } })
        assertEquals(listOf("a", "b", "c", "d"), onlyNew.visibleItems.map { it.url })
    }

    @Test
    fun byTimeHeadlinesAreNewestFirstAndUntimedLast() {
        fun at(url: String, minute: Int) = item(url).copy(publishedAt = Instant.fromEpochSeconds(minute * 60L))
        val items = listOf(at("old", 1), item("untimed1"), at("new", 9), item("untimed2"), at("mid", 5))
        assertEquals(listOf("new", "mid", "old", "untimed1", "untimed2"), HeadlinesState(items = items).shownItems.map { it.url })
    }

    @Test
    fun readHistoryPersistsAndKeepsTheLatest() {
        val file = File(Files.createTempDirectory("read").toFile(), "read.txt")
        val history = ReadHistory(file)
        assertTrue(history.add("a"))
        assertFalse(history.add("a"))
        history.add("b")
        assertEquals(setOf("a", "b"), ReadHistory(file).all())

        (0..ReadHistory.LIMIT * 2).forEach { history.add("u$it") }
        val kept = ReadHistory(file).all()
        assertTrue(kept.size in ReadHistory.LIMIT..ReadHistory.LIMIT * 2)
        assertTrue("u${ReadHistory.LIMIT * 2}" in kept)
        assertFalse("a" in kept)

        history.clear()
        assertEquals(emptySet(), ReadHistory(file).all())
    }

    @Test
    fun readHistoryAddsManyAtOnce() {
        val file = File(Files.createTempDirectory("read").toFile(), "read.txt")
        val history = ReadHistory(file)
        history.add("a")
        history.addAll(listOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), ReadHistory(file).all())
        assertEquals(listOf("a", "b", "c"), file.readLines())
        // Past the limit, the oldest go, as with one at a time.
        history.addAll((0..ReadHistory.LIMIT * 2).map { "u$it" })
        val kept = ReadHistory(file).all()
        assertTrue(kept.size in ReadHistory.LIMIT..ReadHistory.LIMIT * 2)
        assertFalse("a" in kept)
    }
}
