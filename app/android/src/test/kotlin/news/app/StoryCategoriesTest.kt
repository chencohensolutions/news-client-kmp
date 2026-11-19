package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryCategoriesTest {
    private fun report(url: String, category: String?) = ArticleSummary("site-$url", url, "headline $url", category = category)

    @Test
    fun aStoryWhoseLeadHasNoCategoryShowsItsOtherReportsOne() {
        assertEquals(listOf("world"), storyCategories(listOf(report("a", null), report("b", "world"), report("c", "world")), emptySet()))
    }

    @Test
    fun everyCategoryShowsOnceWithTheLeadsFirst() {
        assertEquals(
            listOf("politics", "world", "news"),
            storyCategories(listOf(report("a", "politics"), report("b", "world"), report("c", "politics"), report("d", "news")), emptySet()),
        )
    }

    @Test
    fun reportsAboutSomethingElseAreLeftOut() {
        assertEquals(listOf("world"), storyCategories(listOf(report("a", "world"), report("b", "sport")), setOf("b")))
    }
}
