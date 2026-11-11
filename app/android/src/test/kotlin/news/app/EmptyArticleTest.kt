package news.app

import news.core.model.Article
import news.core.model.ArticleVideo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyArticleTest {

    private fun article(body: List<String> = emptyList(), subtitle: String? = null, video: ArticleVideo? = null) =
        Article("gamma", "https://site/a", "We are sorry", subtitle = subtitle, body = body, video = video)

    @Test
    fun aTitleAloneIsAnErrorPageNotAnArticle() {
        assertFalse(article().hasContent)
        assertFalse(article(subtitle = " ").hasContent)
        assertTrue(article(body = listOf("text")).hasContent)
        // A quiz: a subtitle and a picture, no text.
        assertTrue(article(subtitle = "10 שאלות").hasContent)
        assertTrue(article(video = ArticleVideo(stream = "https://site/v.m3u8")).hasContent)
    }
}
