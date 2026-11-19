package news.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import news.core.model.ArticleSummary

class SavedArticlesTest {

    private fun item(url: String) = ArticleSummary("s", "https://news.example/$url", "כותרת $url")
    private fun at(seconds: Long) = Instant.fromEpochSeconds(1_800_000_000L + seconds)

    @Test
    fun theLatestSavedComesFirstAndSavingAgainTakesItOut() {
        val dir = Files.createTempDirectory("saved").toFile()
        val saved = SavedArticles(File(dir, SavedArticles.FILE))
        saved.toggle(item("a"), at(0))
        saved.toggle(item("b"), at(1))
        assertEquals(listOf("b", "a"), saved.all().map { it.summary.url.substringAfterLast('/') })
        saved.toggle(item("b"), at(2))
        assertEquals(listOf("a"), saved.all().map { it.summary.url.substringAfterLast('/') })
    }

    @Test
    fun theListIsKeptBetweenRuns() {
        val file = File(Files.createTempDirectory("saved").toFile(), SavedArticles.FILE)
        SavedArticles(file).apply {
            toggle(item("a"), at(0))
            toggle(item("b"), at(5))
        }
        val again = SavedArticles(file).all()
        assertEquals(listOf(item("b"), item("a")), again.map { it.summary })
        assertEquals(at(5), again.first().savedAt)
    }

    @Test
    fun aFullerHeadlineReplacesTheSavedOneAndKeepsItsPlace() {
        val file = File(Files.createTempDirectory("saved").toFile(), SavedArticles.FILE)
        val saved = SavedArticles(file)
        saved.toggle(item("a"), at(0))
        saved.toggle(item("b"), at(1))
        val fuller = item("a").copy(imageUrl = "https://img.example/a.jpg", subtitle = "תת כותרת")
        saved.update(fuller)
        assertEquals(listOf(item("b"), fuller), SavedArticles(file).all().map { it.summary })
        assertEquals(at(0), saved.all().last().savedAt)
    }

    @Test
    fun removingAndABrokenFileLeaveAnEmptyList() {
        val file = File(Files.createTempDirectory("saved").toFile(), SavedArticles.FILE)
        val saved = SavedArticles(file)
        saved.toggle(item("a"), at(0))
        saved.remove(item("a").url)
        assertTrue(SavedArticles(file).all().isEmpty())
        file.writeText("not json")
        assertTrue(SavedArticles(file).all().isEmpty())
    }
}
