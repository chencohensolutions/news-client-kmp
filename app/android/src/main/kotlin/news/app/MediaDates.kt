package news.app

import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleSummary
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The date each cached picture and video counts by when its cache is over its quota ([NewsApplication.trimMedia]):
 * its article's publish time, or, for an article without one, when it was cached. The media of the oldest articles go
 * first, however recently they were seen.
 *
 * What the app learns of articles ([noteArticle], [noteHeadlines]) is held in memory; a picture's date is written to
 * [file] once the picture is in the cache ([cached]), so it outlives a restart. A video's is written as it plays
 * ([noteVideo]), by its stream's folder: an HLS stream's segments are cached each under its own URL, next to its playlist.
 */
class MediaDates(private val file: File) {

    /** Article publish times by picture URL, as articles and headlines pass through the app. In memory. */
    private val articleTimes = ConcurrentHashMap<String, Long>()

    /** The dates of cached media, by picture URL or video folder, from [file]. */
    private val dates: MutableMap<String, Long> by lazy {
        val read = ConcurrentHashMap<String, Long>()
        runCatching { file.takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty().forEach { line ->
            val tab = line.lastIndexOf('\t')
            line.substring(tab + 1).toLongOrNull()?.takeIf { tab > 0 }?.let { read[line.substring(0, tab)] = it }
        }
        read
    }

    fun noteArticle(article: Article) {
        val time = article.publishedAt?.toEpochMilliseconds() ?: return
        article.imageUrl?.let { articleTimes.putIfAbsent(it, time) }
        article.blocks.filterIsInstance<ArticleBlock.Image>().forEach { articleTimes.putIfAbsent(it.url, time) }
    }

    fun noteHeadlines(headlines: List<ArticleSummary>) = headlines.forEach { h ->
        val url = h.imageUrl ?: return@forEach
        h.publishedAt?.toEpochMilliseconds()?.let { articleTimes.putIfAbsent(url, it) }
    }

    /** The picture at [url] is in the cache: its date is kept, its article's or now. */
    fun cached(url: String, now: Long = System.currentTimeMillis()) {
        if (url in dates) return
        write(url, articleTimes[url] ?: now)
    }

    /** A video of an article published at [publishedAt] (null: none) plays from [stream]. */
    fun noteVideo(stream: String, publishedAt: Long?, now: Long = System.currentTimeMillis()) {
        val folder = folderOf(stream)
        if (folder !in dates) write(folder, publishedAt ?: now)
    }

    /** The pictures with a date, oldest first: those the picture cache's trim goes through. */
    fun picturesOldestFirst(): List<String> = dates.entries.filter { !it.key.endsWith("/") && !it.key.startsWith("content:") }.sortedBy { it.value }.map { it.key }

    /** [key]'s date as a video cache key: its stream folder's; one never noted counts from now, and is noted so. */
    fun videoDate(key: String, now: Long = System.currentTimeMillis()): Long =
        dates[key] ?: dates[folderOf(key)] ?: now.also { write(folderOf(key), it) }

    /** The pictures at [urls] left the cache. */
    fun removed(urls: Collection<String>) {
        if (urls.count { dates.remove(it) != null } > 0) rewrite()
    }

    @Synchronized
    private fun write(key: String, time: Long) {
        dates[key] = time
        runCatching { file.parentFile?.mkdirs(); file.appendText("$key\t$time\n") }
    }

    @Synchronized
    private fun rewrite() {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(dates.entries.joinToString("") { "${it.key}\t${it.value}\n" })
            if (!temp.renameTo(file)) temp.delete()
        }
    }

    companion object {
        const val FILE = "media_dates.tsv"

        /**
         * A stream's folder: its URL up to its last "/" (an HLS playlist's segments are under it). A file of the
         * archive (content://) is its own: the archive's files share their provider's path.
         */
        fun folderOf(url: String): String =
            if (url.startsWith("content:")) url else url.substringBefore('?').substringBeforeLast('/') + "/"
    }
}
