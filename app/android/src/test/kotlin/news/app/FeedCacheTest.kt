package news.app

import news.core.model.Article
import news.core.model.ArticleSummary
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FeedCacheTest {

    private fun item(url: String) = ArticleSummary("s", url, "title $url")

    @Test
    fun theFeedKeepsItsFetchTimeAndStories() {
        val cache = FeedCache(Files.createTempDirectory("feed").toFile())
        val headlines = Headlines(
            items = listOf(item("a"), item("b"), item("c")),
            failures = mapOf("x" to "x: 503"),
            fetchedAt = 1_800_000_000_000L,
            stories = listOf(listOf("b", "a")),
            storiesThreshold = 0.9f,
        )
        cache.saveHeadlines(headlines)
        assertEquals(headlines, cache.loadHeadlines())
    }

    @Test
    fun aFeedSavedBeforeStoriesWereKeptStillLoads() {
        val root = Files.createTempDirectory("feed").toFile()
        val cache = FeedCache(root)
        val file = File(root, "v3/headlines.json")
        file.parentFile.mkdirs()
        file.writeText("""{"items":[{"sourceId":"s","url":"a","title":"t"}],"failures":{}}""")
        val loaded = cache.loadHeadlines()!!
        assertEquals(listOf("a"), loaded.items.map { it.url })
        assertNull(loaded.fetchedAt)
        assertNull(loaded.stories)
    }

    private fun article(url: String) = Article("s", url, "title $url", body = listOf("x".repeat(1000)))

    private fun at(url: String, minute: Int) = item(url).copy(publishedAt = Instant.fromEpochSeconds(minute * 60L))

    @Test
    fun headlinesThatLeftTheFeedStayAsOlderOnesWithTheirArticles() {
        val root = Files.createTempDirectory("feed").toFile()
        val cache = FeedCache(root)
        listOf("a", "b", "c").forEach { cache.saveArticle(article(it)) }
        cache.saveHeadlines(Headlines(items = listOf(at("a", 1), at("b", 3), at("c", 2))))
        // The next feed has only c: a and b are older ones, newest first, after a restart too.
        cache.saveHeadlines(Headlines(items = listOf(at("c", 2), item("d"))))
        cache.trim(0, keep = setOf("c", "d"))
        assertEquals(listOf("b", "a"), FeedCache(root).older(except = setOf("c", "d")).map { it.url })
        assertTrue(cache.hasArticle("a"))
    }

    @Test
    fun pastTheQuotaTheOldestArticlesGoButNeverTheFeeds() {
        val root = Files.createTempDirectory("feed").toFile()
        val cache = FeedCache(root)
        listOf("old", "mid", "new").forEachIndexed { i, url ->
            cache.saveArticle(article(url))
            File(root, "v3/articles/" + FeedCache.fileName(url)).setLastModified(1_000_000L * (i + 1))
        }
        cache.saveHeadlines(Headlines(items = listOf(item("old"), item("mid"), item("new"))))
        val one = cache.articleBytes / 3
        // Room for two: the oldest goes, unless the feed has it.
        cache.trim(one * 2 + 10, keep = setOf("old"))
        assertEquals(listOf(true, false, true), listOf("old", "mid", "new").map(cache::hasArticle))
        // Its headline goes with it.
        assertEquals(listOf("new"), cache.older(except = setOf("old")).map { it.url })
    }
}
