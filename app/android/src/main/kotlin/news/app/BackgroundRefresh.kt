package news.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import news.core.model.ArticleSummary

/**
 * Refreshing the feed in the background every [AppSettings.backgroundMinutes]. Each run queues the next one, which
 * allows intervals under WorkManager's 15-minute periodic minimum; Android still defers runs while the phone is
 * idle (Doze), so the interval is a target, not a guarantee. Runs need a network and skip a low battery. Each run
 * asks the news server only.
 */
object BackgroundRefresh {
    private const val WORK = "background-refresh"

    /** What a run spends at most: Android stops a background job after about ten minutes. */
    internal const val BUDGET_MS = 7 * 60_000L

    /** How long a run waits for the new headlines' articles before asking for the stories. */
    internal const val NEW_ARTICLES_MS = 90_000L

    /**
     * Starts the schedule, the next run one interval away; off cancels it. [restart] (the interval changed) replaces a
     * queued or running run; otherwise (the app opening) one already there is kept, so opening the app never stops a
     * refresh halfway through its article downloads.
     */
    fun schedule(context: Context, minutes: Int, restart: Boolean = false) {
        val work = WorkManager.getInstance(context)
        when {
            minutes <= 0 -> work.cancelUniqueWork(WORK)
            else -> work.enqueueUniqueWork(WORK, if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request(minutes))
        }
    }

    /** Queued by a running refresh: starts when it finishes. */
    internal fun scheduleNext(context: Context, minutes: Int) {
        if (minutes > 0) WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request(minutes))
    }

    private fun request(minutes: Int) = OneTimeWorkRequestBuilder<BackgroundRefreshWorker>()
        .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
        .build()
}

class BackgroundRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NewsApplication
        val settings = app.settingsStore.load()
        if (settings.backgroundMinutes <= 0) return Result.success()
        val log = BackgroundLog(app)
        val start = Probe.read(app)
        var headlines = 0
        var articles = 0
        var storiesMs = 0L
        var notified = 0
        // Android stops a background job after about ten minutes, and a stopped run saved nothing: so the run does the
        // important part first and saves it, then the rest while time lasts, each step within [BUDGET_MS].
        val deadline = SystemClock.elapsedRealtime() + BackgroundRefresh.BUDGET_MS
        fun left(): Long = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val error = runCatching {
            // The feed as it was before this run: fetching saves the new one in its place.
            val previous = app.feedCache.loadHeadlines()?.items
            val fetched = app.repository.latest(skip = settings.hiddenSources)
            headlines = fetched.items.size
            val shown = { items: List<ArticleSummary> -> items.filter { it.sourceId !in settings.hiddenSources } }
            // The server's stories over [items]; null when it doesn't group them or doesn't answer.
            suspend fun group(items: List<ArticleSummary>): List<List<String>>? {
                if (left() <= 0) return null
                val began = SystemClock.elapsedRealtime()
                return withTimeoutOrNull(left()) { runCatching { app.server.stories(shown(items)) }.getOrNull() }
                    ?.map { s -> s.reports.map { it.url } }.also { storiesMs += SystemClock.elapsedRealtime() - began }
            }
            fun save(items: List<ArticleSummary>, stories: List<List<String>>?) = app.feedCache.saveHeadlines(
                fetched.copy(items = items, stories = stories ?: fetched.stories),
            )
            // 1. The headlines not yet considered for notifying get their articles first, then the stories are asked
            // for; only a run with stories notifies (not those read, nor of a story already read) and marks them
            // considered. Otherwise the next run takes them up again.
            val considered = NotifiedFeed(app)
            val before = considered.load() ?: previous?.mapTo(HashSet()) { it.url }
            val merged = withFilledFrom(fetched.items, previous)
            val fresh = shown(merged).filter { before != null && it.url !in before }
            val withArticles = withArticles(app, fresh, minOf(left(), BackgroundRefresh.NEW_ARTICLES_MS))
            val listed = merged.map { withArticles[it.url] ?: it }
            val firstStories = group(listed) ?: if (app.server.storiesOn) null else emptyList()
            if (before == null) {
                // A first run: what is listed now is the baseline; nothing is new yet.
                considered.save(listed.map { it.url })
            } else if (firstStories != null) {
                val read = ReadHistory(File(app.filesDir, ReadHistory.FILE)).all()
                // Only the sites and categories settings ask for (none picked: all).
                // Only those with their article on the phone: the feed shows no other, so a notification would
                // open on nothing.
                val wanted = shown(listed).filter { item ->
                    app.repository.hasArticle(item.url) &&
                    (settings.notifySources.isEmpty() || item.sourceId in settings.notifySources) &&
                        (settings.notifyCategories.isEmpty() || (item.category ?: OTHER_CATEGORY) in settings.notifyCategories)
                }
                val notify = worthNotifying(wanted, before, read, firstStories)
                NewArticleNotifications.show(
                    app, notify, app.repository.sources.associate { it.id to it.name },
                    combine = settings.combineNotifications, read = read,
                )
                notified = notify.size
                // Those still without their article are taken up again by a later run, once it is in.
                considered.save(listed.filter { it.url !in fresh.mapTo(HashSet()) { f -> f.url } || app.repository.hasArticle(it.url) }.map { it.url })
            }
            save(listed, firstStories?.takeIf { it.isNotEmpty() })
            // 2. The rest of the feed's articles (and the pictures and times they fill), while time lasts.
            val fills = java.util.concurrent.ConcurrentHashMap<String, ArticleSummary>()
            val filled = withTimeoutOrNull(left()) {
                app.articleFetcher.run(listed, onFilled = { fills[it.url] = it }, onDownloaded = { articles++ })
            } ?: listed.map { fills[it.url] ?: it }
            if (filled != listed) save(filled, firstStories?.takeIf { it.isNotEmpty() })
        }.exceptionOrNull()
        runCatching { app.trimMedia() }
        log.add(Probe.read(app).runSince(start, headlines, articles, storiesMs, error?.message, notified))
        BackgroundRefresh.scheduleNext(app, settings.backgroundMinutes)
        return Result.success()
    }
}

