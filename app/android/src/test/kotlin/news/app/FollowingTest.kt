package news.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import news.app.server.FollowUpdate
import news.app.server.FollowedStory
import news.app.server.Sentence

class FollowingTest {

    private fun at(minutes: Long) = Instant.fromEpochSeconds(1_800_000_000L + minutes * 60)
    private fun update(minutes: Long, story: String = "f") = FollowUpdate(at(minutes), story, "כותרת", story != "f", listOf(Sentence("משפט.", emptyList())))

    @Test
    fun onlyUpdatesAfterTheLastLookCount() {
        val following = listOf(
            FollowedStory("f", "סערה", at(0), emptyList(), listOf(update(30), update(20, "j"), update(5))),
            FollowedStory("g", "בחירות", at(1), emptyList(), listOf(update(25, "g"))),
        )
        assertEquals(4, unseenUpdates(following, Instant.DISTANT_PAST))
        assertEquals(3, unseenUpdates(following, at(10)))
        assertEquals(0, unseenUpdates(following, at(30)))
    }
}
