package news.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class StorySeenTest {

    @Test
    fun keepsTheLatestOpeningBetweenRuns() {
        val file = Files.createTempDirectory("seen").resolve("stories-seen.txt").toFile()
        StorySeen(file).apply {
            open("a", Instant.fromEpochMilliseconds(1000))
            open("b", Instant.fromEpochMilliseconds(2000))
            open("a", Instant.fromEpochMilliseconds(3000))
        }
        assertEquals(
            mapOf("b" to Instant.fromEpochMilliseconds(2000), "a" to Instant.fromEpochMilliseconds(3000)),
            StorySeen(file).all(),
        )
    }

    @Test
    fun dropsTheOldestPastTheLimit() {
        val file = Files.createTempDirectory("seen").resolve("stories-seen.txt").toFile()
        val seen = StorySeen(file, limit = 2)
        (1..5).forEach { seen.open("s$it", Instant.fromEpochMilliseconds(it * 1000L)) }
        assertEquals(setOf("s4", "s5"), StorySeen(file).all().keys)
    }
}
