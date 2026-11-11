package news.app

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDurationTest {
    @Test
    fun isoDurationsBecomeClockTimes() {
        assertEquals("1:45", formatDuration("PT0H1M45S"))
        assertEquals("3:08", formatDuration("PT3M8S"))
        assertEquals("1:02:03", formatDuration("PT1H2M3S"))
        assertEquals("0:42", formatDuration("PT42.5S"))
        assertEquals(null, formatDuration("PT0S"))
        assertEquals(null, formatDuration("3 minutes"))
        assertEquals(null, formatDuration(null))
    }
}
