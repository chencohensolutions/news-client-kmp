package news.app

import news.core.model.Article
import news.core.model.ArticleBlock
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MediaDatesTest {

    private fun file() = File(Files.createTempDirectory("media").toFile(), MediaDates.FILE)

    private fun article(url: String, minute: Long?, vararg pictures: String) = Article(
        "s", url, url, publishedAt = minute?.let { Instant.fromEpochSeconds(it * 60) }, imageUrl = pictures.first(),
        blocks = pictures.drop(1).map { ArticleBlock.Image(it) },
    )

    @Test
    fun theOldestArticlesPicturesGoFirstHoweverLateTheyWereSeen() {
        val file = file()
        val dates = MediaDates(file)
        dates.noteArticle(article("new", 100, "new.jpg"))
        dates.noteArticle(article("old", 1, "old.jpg", "old-photo.jpg"))
        // Seen in this order: the old article's pictures last, as when scrolling down an imported archive.
        listOf("new.jpg", "old.jpg", "old-photo.jpg").forEach(dates::cached)
        assertEquals(listOf("old.jpg", "old-photo.jpg", "new.jpg"), MediaDates(file).picturesOldestFirst())
    }

    @Test
    fun aPictureOfAnUndatedArticleCountsFromWhenItWasCached() {
        val dates = MediaDates(file())
        dates.noteArticle(article("dated", 50, "dated.jpg"))
        dates.noteArticle(article("undated", null, "undated.jpg"))
        dates.cached("undated.jpg", now = 10 * 60_000L)
        dates.cached("dated.jpg")
        assertEquals(listOf("undated.jpg", "dated.jpg"), dates.picturesOldestFirst())
    }

    @Test
    fun anHlsSegmentCountsByItsStreamsArticle() {
        val dates = MediaDates(file())
        dates.noteVideo("https://cdn.example/v/123/master.m3u8", publishedAt = 5L)
        assertEquals(5L, dates.videoDate("https://cdn.example/v/123/seg-7.ts"))
    }
}
