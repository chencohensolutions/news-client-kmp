package news.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class StatsTest {
    @Test
    fun tokensAndSecondsAreShort() {
        assertEquals("940", tokens(940))
        assertEquals("17.2K", tokens(17_161))
        assertEquals("12K", tokens(12_000))
        assertEquals("1.2M", tokens(1_234_567))
        assertEquals("0.8 ש׳", seconds(0.772))
        assertEquals("34 ש׳", seconds(34.2))
        assertEquals("2:05 דק׳", seconds(125.0))
    }

    @Test
    fun googlesDayStartsAtMidnightPacific() {
        // 00:25 in Israel on Nov 11 (22:25 UTC on Nov 10) is 14:25 on Nov 10 in California: the day starts again at
        // midnight there, 08:00 UTC, 10:00 in Israel.
        assertEquals(Instant.parse("2026-11-11T08:00:00Z"), googleDayStart(Instant.parse("2026-11-10T22:25:00Z")))
        // Summer time: midnight Pacific is 07:00 UTC.
        assertEquals(Instant.parse("2026-07-02T07:00:00Z"), googleDayStart(Instant.parse("2026-07-01T12:00:00Z")))
    }
}
