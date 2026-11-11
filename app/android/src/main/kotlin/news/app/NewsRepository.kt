package news.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import news.app.server.NewsServer
import news.core.model.Article
import news.core.model.ArticleSummary
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The news, all of it from the news server ([NewsServer]): the headlines ([latest]), the articles ([article]) and the
 * sites ([sites]). The phone never reads the sites itself. Articles go to [cache]; every article returned goes to
 * [onArticle] (the media dates). One per process ([NewsApplication]), shared by the app and the background refresh.
 */
class NewsRepository(
    context: Context,
    val cache: FeedCache,
    val server: NewsServer,
    private val onArticle: (Article) -> Unit = {},
) {

    private val siteList = SiteList(File(context.filesDir, "sites.json"))

    private val _sites = MutableStateFlow(siteList.load())

    /** The sites the server reads, as it last said ([updateSites]); kept on the phone between starts. */
    val sites: StateFlow<List<Site>> = _sites

    /** The sites now: the names and addresses the screens show. */
    val sources: List<Site> get() = _sites.value

    fun source(id: String): Site? = _sites.value.firstOrNull { it.id == id }

    /** The server's sites, saved for the next start. */
    fun updateSites(sites: List<Site>) {
        if (sites == _sites.value) return
        _sites.value = sites
        siteList.save(sites)
    }

    /**
     * Articles already on the phone (for the feed, in advance, or by opening them), so opening one again is instant.
     * Kept, here and in [cache], while their headline is still in the feed.
     */
    private val articles = ConcurrentHashMap<String, Article>()

    /** The feed's articles the server didn't have at the last refresh ([latest]): in advance, not asked again. */
    private val notOnServer: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Where [article] looks when the server can't send it: the archive. */
    var fallback: (String) -> Article? = { null }

    /**
     * Where [article] looks before the server, after the phone: the archive, so an article the cache let go of isn't
     * downloaded again. Kept in memory only: the archive is not read into the cache.
     */
    var archived: (String) -> Article? = { null }

    /** Whether [url]'s article is in the archive ([ArticleArchive.contains]). */
    var inArchive: (String) -> Boolean = { false }

    /** Whether [url]'s article opens without the server: it is on the phone or in the archive. */
    fun canOpen(url: String): Boolean = hasArticle(url) || inArchive(url)

    /** The cache's text quota ([AppSettings.articleCacheMb]) in bytes; 0: no limit. */
    @Volatile
    var articleQuotaBytes: Long = 0

    /**
     * The server's feed ([NewsServer.headlines]) without the sites in [skip]. The articles the phone lacks come from
     * the server first, so the feed shows them at once ([NewsViewModel] shows a headline once its article is in). When
     * the server doesn't answer, the feed stays as it was.
     */
    suspend fun latest(skip: Set<String> = emptySet()): Headlines = withContext(Dispatchers.IO) {
        server.sources()?.let { list -> updateSites(list.map { it.toSite() }) }
        val items = server.headlines(skip) ?: return@withContext cache.loadHeadlines() ?: Headlines(emptyList())
        val missing = items.filter { !hasArticle(it.url) }.map { it.url }
        val got = server.articles(missing)
        got.values.forEach { article ->
            if (article.hasContent) {
                articles[article.url] = article
                cache.saveArticle(article)
            }
        }
        // Not asked again one by one in advance, until the next refresh asks for them all again.
        notOnServer.clear()
        if (server.answering) notOnServer.addAll(missing.filter { it !in got })
        val previous = cache.loadHeadlines()
        val feed = withoutFutureTimes(items, Clock.System.now())
        val current = feed.mapTo(HashSet()) { it.url }
        articles.keys.retainAll(current)
        // Articles stay on the phone after their headline leaves the feed, until the text quota.
        cache.trim(articleQuotaBytes, keep = current, available = ::canOpen)
        // The stories the cache holds stay with the new feed until they are asked again for it.
        Headlines(
            items = feed,
            failures = emptyMap(),
            fetchedAt = System.currentTimeMillis(),
            stories = previous?.stories,
            storiesThreshold = previous?.storiesThreshold,
        ).also(cache::saveHeadlines)
    }

    /** Whether [url]'s article is already on the phone, so opening it needs no download. */
    fun hasArticle(url: String): Boolean = articles.containsKey(url) || cache.hasArticle(url)

    /**
     * [url]'s article as the phone has it (memory or cache), never from the server; null when it has none. Not kept in
     * memory when read from the cache: the whole feed's are read this way. Blocking.
     */
    fun savedArticle(url: String): Article? = articles[url] ?: cache.loadArticle(url)?.takeIf { it.hasContent }

    /**
     * From memory or the cache, else the archive's current copy, else from the server (saved to the cache), else,
     * when the server can't send it, from the archive. [userAsked] (opened by the owner) asks the server even for an
     * article it lacked at the last refresh.
     */
    suspend fun article(@Suppress("UNUSED_PARAMETER") sourceId: String, url: String, userAsked: Boolean = false): Article = withContext(Dispatchers.IO) {
        val article = articles[url] ?: cache.loadArticle(url)?.takeIf { it.hasContent }?.also { articles[url] = it }
            ?: archived(url)?.takeIf { it.hasContent }?.also { articles[url] = it }
            ?: try {
                fromServer(url, userAsked)
            } catch (e: NotOnServerException) {
                fallback(url) ?: throw e
            }
        article.also(onArticle)
    }

    /** [url]'s article from the server; [NotOnServerException] when it lacks it or can't send it just now. */
    private suspend fun fromServer(url: String, userAsked: Boolean): Article {
        if (!userAsked && url in notOnServer && server.answering) throw NotOnServerException(url)
        server.articles(listOf(url))[url]?.takeIf { it.hasContent }?.let { article ->
            articles[url] = article
            cache.saveArticle(article)
            return article
        }
        throw NotOnServerException(url)
    }
}

/**
 * [items] without the times that are later than [now] by more than a few minutes: a site's clock gone wrong. Such a
 * time shows as "now" and holds the headline at the top of the feed; without it, the article's time fills in.
 */
internal fun withoutFutureTimes(items: List<ArticleSummary>, now: Instant): List<ArticleSummary> {
    val latest = now + FUTURE_SLACK
    return items.map { item -> if (item.publishedAt?.let { it > latest } == true) item.copy(publishedAt = null) else item }
}

/** How far ahead of the phone's clock a headline's time may be (clocks differ a little) before it is ignored. */
private val FUTURE_SLACK = 10.minutes

/**
 * Whether an article gave anything to read: text, photos, a video or at least a subtitle (a quiz has only that). A
 * title alone is a site's error or block page.
 */
internal val Article.hasContent: Boolean
    get() = body.isNotEmpty() || blocks.isNotEmpty() || video != null || !subtitle.isNullOrBlank()

/** The server doesn't have the article (yet), or can't send it just now. */
class NotOnServerException(url: String) : Exception("הכתבה עוד לא בשרת: $url")
