package news.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AgoTest {

    private val now = 1_800_000_000_000L
    private fun at(minutesAgo: Long) = Instant.fromEpochMilliseconds(now - minutesAgo * 60_000)

    @Test
    fun shortHebrewBuckets() {
        assertNull(ago(null, now))
        assertEquals("עכשיו", ago(at(0), now))
        assertEquals("לפני 4 דק׳", ago(at(4), now))
        assertEquals("לפני שעה", ago(at(90), now))
        assertEquals("לפני 3 שע׳", ago(at(3 * 60 + 10), now))
        assertEquals("אתמול", ago(at(30 * 60), now))
        assertEquals("לפני 3 ימים", ago(at(72 * 60 + 5), now))
        // A clock that runs ahead of a site's never says "in 2 minutes".
        assertEquals("עכשיו", ago(Instant.fromEpochMilliseconds(now + 120_000), now))
    }

    @Test
    fun theLineUnderTheTitle() {
        assertEquals("מרענן…", checkInLine(at(4), 3, refreshing = true, now = now))
        assertEquals("עדיין לא עודכן", checkInLine(null, 0, refreshing = false, now = now))
        assertEquals("עודכן לפני 4 דק׳", checkInLine(at(4), 0, refreshing = false, now = now))
        assertEquals("עודכן עכשיו · 8 חדשות", checkInLine(at(0), 8, refreshing = false, now = now))
    }
}
