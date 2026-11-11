package news.app

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import news.core.model.Article
import news.core.model.ArticleSummary

/**
 * Brings the feed's articles from the server after a refresh, for the app and the background refresh alike: every
 * headline whose article isn't on the phone yet, newest first (the feed shows a headline only once its article is: its
 * data is the article's, not the list page's). Most come in the refresh's own batch ([NewsRepository.latest]); this
 * asks for the rest and fills each headline from its article.
 */
class ArticleFetcher(private val context: Context, private val repository: NewsRepository) {

    /** The articles of [items] that [run] goes through: those on the phone first (quick fills), then the rest, newest first. */
    fun queue(items: List<ArticleSummary>): List<ArticleSummary> =
        items.sortedWith(compareBy<ArticleSummary> { !repository.hasArticle(it.url) }.thenByDescending(nullsFirst()) { it.publishedAt })

    /**
     * Returns [items] with the pictures and times filled in. [onFilled] gets each headline as soon as its article is
     * on the phone (filled from it, or as it was when the article adds nothing), [onDownloaded] each article newly
     * downloaded.
     */
    suspend fun run(
        items: List<ArticleSummary>,
        onFilled: (ArticleSummary) -> Unit = {},
        onDownloaded: (Article) -> Unit = {},
    ): List<ArticleSummary> {
        val queue = queue(items)
        val filled = HashMap<String, ArticleSummary>()
        perSite(queue) { item ->
            val fresh = !repository.hasArticle(item.url)
            val article = runCatching { repository.article(item.sourceId, item.url) }.getOrNull()
                ?.also { if (fresh) onDownloaded(it) } ?: return@perSite
            // The article's data in place of the list page's.
            val updated = item.filledFrom(article)
            if (updated != item) synchronized(filled) { filled[item.url] = updated }
            onFilled(updated)
        }
        return items.map { filled[it.url] ?: it }
    }

    /**
     * Downloads [urls] into the picture cache, so they show without waiting. To disk only: full-size bitmaps of many
     * stories would push the pictures on screen out of memory.
     */
    fun prefetchImages(urls: Collection<String>) {
        val images = SingletonImageLoader.get(context)
        urls.distinct().forEach { url ->
            images.enqueue(ImageRequest.Builder(context).data(url).memoryCachePolicy(CachePolicy.DISABLED).build())
        }
    }

}

/**
 * This headline as its [article] has it: the article's own data in place of the list page's wherever the article gives
 * it, which is the real one (a list shortens titles and drops names, often has no time or the section's, a thumbnail,
 * no subtitle): its title, subtitle, main picture (the feed and stories show the picture the article opens with), time,
 * category and whether it has a video. The list's stays only where the article has none.
 */
internal fun ArticleSummary.filledFrom(article: Article): ArticleSummary =
    withSubtitleFrom(article).copy(
        title = article.title.takeIf { it.isNotBlank() } ?: title,
        imageUrl = article.imageUrl ?: imageUrl,
        category = article.category ?: category,
        video = video || article.video != null,
        publishedAt = article.publishedAt ?: publishedAt,
    )

/** Runs [fetch] on [items] every site at once, each site's one after another in order. */
internal suspend fun perSite(items: List<ArticleSummary>, fetch: suspend (ArticleSummary) -> Unit) = coroutineScope {
    items.groupBy { it.sourceId }.values.forEach { site -> launch { site.forEach { fetch(it) } } }
}
