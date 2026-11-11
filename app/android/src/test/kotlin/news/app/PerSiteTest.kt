package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class PerSiteTest {

    @Test
    fun everySiteDownloadsAtOnceEachOneArticleAtATime() = kotlinx.coroutines.runBlocking {
        val items = listOf("a" to 1, "a" to 2, "a" to 3, "b" to 1, "c" to 1, "b" to 2).map { (site, n) -> ArticleSummary(site, "$site$n", "t") }
        val running = HashMap<String, Int>()
        var mostSites = 0
        val order = mutableListOf<String>()
        perSite(items) { item ->
            synchronized(running) {
                running[item.sourceId] = (running[item.sourceId] ?: 0) + 1
                // Never two of one site at once.
                assertEquals(1, running[item.sourceId])
                mostSites = maxOf(mostSites, running.size)
                order += item.url
            }
            kotlinx.coroutines.delay(10)
            synchronized(running) { running.remove(item.sourceId) }
        }
        // All three sites were downloading together; each site's in order.
        assertEquals(3, mostSites)
        assertEquals(listOf("a1", "a2", "a3"), order.filter { it.startsWith("a") })
    }
}
