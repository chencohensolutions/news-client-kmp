package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FutureTimesTest {

    @Test
    fun timesWellAheadOfTheClockAreDropped() {
        val now = Instant.parse("2026-10-31T22:53:00Z")
        fun at(url: String, time: Instant?) = ArticleSummary("s", url, url, publishedAt = time)
        val items = listOf(at("past", now - 1.hours), at("slightly-ahead", now + 5.minutes), at("ahead", now + 11.hours), at("none", null))
        // A clock a few minutes fast is let be; 12:16 for 00:16 (a site with its clock wrong) is not.
        assertEquals(
            listOf(now - 1.hours, now + 5.minutes, null, null),
            withoutFutureTimes(items, now).map { it.publishedAt },
        )
    }
}
