package news.app.stories

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicsTest {

    private fun item(url: String, title: String, subtitle: String? = null) =
        ArticleSummary(sourceId = "a", url = url, title = title, subtitle = subtitle)

    @Test
    fun scoresAreTheShareOfTheTopicsWords() {
        val items = listOf(
            item("1", "Heavy rain expected in the north"),
            item("2", "Rain tomorrow", subtitle = "Weather turns cold"),
            item("3", "Elections called"),
        )
        val scores = wordScores("rain weather", items)
        assertEquals(mapOf("1" to 50, "2" to 100), scores)
    }

    @Test
    fun aOneLetterPrefixStillMatches() {
        assertEquals(mapOf("1" to 100), wordScores("גשם", listOf(item("1", "וגשם ממשיך"), item("2", "בחירות"))))
    }

    @Test
    fun theBestTopicOnAboveTheThresholdWins() {
        val scores = mapOf("a" to mapOf("x" to 50, "y" to 100), "b" to mapOf("x" to 100))
        assertEquals(mapOf("x" to TopicMatch("b", 100), "y" to TopicMatch("a", 100)), topicMatches(scores, listOf("a", "b"), 55))
        assertEquals(mapOf("y" to TopicMatch("a", 100)), topicMatches(scores, listOf("a"), 55))
    }
}
