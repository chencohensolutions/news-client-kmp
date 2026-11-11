package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NotificationsTest {

    private fun at(url: String, minute: Int) = ArticleSummary("s", url, url, publishedAt = Instant.fromEpochSeconds(minute * 60L))

    @Test
    fun onlyNewUnreadArticlesOfUnreadStoriesOnePerStory() {
        val fresh = listOf(at("old", 1), at("a", 2), at("b", 5), at("c", 4), at("read", 6), at("d", 3), at("e", 7))
        val previous = setOf("old")
        val read = setOf("read", "x")
        // a and b report one story; d is in a story with x, which was read.
        val stories = listOf(listOf("a", "b"), listOf("d", "x"))
        // Newest first: e, then b for its story (a goes with it), then c; not old (seen), read, or d (its story read).
        assertEquals(listOf("e", "b", "c"), worthNotifying(fresh, previous, read, stories).map { it.url })
        assertEquals(listOf("e", "b"), worthNotifying(fresh, previous, read, stories, max = 2).map { it.url })
        // A first run notifies nothing: the whole feed would be new.
        assertEquals(emptyList(), worthNotifying(fresh, null, read, stories))
        // Without stories, each article for itself.
        assertEquals(listOf("e", "b", "c", "d", "a"), worthNotifying(fresh, previous, read, null).map { it.url })
    }

    @Test
    fun theCombinedNotificationAddsTheNewOnTopOfWhatItListed() {
        fun line(url: String) = CombinedLine(url, "yellow", "כותרת $url")
        val before = listOf(line("b"), line("c"), line("d"))
        // New on top; one listed already isn't twice; one read since leaves.
        assertEquals(listOf("a", "b", "d"), combinedLines(listOf(line("a"), line("b")), before, read = setOf("c"), max = 30).map { it.url })
        // At most max, the newest kept.
        assertEquals(listOf("a", "b"), combinedLines(listOf(line("a")), before, read = emptySet(), max = 2).map { it.url })
    }

    @Test
    fun aCombinedLineSurvivesItsFile() {
        val line = CombinedLine("https://x.co/a?b=1", "חדשות הנמל", "כותרת\tעם טאב")
        assertEquals(CombinedLine("https://x.co/a?b=1", "חדשות הנמל", "כותרת עם טאב"), CombinedLine.parse(line.line()))
        assertEquals(null, CombinedLine.parse("broken"))
    }
}
