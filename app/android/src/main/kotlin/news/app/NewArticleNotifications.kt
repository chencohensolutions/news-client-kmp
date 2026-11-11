package news.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import news.core.model.ArticleSummary
import java.io.File
import kotlin.concurrent.thread

/**
 * New articles from the background refresh ([BackgroundRefresh]) in the system's notifications: each with its title,
 * its subtitle, its site, and two buttons, mark as read ([MarkReadReceiver]) and open (the reader, at it). Several
 * are grouped under one summary. Or, with [AppSettings.combineNotifications], all in one notification that each refresh
 * updates: its headlines one under another, the newest first, kept in a file until it is opened, dismissed or marked
 * read ([showCombined]).
 */
object NewArticleNotifications {
    private const val CHANNEL = "new_articles"
    private const val GROUP = "news.app.NEW_ARTICLES"
    private const val SUMMARY_ID = 1
    private const val COMBINED_ID = 2

    /** The combined notification holds at most this many headlines, the newest; it lists [COMBINED_LINES] of them. */
    private const val COMBINED_MAX = 30
    private const val COMBINED_LINES = 7
    private const val COMBINED_FILE = "combined_notification.txt"

    /** At most this many articles a refresh: the newest. */
    const val MAX = 5

    const val EXTRA_URL = "news.app.extra.URL"
    const val EXTRA_SOURCE = "news.app.extra.SOURCE"
    const val EXTRA_TITLE = "news.app.extra.TITLE"
    const val EXTRA_URLS = "news.app.extra.URLS"
    const val EXTRA_COMBINED = "news.app.extra.COMBINED"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "כתבות חדשות", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Whether the app may post notifications (asked for on Android 13 and later). */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Posts [items] (newest first), each named by its site in [names]; with [combine], into the one combined
     * notification ([showCombined]), leaving out those in [read]. Nothing without the permission.
     */
    fun show(
        context: Context,
        items: List<ArticleSummary>,
        names: Map<String, String>,
        combine: Boolean = false,
        read: Set<String> = emptySet(),
    ) {
        if (items.isEmpty() || !allowed(context)) return
        ensureChannel(context)
        if (combine) return showCombined(context, items, names, read)
        val manager = NotificationManagerCompat.from(context)
        items.forEach { item ->
            val id = idOf(item.url)
            val open = openIntent(context, item, id)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.title)
                .setContentText(item.subtitle ?: item.summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.subtitle ?: item.summary))
                .setSubText(names[item.sourceId] ?: item.sourceId)
                .setWhen(item.publishedAt?.toEpochMilliseconds() ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "סימון כנקרא", markReadIntent(context, item.url, id))
                .addAction(0, "פתיחה", open)
                .build()
            runCatching { manager.notify(id, notification) }
        }
        if (items.size > 1) {
            val summary = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${items.size} כתבות חדשות")
            // When they were posted, not one article's own time (an old article newly listed).
            .setWhen(System.currentTimeMillis())
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        }
    }

    /**
     * The one notification of all new articles: [items] on top of the headlines it already lists ([combinedLines]) that
     * weren't [read] since, at most [COMBINED_MAX]; titled with how many, the first [COMBINED_LINES] shown one under
     * another as "site: headline". A tap opens the feed; "mark all read" marks them all; either, or dismissing it, empties
     * it for the next.
     */
    private fun showCombined(context: Context, items: List<ArticleSummary>, names: Map<String, String>, read: Set<String>) {
        val fresh = items.map { CombinedLine(it.url, names[it.sourceId] ?: it.sourceId, it.title) }
        val lines = combinedLines(fresh, loadCombined(context), read, COMBINED_MAX)
        saveCombined(context, lines)
        val style = NotificationCompat.InboxStyle()
        lines.take(COMBINED_LINES).forEach { style.addLine("${it.site}: ${it.title}") }
        if (lines.size > COMBINED_LINES) style.setSummaryText("ועוד ${lines.size - COMBINED_LINES}")
        val open = PendingIntent.getActivity(
            context, COMBINED_ID,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_COMBINED, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markAll = PendingIntent.getBroadcast(
            context, COMBINED_ID,
            Intent(context, MarkReadReceiver::class.java).putExtra(EXTRA_URLS, lines.map { it.url }.toTypedArray()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismissed = PendingIntent.getBroadcast(
            context, COMBINED_ID + 1,
            Intent(context, MarkReadReceiver::class.java).putExtra(EXTRA_COMBINED, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (lines.size == 1) "כתבה חדשה" else "${lines.size} כתבות חדשות")
            .setContentText(lines.first().let { "${it.site}: ${it.title}" })
            .setStyle(style)
            .setNumber(lines.size)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .addAction(0, "סימון הכל כנקרא", markAll)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(COMBINED_ID, notification) }
    }

    /** Empties the combined notification's list (it was opened, dismissed or marked read): the next starts afresh. */
    fun clearCombined(context: Context) {
        File(context.filesDir, COMBINED_FILE).delete()
    }

    private fun loadCombined(context: Context): List<CombinedLine> =
        runCatching { File(context.filesDir, COMBINED_FILE).readLines() }.getOrDefault(emptyList()).mapNotNull(CombinedLine::parse)

    private fun saveCombined(context: Context, lines: List<CombinedLine>) {
        runCatching { File(context.filesDir, COMBINED_FILE).writeText(lines.joinToString("\n") { it.line() }) }
    }

    fun cancel(context: Context, url: String) {
        NotificationManagerCompat.from(context).cancel(idOf(url))
    }

    fun cancelCombined(context: Context) {
        NotificationManagerCompat.from(context).cancel(COMBINED_ID)
    }

    /** The notification of [url]: one per article, the same one again for the same article. */
    private fun idOf(url: String) = url.hashCode().let { if (it == SUMMARY_ID || it == COMBINED_ID) it + 2 else it }

    private fun openIntent(context: Context, item: ArticleSummary, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_URL, item.url)
            .putExtra(EXTRA_SOURCE, item.sourceId)
            .putExtra(EXTRA_TITLE, item.title)
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun markReadIntent(context: Context, url: String, id: Int): PendingIntent {
        val intent = Intent(context, MarkReadReceiver::class.java).putExtra(EXTRA_URL, url)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * What a refresh notifies: the headlines of [fresh] that weren't in the [previous] feed and weren't read, newest
 * first, at most [max]. Not one whose story ([stories]: groups of URLs) has a report already read, and one per story,
 * its newest. Nothing on a first run ([previous] null): the whole feed would be new.
 */
internal fun worthNotifying(
    fresh: List<ArticleSummary>,
    previous: Set<String>?,
    read: Set<String>,
    stories: List<List<String>>?,
    max: Int = NewArticleNotifications.MAX,
): List<ArticleSummary> {
    if (previous == null) return emptyList()
    val storyOf = HashMap<String, List<String>>()
    stories.orEmpty().forEach { group -> group.forEach { storyOf[it] = group } }
    val seenStories = HashSet<List<String>>()
    return fresh
        .filter { it.url !in previous && it.url !in read }
        .sortedWith(compareByDescending(nullsFirst()) { it.publishedAt })
        .filter { item ->
            val story = storyOf[item.url] ?: return@filter true
            story.none { it in read } && seenStories.add(story)
        }
        .take(max)
}

/** One headline of the combined notification: its [url], its [site]'s name and its [title]; a line of its file. */
internal data class CombinedLine(val url: String, val site: String, val title: String) {
    fun line(): String = listOf(url, site, title).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    companion object {
        fun parse(line: String): CombinedLine? = line.split('\t').takeIf { it.size == 3 }?.let { (u, s, t) -> CombinedLine(u, s, t) }
    }
}

/**
 * What the combined notification lists after a refresh: the [fresh] headlines on top of those it listed ([before]),
 * without the ones [read] since or twice, at most [max], the newest kept.
 */
internal fun combinedLines(fresh: List<CombinedLine>, before: List<CombinedLine>, read: Set<String>, max: Int): List<CombinedLine> =
    (fresh + before).distinctBy { it.url }.filter { it.url !in read }.take(max)

/**
 * "Mark as read" on a notification: the article joins the read history ([ReadHistory]) and its notification goes. On
 * the combined notification, every article it lists ([NewArticleNotifications.EXTRA_URLS]); its dismissal
 * ([NewArticleNotifications.EXTRA_COMBINED]) only empties its list.
 */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(NewArticleNotifications.EXTRA_COMBINED, false)) {
            NewArticleNotifications.clearCombined(context)
            return
        }
        val all = intent.getStringArrayExtra(NewArticleNotifications.EXTRA_URLS)
        val urls = all?.toList() ?: listOfNotNull(intent.getStringExtra(NewArticleNotifications.EXTRA_URL))
        if (urls.isEmpty()) return
        if (all != null) {
            NewArticleNotifications.cancelCombined(context)
            NewArticleNotifications.clearCombined(context)
        } else {
            NewArticleNotifications.cancel(context, urls.single())
        }
        val pending = goAsync()
        thread {
            runCatching { ReadHistory(File(context.filesDir, ReadHistory.FILE)).addAll(urls) }
            pending.finish()
        }
    }
}
