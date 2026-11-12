package news.app

import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.stories.Story
import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NewestVersionsTest {
    private val now = Instant.parse("2026-11-12T08:00:00Z")
    private fun r(site: String, url: String) = ArticleSummary(site, url, url)
    private fun written(id: String, at: Instant, version: Int, current: Boolean = true) =
        StorySummary("t", listOf(Sentence("s.", emptyList())), writtenAt = at, id = id, version = version, versions = 2, current = current)

    private val first = Story(listOf(r("yellow", "y1"), r("white", "w1")))
    private val second = Story(listOf(r("gamma", "g1"), r("white", "w2")))
    private val other = Story(listOf(r("yellow", "y3"), r("gamma", "g3")))
    private val plain = Story(listOf(r("yellow", "y4"), r("white", "w4")))

    private fun summaries(vararg pairs: Pair<Story, StorySummary>) = pairs.flatMap { (s, w) -> s.reports.map { it.url to w } }.toMap()

    @Test
    fun aStoryShowsOnceAtItsNewestVersion() {
        val summaries = summaries(first to written("a", now, 1), second to written("a", now + 30.minutes, 2), other to written("b", now, 1))
        // The newer version stays at its own place; the other stories, and one without a generated article, stay.
        assertEquals(listOf(other, second, plain), newestVersionsOnly(listOf(first, other, second, plain), summaries))
    }

    @Test
    fun aVersionWrittenFromTheReportsAsTheyAreGoesBeforeOneWaiting() {
        // Both show the same version: the one written from its reports as they are wins over the one whose next waits.
        val summaries = summaries(first to written("a", now, 2, current = false), second to written("a", now, 2))
        assertEquals(listOf(second), newestVersionsOnly(listOf(first, second), summaries))
    }

    @Test
    fun storiesWithoutAnIdAllStay() {
        val summaries = summaries(first to written("", now, 1), second to written("", now, 1))
        assertEquals(listOf(first, second), newestVersionsOnly(listOf(first, second), summaries))
    }
}
