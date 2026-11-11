package news.app

import news.core.model.ArticleBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeadImageTest {

    private fun photos(vararg urls: String) = listOf<ArticleBlock>(ArticleBlock.Text("פסקה")) + urls.map { ArticleBlock.Image(it, "כיתוב") }

    @Test
    fun mainImageShowsWhenNotInTheText() {
        val main = "https://cdn.site.co.il/2026/10/intel-shares.jpg"
        assertEquals(main, leadImage(main, photos("https://cdn.site.co.il/2026/10/tower-semiconductor.jpg")))
        assertEquals(main, leadImage(main, emptyList()))
        assertNull(leadImage(null, photos("https://cdn.site.co.il/a.jpg")))
    }

    @Test
    fun mainImageHidesWhenTheSamePhotoIsInTheText() {
        val main = "https://images.example/wp-content/uploads/2026/09/IMG_9151-1024x768.jpg"
        // The same URL.
        assertNull(leadImage(main, photos(main)))
        // Another size of the same photo (WordPress "-WxH"), with a query.
        assertNull(leadImage(main, photos("https://images.example/wp-content/uploads/2026/09/IMG_9151-768x576.jpg?w=768")))
        // The same file in another format and folder (a CDN crop).
        assertNull(leadImage("https://photos.example/Image/GG/7,1/1606240.webp", photos("https://photos.example/Image/GG/X/1606240.jpg")))
    }

    @Test
    fun shortFileNamesMatchOnlyByTheWholeUrl() {
        // "1.jpg" in two folders is two photos.
        val main = "https://img.site.co.il/2026/10/30/a/1.jpg"
        assertEquals(main, leadImage(main, photos("https://img.site.co.il/2026/10/29/b/1.jpg")))
        assertNull(leadImage(main, photos(main)))
    }
}