/**
 * The articles of [items] (headlines new to the notifications), a few at once, within [within]: each headline as its
 * article has it ([filledFrom]: its title, subtitle, picture, time, category, video). Those not in by then are left out.
 */
private suspend fun withArticles(app: NewsApplication, items: List<ArticleSummary>, within: Long): Map<String, ArticleSummary> {
    if (items.isEmpty() || within <= 0) return emptyMap()
    val filled = java.util.concurrent.ConcurrentHashMap<String, ArticleSummary>()
    withTimeoutOrNull(within) {
        perSite(items) { item ->
            val article = runCatching { app.repository.article(item.sourceId, item.url) }.getOrNull() ?: return@perSite
            filled[item.url] = item.filledFrom(article)
        }
    }
    return HashMap(filled)
}

/**
 * The headlines the notifications have considered, kept in the app's storage (one URL per line): a headline not among
 * them is new to the notifications. Null before the first run.
 */
private class NotifiedFeed(context: Context) {
    private val file = File(context.filesDir, "notified.txt")

    fun load(): Set<String>? = if (file.exists()) file.readLines().filterTo(HashSet()) { it.isNotBlank() } else null

    fun save(urls: Collection<String>) {
        val temp = File(file.path + ".tmp")
        temp.writeText(urls.joinToString("") { it + "\n" })
        if (!temp.renameTo(file)) temp.delete()
    }
}

/** One background refresh, for measuring what the app costs: how long it ran, its CPU time, data and the battery. */
@Serializable
data class BackgroundRun(
    val startedAt: Long,
    val durationMs: Long,
    val cpuMs: Long,
    val bytesReceived: Long,
    val batteryPercent: Int,
    val charging: Boolean,
    val headlines: Int,
    val articlesDownloaded: Int,
    val error: String? = null,
    /** Time spent asking the server for the stories. */
    val storiesMs: Long = 0,
    /** New articles shown in the system's notifications. */
    val notified: Int = 0,
)

/** The process's counters at one moment; two of them make a [BackgroundRun]. */
private class Probe(val wallMs: Long, val elapsedMs: Long, val cpuMs: Long, val rxBytes: Long, val battery: Int, val charging: Boolean) {
    fun runSince(start: Probe, headlines: Int, articles: Int, storiesMs: Long, error: String?, notified: Int = 0) = BackgroundRun(
        startedAt = start.wallMs,
        durationMs = elapsedMs - start.elapsedMs,
        cpuMs = cpuMs - start.cpuMs,
        bytesReceived = (rxBytes - start.rxBytes).coerceAtLeast(0),
        batteryPercent = battery,
        charging = charging,
        headlines = headlines,
        articlesDownloaded = articles,
        error = error,
        storiesMs = storiesMs,
        notified = notified,
    )

    companion object {
        fun read(context: Context): Probe {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return Probe(
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                Process.getElapsedCpuTime(),
                TrafficStats.getUidRxBytes(Process.myUid()),
                battery,
                plugged,
            )
        }
    }
}

/** The last [MAX] background runs, newest first, in the app's private storage. */
class BackgroundLog(context: Context) {
    private val file = File(context.filesDir, "background-runs.json")

    @Synchronized
    fun runs(): List<BackgroundRun> =
        runCatching { json.decodeFromString(ListSerializer(BackgroundRun.serializer()), file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun add(run: BackgroundRun) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(ListSerializer(BackgroundRun.serializer()), (listOf(run) + runs()).take(MAX)))
        temp.renameTo(file)
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val MAX = 500
        val json = Json { ignoreUnknownKeys = true }
    }
}
