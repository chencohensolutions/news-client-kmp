package news.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import news.core.model.Article
import news.core.model.ArticleSummary
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The last feed fetched, as the app shows it on its next start before refreshing: the headlines, the sites that
 * failed, when it was fetched, and the stories found for it ([stories]: groups of headline URLs, lead first, computed
 * at [storiesThreshold]), so Stories opens at once without waiting for the server.
 */
@Serializable
data class Headlines(
    val items: List<ArticleSummary>,
    val failures: Map<String, String> = emptyMap(),
    val fetchedAt: Long? = null,
    val stories: List<List<String>>? = null,
    val storiesThreshold: Float? = null,
    /** The [news.app.stories.StoryText] the stories were found by; none before it was a setting (the subtitle). */
    val storiesText: String? = null,
)

/**
 * The app's own copy of the last feed, of every article fetched and of every headline the feed has shown, in its
 * private storage, so the app opens on the last feed, articles open without a connection and are never downloaded
 * twice, and the feed goes on into older headlines ([older]). Articles stay until the text quota ([trim]) is reached,
 * the oldest going first; pictures and videos have their own caches and quotas. Removed with the app; the archive
 * ([ArticleArchive]) is what outlives it. Every file is written to a temp file and renamed, so a damaged write never
 * hides the others.
 */
class FeedCache(root: File) {

    /** A folder per format version: an app update that changes what an article holds starts a fresh cache. */
    private val dir = File(root, "v$VERSION").also { current ->
        root.listFiles().orEmpty().filter { it != current }.forEach { it.deleteRecursively() }
    }

    private val headlinesFile get() = File(dir, "headlines.json")
    private val articlesDir get() = File(dir, "articles")
    private val pagesDir get() = File(dir, "pages")
    private val indexFile get() = File(dir, "index.jsonl")

    /**
     * Every headline the feed has shown, by URL, as it last showed it, from [indexFile] (a headline in JSON per line);
     * one leaves with its article ([trim]).
     */
    private val index: MutableMap<String, ArticleSummary> by lazy {
        val text = runCatching { indexFile.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
        text.lineSequence().filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString(ArticleSummary.serializer(), it) }.getOrNull() }
            .associateByTo(LinkedHashMap()) { it.url }
    }

    fun loadHeadlines(): Headlines? = read(headlinesFile) { json.decodeFromString(Headlines.serializer(), it) }

    /** Saves the feed, and its headlines into the index, each as the feed has it now. */
    fun saveHeadlines(headlines: Headlines) {
        write(headlinesFile, json.encodeToString(Headlines.serializer(), headlines))
        synchronized(index) {
            headlines.items.forEach { index[it.url] = it }
            writeIndex()
        }
    }

    /**
     * The headlines the feed has shown, and those imported from the archive ([addHeadlines]), whose article can be
     * opened ([available]: on the phone, or in the archive), but [except] (the feed's own), newest first; those without
     * a time last. Reads the index file the first time: call off the main thread.
     */
    fun older(except: Set<String>, available: (String) -> Boolean = ::hasArticle): List<ArticleSummary> =
        synchronized(index) { index.values.filter { it.url !in except && available(it.url) } }
            .sortedWith(compareByDescending(nullsLast()) { it.publishedAt })

    /** Adds those of [headlines] the index doesn't have (an import from the archive); returns how many. */
    fun addHeadlines(headlines: List<ArticleSummary>): Int = synchronized(index) {
        val added = headlines.count { h -> index.putIfAbsent(h.url, h) == null }
        if (added > 0) writeIndex()
        added
    }

    fun loadArticle(url: String): Article? = read(articleFile(url)) { json.decodeFromString(Article.serializer(), it) }

    fun saveArticle(article: Article) = write(articleFile(article.url), json.encodeToString(Article.serializer(), article))

    fun hasArticle(url: String): Boolean = articleFile(url).exists()

    /** The article page's HTML as downloaded, gzipped, so its article can be read again with a newer config. */
    fun savePage(url: String, html: String) {
        val file = pageFile(url)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        GZIPOutputStream(temp.outputStream()).bufferedWriter().use { it.write(html) }
        if (!temp.renameTo(file)) temp.delete()
    }

    fun hasPage(url: String): Boolean = pageFile(url).exists()

    fun loadPage(url: String): String? = pageFile(url).takeIf { it.exists() }
        ?.let { file -> runCatching { GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() } }.getOrNull() }

    /** The cached articles but those whose id ([fileName] without ".json") is in [except], read one at a time. */
    fun articles(except: (String) -> Boolean = { false }): Sequence<Article> = articlesDir.listFiles().orEmpty().asSequence()
        .filter { !except(it.name.removeSuffix(".json")) }
        .mapNotNull { file -> read(file) { json.decodeFromString(Article.serializer(), it) } }

    /**
     * Keeps the articles and their pages within [maxBytes] (0: no limit): the oldest saved go first, but never those
     * of [keep] (the feed's own). The index then drops the headlines whose article can't be opened any more
     * ([available]: on the phone, or in the archive), except [keep]'s, whose articles may be on their way.
     */
    fun trim(maxBytes: Long, keep: Set<String>, available: (String) -> Boolean = ::hasArticle) {
        val kept = keep.mapTo(HashSet()) { fileName(it) }
        if (maxBytes > 0) {
            val articles = articlesDir.listFiles().orEmpty()
            val pageOf = { article: File -> File(pagesDir, article.name + ".gz") }
            var total = articles.sumOf { it.length() + pageOf(it).length() }
            articles.filter { it.name !in kept }.sortedBy { it.lastModified() }.forEach { article ->
                if (total <= maxBytes) return@forEach
                total -= article.length() + pageOf(article).length()
                article.delete()
                pageOf(article).delete()
            }
        }
        synchronized(index) {
            if (index.keys.removeAll { it !in keep && !available(it) }) writeIndex()
        }
    }

    private fun writeIndex() = write(indexFile, index.values.joinToString("") { json.encodeToString(ArticleSummary.serializer(), it) + "\n" })

    val articleCount: Int get() = articlesDir.listFiles().orEmpty().size

    /** The articles' and their pages' size: what the text quota ([trim]) counts. */
    val articleBytes: Long get() = listOf(articlesDir, pagesDir).sumOf { d -> d.listFiles().orEmpty().sumOf { it.length() } }

    val bytes: Long get() = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        dir.deleteRecursively()
        synchronized(index) { index.clear() }
    }

    private fun articleFile(url: String) = File(articlesDir, fileName(url))

    private fun pageFile(url: String) = File(pagesDir, fileName(url) + ".gz")

    private fun <T> read(file: File, parse: (String) -> T): T? =
        if (file.exists()) runCatching { parse(file.readText()) }.getOrNull() else null

    @Synchronized
    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) temp.delete()
    }

    companion object {
        /** 2: articles have `blocks` (in-text photos) and `video`. 3: more sites' in-text photos. */
        private const val VERSION = 3

        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** A file name for [url]: its SHA-256, so any URL fits and the same URL always maps to the same file. */
        fun fileName(url: String): String =
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(16).joinToString("") { "%02x".format(it) } + ".json"
    }
}
