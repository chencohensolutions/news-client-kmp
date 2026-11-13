package news.app

import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.server.StoryUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WhatsNewTest {

    private fun at(minute: Int) = Instant.fromEpochMilliseconds(minute * 60_000L)

    private fun s(text: String) = Sentence(text, listOf(Footnote("https://harbor.example/news/$text", "harbor")))

    private val updates = listOf(
        StoryUpdate(at(0), listOf(s("first")), emptyList()),
        StoryUpdate(at(10), listOf(s("closed"), s("nobody hurt")), listOf("development", "detail")),
        StoryUpdate(at(20), listOf(s("three lanes"), s("mayor")), listOf("correction", "reaction")),
        StoryUpdate(at(30), listOf(s("bypass"), s("renovated")), listOf("development", "background")),
    )

    private fun summary(updates: List<StoryUpdate> = this.updates, writtenAt: Instant = at(30), versions: Int = 4) =
        StorySummary("title", emptyList(), writtenAt = writtenAt, id = "x", versions = versions, version = versions, updates = updates)

    private fun texts(lines: List<WhatsNewLine>) = lines.map { it.sentence.text }

    @Test
    fun firstVisitHasNoBlock() {
        assertNull(whatsNew(summary(), null))
    }

    @Test
    fun developmentsAndCorrectionsSinceTheVisitNewestFirst() {
        val news = whatsNew(summary(), at(15))!!
        assertEquals(listOf("bypass", "three lanes"), texts(news.lines))
        assertEquals(listOf(UpdateKind.DEVELOPMENT, UpdateKind.CORRECTION), news.lines.map { it.kind })
        assertEquals(listOf("renovated", "mayor"), texts(news.more))
    }

    @Test
    fun theFirstVersionIsNeverAnUpdate() {
        val news = whatsNew(summary(), at(-5))!!
        assertTrue("first" !in texts(news.lines + news.more))
        assertEquals(listOf("bypass", "three lanes", "closed"), texts(news.lines))
    }

    @Test
    fun nothingNewAfterTheLatestVersion() {
        val news = whatsNew(summary(), at(30))!!
        assertTrue(news.isEmpty)
        assertNull(cardUpdate(summary(), at(30)))
    }

    @Test
    fun withoutKindsEachVersionsFirstSentenceIsALine() {
        val plain = updates.map { it.copy(kinds = emptyList()) }
        val news = whatsNew(summary(plain), at(15))!!
        assertEquals(listOf("bypass", "three lanes"), texts(news.lines))
        assertEquals(listOf(null, null), news.lines.map { it.kind })
        assertEquals(listOf("renovated", "mayor"), texts(news.more))
    }

    @Test
    fun unknownKindsWaitBehindMore() {
        val odd = listOf(updates[0], StoryUpdate(at(10), listOf(s("odd")), listOf("rumor")))
        val news = whatsNew(summary(odd, at(10), 2), at(5))!!
        assertTrue(news.lines.isEmpty())
        assertEquals(listOf("odd"), texts(news.more))
    }

    @Test
    fun cardShowsTheNewestDevelopmentAndCountsTheRest() {
        val card = cardUpdate(summary(), at(15))!!
        assertEquals("bypass", card.line?.sentence?.text)
        assertEquals(3, card.others)
    }

    @Test
    fun cardFallsBackToTheLatestUpdatesFirstSentenceWithoutKinds() {
        val plain = updates.map { it.copy(kinds = emptyList()) }
        assertEquals("bypass", cardUpdate(summary(plain), at(25))?.line?.sentence?.text)
    }

    @Test
    fun cardWhenOnlyMinorUpdatesCame() {
        val card = cardUpdate(summary(), at(25))!!
        assertEquals("bypass", card.line?.sentence?.text)
        val minor = updates.take(3) + StoryUpdate(at(30), listOf(s("renovated")), listOf("background"))
        assertEquals("renovated", cardUpdate(summary(minor), at(25))?.line?.sentence?.text)
    }

    @Test
    fun noCardUpdateForAStoryNeverOpened() {
        assertNull(cardUpdate(summary(), null))
    }

    @Test
    fun newerVersionFromAServerWithoutUpdatesStillSaysUpdated() {
        val card = cardUpdate(summary(emptyList()), at(15))!!
        assertNull(card.line)
    }

    @Test
    fun kindsReadAnyCase() {
        assertEquals(UpdateKind.CORRECTION, UpdateKind.of("Correction"))
        assertNull(UpdateKind.of("rumor"))
    }
}
