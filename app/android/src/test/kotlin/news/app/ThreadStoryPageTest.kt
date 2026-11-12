package news.app

import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.ThreadRef
import news.app.server.ThreadStory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ThreadStoryPageTest {

    private val at = Instant.parse("2026-11-10T08:00:00Z")

    private fun story(vararg sentences: Sentence) =
        ThreadStory(id = "s1", title = "A made-up story", sentences = sentences.toList(), firstWrittenAt = at, writtenAt = at)

    @Test
    fun aStoryTheFeedDoesntHaveIsMadeFromItsFootnotes() {
        val older = Footnote("https://one.example/a", "one", "First report", publishedAt = at)
        val newer = Footnote("https://two.example/b", "two", "Second report", video = true, publishedAt = at.plus(kotlin.time.Duration.parse("1h")))
        val thread = ThreadRef(id = "t1", name = "A made-up affair", storyCount = 1, startedAt = at, updatedAt = at)
        val (made, summary) = threadStoryPage(story(Sentence("One.", listOf(older)), Sentence("Two.", listOf(newer, older))), thread)!!
        // Each article once, newest first.
        assertEquals(listOf("https://two.example/b", "https://one.example/a"), made.reports.map { it.url })
        assertEquals(true, made.lead.video)
        assertEquals("s1", summary.id)
        assertEquals(listOf("One.", "Two."), summary.sentences.map { it.text })
        assertEquals(listOf(thread), summary.threads)
    }

    @Test
    fun aStoryIsPlacedNewestFirstByItsLatestReport() {
        val later = at.plus(kotlin.time.Duration.parse("26h"))
        val longRunning = story().copy(firstReportAt = at, lastReportAt = later)
        assertEquals(at, longRunning.startedAt)
        assertEquals(later, longRunning.latestAt)
        // No later report: where it began.
        assertEquals(at, story().copy(firstReportAt = at).latestAt)
    }

    @Test
    fun noFootnotesNoPage() {
        assertNull(threadStoryPage(story(Sentence("One.", emptyList())), null))
    }
}
