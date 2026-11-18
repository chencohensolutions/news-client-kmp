package news.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import news.app.server.EnrollmentLink
import news.app.server.Footnote
import news.app.server.StorySummary
import news.app.server.ServerGrouping
import news.app.server.ServerSource
import news.app.server.ServerStats
import news.app.server.findEnrollmentLink
import news.app.stories.Story
import news.app.stories.StoryText
import news.app.stories.TopicMatch
import news.app.stories.wordScores
import news.app.stories.topicMatches
import news.app.stories.latestFirst
import news.app.stories.newestFirst
import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleSummary
import news.core.model.Categories

data class HeadlinesState(
    val items: List<ArticleSummary> = emptyList(),
    /**
     * The feed's older part: headlines it showed before whose articles are still on the phone ([FeedCache.older]),
     * newest first, as many as scrolling down has asked for ([NewsViewModel.showMoreOlder]). Shown and grouped with
     * [items] ([allItems]); never downloaded, saved with the feed or new.
     */
    val older: List<ArticleSummary> = emptyList(),
    val failures: Map<String, String> = emptyMap(),
    /** The news server's generated articles, by any of their stories' report URLs ([news.app.server.NewsServer.writing]). */
    val summaries: Map<String, StorySummary> = emptyMap(),
    /** The report URLs of every generated story opened (its card, article page or a source): see [unreadGenerated]. */
    val storiesOpened: Set<String> = emptySet(),
    /** The feed's switch: the stories' original headlines instead of their generated articles. */
    val showOriginal: Boolean = false,
    /** Stories (by lead URL) flipped on their own card to the other view than [showOriginal]; cleared when it changes. */
    val flipped: Set<String> = emptySet(),
    /** The feed shows only stories with a generated article. */
    val onlyGenerated: Boolean = true,
    /** The news comes from the news server ([news.app.server.NewsServer.inUse]) and it doesn't answer: the feed is its last. */
    val serverDown: Boolean = false,
    /** The phone is enrolled with the server, whether or not it answers now. */
    val serverOn: Boolean = false,
    val refreshing: Boolean = false,
    /**
     * The saved feed is still being read at launch: the feed shows a fetching line rather than "no headlines" until
     * it is in (or there was none).
     */
    val starting: Boolean = true,
    /** Source ids the server doesn't read: their earlier headlines hidden. */
    val hiddenSources: Set<String> = emptySet(),
    /** The one view the app has now, "הכל"; the others' code paths remain for the tests and the reader's order. */
    val view: HeadlinesView = HeadlinesView.ALL,
    /** The sites shown (from the bottom bar's drawer), or every site that is on when empty. For this session; not a setting. */
    val sites: Set<String> = emptySet(),
    /**
     * The categories shown (from the header's drawer; [OTHER_CATEGORY] for headlines with none), or all when empty.
     * For this session.
     */
    val categories: Set<String> = emptySet(),
    /** Only what is new ([onlyNewUrls]); the bottom bar's toggle. For this session. */
    val onlyNew: Boolean = false,
    /**
     * What [onlyNew] shows: the new headlines when it was switched on, and those that arrived new since. Rows viewed
     * meanwhile keep their place in it, so the list doesn't change under the owner while it is on.
     */
    val onlyNewUrls: Set<String> = emptySet(),
    val stories: StoriesState = StoriesState.Idle,
    /** URLs of the articles the user has opened ([ReadHistory]). */
    val read: Set<String> = emptySet(),
    /** When the feed on screen was fetched, by the app or a background run; null before the first fetch. */
    val refreshedAt: Instant? = null,
    /**
     * The feed's headlines neither viewed ([NewsViewModel.scrolledPast]) nor read, as of the last refresh (or start);
     * marked and counted on screen. The feed's older part is never new.
     */
    val newUrls: Set<String> = emptySet(),
    /** New headlines viewed since ([NewsViewModel.scrolledPast]): no longer marked new, though still ordered as new. */
    val pastUrls: Set<String> = emptySet(),
    /**
     * The topic search, while a topic is on: each headline close enough to a topic that is on, by url ([topicMatches]);
     * null when no topic is on. It picks the rows of [shownAll], it doesn't change them.
     */
    val topicMatches: Map<String, TopicMatch>? = null,
) {
    /** [items], then those of [older] that aren't among them. */
    val allItems: List<ArticleSummary> by lazy {
        if (older.isEmpty()) items
        else items.mapTo(HashSet()) { it.url }.let { live -> items + older.filter { it.url !in live } }
    }

    /** The headlines of the sites that are on: what stories are grouped from, whatever the filters show. */
    val visibleItems: List<ArticleSummary> get() = allItems.filter { it.sourceId !in hiddenSources }

    /** Whether [item] passes the filters: its site and its category are chosen, and it is new when [onlyNew]. */
    private fun shows(item: ArticleSummary): Boolean =
        (sites.isEmpty() || item.sourceId in sites) &&
            (categories.isEmpty() || (item.category ?: OTHER_CATEGORY) in categories) &&
            (!onlyNew || item.url in onlyNewUrls)

    /** Errors from the sites that are switched on. */
    val visibleFailures: Map<String, String> get() = failures.filterKeys { it !in hiddenSources }

    /** What is marked new: [newUrls] but those scrolled past ([pastUrls]). */
    val markedNew: Set<String> by lazy { if (pastUrls.isEmpty()) newUrls else newUrls - pastUrls }

    /** How many of the headlines the filters show are marked new, for the line under the title; with a topic on, in its rows. */
    val visibleNewCount: Int
        get() {
            val picked = pickedUrls
            return visibleItems.count { it.url in markedNew && shows(it) && (picked == null || it.url in picked) }
        }

    /** With a topic on, the headlines of the rows it picked ([shownAll]); null when no topic is on. */
    val pickedUrls: Set<String>? by lazy { topicMatches?.let { shownAll.flatMapTo(HashSet()) { s -> s.reports.map { it.url } } } }

    /**
     * The stories found, with their reports refreshed from the feed (pictures and times may have been filled in
     * since) and ordered again; empty until they are [StoriesState.Ready].
     */
    val visibleStories: List<Story> by lazy {
        val ready = stories as? StoriesState.Ready ?: return@lazy emptyList()
        val current = allItems.associateBy { it.url }
        newestFirst(ready.stories.map { s -> Story(s.reports.map { current[it.url] ?: it }) }, allItems)
    }

    // What the screen shows: what the filters let through ([shows]): a chosen site, a chosen category, new when
    // [onlyNew]. They filter what is shown, not what stories are grouped from, so a story keeps all its sites.

    /**
     * [visibleStories] as shown: those with a report the filters let through, led by the newest such report (a story
     * from other sites too is led by one of the chosen), every other report after it as before.
     */
    val shownStories: List<Story> by lazy {
        visibleStories.mapNotNull { story ->
            val lead = story.reports.firstOrNull(::shows) ?: return@mapNotNull null
            if (lead == story.lead) story else Story(listOf(lead) + (story.reports - lead))
        }
    }

    /** [visibleItems] as shown ([shows]), newest first (those without a time after them, in the feed's order). */
    val shownItems: List<ArticleSummary> by lazy {
        visibleItems.filter(::shows).sortedWith(compareByDescending(nullsFirst()) { it.publishedAt })
    }

    /**
     * Everything, story by story: [shownStories] (their leads as chosen), and each of [shownItems] that is in no story
     * as one of its own, newest first by the reports shown: one site's list goes by that site's times, not by a newer
     * report of the same story from another site. Before the stories are ready, every headline is its own. A headline
     * is in one row only.
     *
     * With a topic on ([topicMatches]), only the rows with a report close to it, each whole and in its place: a story
     * keeps its lead and every report, matching or not, and a match inside a story is not shown again on its own.
     */
    val shownAll: List<Story> by lazy {
        val grouped = visibleStories.flatMapTo(HashSet()) { s -> s.reports.map { it.url } }
        val all = latestFirst(shownStories + shownItems.filter { it.url !in grouped }.map { Story(listOf(it)) }, allItems, ::shows)
        val matches = topicMatches ?: return@lazy all
        all.filter { story -> story.reports.any { it.url in matches } }
    }

    /** [shownItems] grouped in [Categories.IDS] order, keeping their order inside a group; uncategorized last. */
    val shownByCategory: List<Pair<String?, List<ArticleSummary>>> by lazy {
        shownItems.groupBy { it.category }.toList()
            .sortedBy { (id, _) -> Categories.IDS.indexOf(id).takeIf { it >= 0 } ?: Categories.IDS.size }
    }

    /**
     * What swiping in the reader goes through, in the order the current view shows it: each page is one story's
     * reports in Stories, and one headline in the other views (and in Stories before the stories are ready).
     */
    val readingOrder: List<List<ArticleSummary>>
        get() = when (view) {
            HeadlinesView.LATEST -> shownItems.map(::listOf)
            HeadlinesView.CATEGORIES -> shownByCategory.flatMap { it.second }.map(::listOf)
            HeadlinesView.STORIES -> shownStories.map { it.reports }.ifEmpty { shownItems.map(::listOf) }
            HeadlinesView.ALL -> shownAll.map { it.reports }
        }
}

/**
 * The new headlines of [current]: neither viewed ([NewsViewModel.scrolledPast]) nor read, and published at [since] or
 * after ([AppSettings.newWithinHours]; null: no limit); one without a publish time by when the feed first had it
 * ([firstSeen]).
 */
fun newUrlsOf(
    current: List<ArticleSummary>,
    viewed: Set<String>,
    read: Set<String>,
    since: Long? = null,
    firstSeen: Map<String, Long> = emptyMap(),
): Set<String> = current.mapNotNullTo(HashSet()) { item ->
    val time = item.publishedAt?.toEpochMilliseconds() ?: firstSeen[item.url]
    item.url.takeIf { it !in viewed && it !in read && (since == null || time == null || time >= since) }
}

/**
 * [fresh] headlines (a new refresh) with what [previous] had filled in from their articles for the same URLs
 * ([filledFrom]): the article's title, subtitle, main picture, category and time in place of the headline's, which
 * often lacks the category or the time. Without it those headlines would lose them at every refresh until their
 * articles are read again.
 */
fun withFilledFrom(fresh: List<ArticleSummary>, previous: List<ArticleSummary>?): List<ArticleSummary> {
    val before = previous.orEmpty().associateBy { it.url }
    if (before.isEmpty()) return fresh
    return fresh.map { item ->
        val old = before[item.url] ?: return@map item
        item.copy(
            title = old.title,
            subtitle = item.subtitle ?: old.subtitle,
            // Filled from the article: its time is the real one (the list's is often missing or the section's).
            publishedAt = old.publishedAt ?: item.publishedAt,
            imageUrl = old.imageUrl ?: item.imageUrl,
            category = old.category ?: item.category,
            video = item.video || old.video,
        )
    }
}

/**
 * The reader: [pages] to swipe through (see [HeadlinesState.readingOrder]), as they were when it opened, starting
 * at report [startReport] of page [startPage].
 */
data class ReaderState(val pages: List<List<ArticleSummary>>, val startPage: Int, val startReport: Int)

/** The feed's views. [ALL]: the stories and, among them, every headline that is in none, as a story of its own. */
enum class HeadlinesView { LATEST, CATEGORIES, STORIES, ALL }

/** Whether the view shows stories, so they are grouped for it. */
val HeadlinesView.groupsStories: Boolean get() = this == HeadlinesView.STORIES || this == HeadlinesView.ALL

/**
 * What the headline screen draws, computed from a [HeadlinesState] off the UI thread ([NewsViewModel.feed]): filtering
 * and ordering 3,500 headlines and their stories on it made scrolling stutter.
 */
data class FeedView(
    val items: List<ArticleSummary> = emptyList(),
    val stories: List<Story> = emptyList(),
    val byCategory: List<Pair<String?, List<ArticleSummary>>> = emptyList(),
    val newCount: Int = 0,
    val readingOrder: List<List<ArticleSummary>> = emptyList(),
    /** How many of [items] are new, and the URL of the first: in "אחרונות" the new ones come first. */
    val freshCount: Int = 0,
    val firstFresh: String? = null,
    /** [HeadlinesState.shownAll], for [HeadlinesView.ALL] only (empty in the other views). */
    val all: List<Story> = emptyList(),
    /** Which headlines show as new ([HeadlinesState.markedNew]), carried here so the list reads it off the UI thread. */
    val markedNew: Set<String> = emptySet(),
    /** [HeadlinesState.topicMatches], for the topic under each story's title. */
    val topicMatches: Map<String, TopicMatch>? = null,
) {
    companion object {
        fun of(s: HeadlinesState): FeedView {
            val items = s.shownItems
            return FeedView(
                items, s.shownStories, s.shownByCategory, s.visibleNewCount, s.readingOrder,
                freshCount = items.count { it.url in s.newUrls }, firstFresh = items.firstOrNull { it.url in s.newUrls }?.url,
                all = if (s.view == HeadlinesView.ALL) s.shownAll else emptyList(),
                markedNew = s.markedNew,
                topicMatches = s.topicMatches,
            )
        }
    }
}

/**
 * The scroll-independent part of the feed: the ordered, grouped, filtered lists, and which of the shown headlines are
 * new ([newShown]). It is recomputed only when an input that affects ordering changes ([FeedKey]), off the UI thread.
 * [withPast] then overlays what scrolling past a new headline changes (the badges and the count), which is cheap.
 */
private class Ordered(val base: FeedView, val newUrls: Set<String>, val newShown: List<String>) {
    fun withPast(pastUrls: Set<String>): FeedView =
        if (pastUrls.isEmpty()) base.copy(markedNew = newUrls, newCount = newShown.size)
        else base.copy(markedNew = newUrls - pastUrls, newCount = newShown.count { it !in pastUrls })
}

private fun orderedFeed(s: HeadlinesState): Ordered {
    val items = s.shownItems
    // With a topic on, what is new counts in the rows it picked only.
    val picked = s.pickedUrls
    val newShown = items.mapNotNull { it.url.takeIf { u -> u in s.newUrls && (picked == null || u in picked) } }
    val base = FeedView(
        items = items, stories = s.shownStories, byCategory = s.shownByCategory, readingOrder = s.readingOrder,
        freshCount = newShown.size, firstFresh = newShown.firstOrNull(),
        all = if (s.view == HeadlinesView.ALL) s.shownAll else emptyList(),
        topicMatches = s.topicMatches,
    )
    return Ordered(base, s.newUrls, newShown)
}

/**
 * The parts of [HeadlinesState] the ordering depends on. Scrolling past a new headline changes only [HeadlinesState.pastUrls],
 * which is not here, so the ordered feed is not recomputed as the list scrolls.
 */
private data class FeedKey(
    val items: List<ArticleSummary>,
    val older: List<ArticleSummary>,
    val hiddenSources: Set<String>,
    val stories: StoriesState,
    val sites: Set<String>,
    val categories: Set<String>,
    val onlyNew: Boolean,
    val onlyNewUrls: Set<String>,
    val view: HeadlinesView,
    val newUrls: Set<String>,
    val topicMatches: Map<String, TopicMatch>?,
) {
    fun ordered(): Ordered = orderedFeed(
        HeadlinesState(
            items = items, older = older, hiddenSources = hiddenSources, stories = stories, sites = sites,
            categories = categories, onlyNew = onlyNew, onlyNewUrls = onlyNewUrls, view = view, newUrls = newUrls, topicMatches = topicMatches,
        ),
    )
}

/**
 * Whether opening the app, or coming back to it, fetches the headlines: always without a feed on the phone
 * ([refreshedAt] null); else when the feed is older than [minutes] ([AppSettings.refreshOnReturnMinutes]; 0, off:
 * never). [refreshedAt] is when the feed was fetched, kept with it on the phone: by the app, or by a background refresh
 * when the app starts on the feed that one saved.
 */
internal fun refreshesOnOpen(refreshedAt: Instant?, minutes: Int, now: Instant): Boolean =
    refreshedAt == null || (minutes > 0 && now - refreshedAt > minutes.minutes)

/** The category chosen in the header's drawer for headlines that have none ("אחר"). */
const val OTHER_CATEGORY = "other"

/** The visible headlines grouped into stories, by the news server. */
sealed interface StoriesState {
    data object Idle : StoriesState
    /** Grouping, with nothing earlier to show meanwhile. */
    data object Working : StoriesState
    /** [forItems] and [threshold] are what the stories were computed from; [refreshing]: a newer run is under way. */
    data class Ready(
        val stories: List<Story>,
        val forItems: List<ArticleSummary>,
        val threshold: Float,
        val refreshing: Boolean = false,
        val text: StoryText = StoryText.SUBTITLE,
    ) : StoriesState
    data class Failed(val message: String) : StoriesState
}

/**
 * Bringing the feed's articles from the server ([ArticleFetcher]): how many of the feed's are already on the phone, and
 * how many of those were before this run started ([before]), so this run's progress counts only the new ones.
 */
data class ArticleDownloads(
    val done: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
    val before: Int = 0,
) {
    /** This run's articles: the ones that weren't on the phone when it started. */
    val toFetch: Int get() = total - before
    val fetched: Int get() = done - before
}

/** An archive import ([NewsViewModel.importArchive]): articles [read] of [total], headlines [added] to the older part. */
data class ArchiveImport(val read: Int, val total: Int, val added: Int, val running: Boolean)

/** What the app keeps on the phone, and in the archive. A null [archiveBytes] is still being counted. */
data class StorageInfo(
    val feedBytes: Long = 0,
    val cachedArticles: Int = 0,
    val imageBytes: Long = 0,
    val archiveFolder: String? = null,
    val archiveAvailable: Boolean = false,
    val archivedArticles: Int = 0,
    val archiveBytes: Long? = null,
)

sealed interface ArticleState {
    val summary: ArticleSummary

    data class Loading(override val summary: ArticleSummary) : ArticleState
    data class Loaded(override val summary: ArticleSummary, val article: Article) : ArticleState
    data class Failed(override val summary: ArticleSummary, val message: String) : ArticleState
}

/**
 * The connection to the news server (news-server-go) as Settings shows it ([ServerScreen]), from
 * [news.app.server.NewsServer]. From the UI lab's server round, option a (docs/ux-history/server/).
 */
sealed interface ServerState {
    /** No certificate: the app shows only the connect screen. [error] is why the last try failed. */
    data class NotConnected(
        val address: String = "",
        val code: String = "",
        val error: ServerError? = null,
        /** What failed on the phone, for [ServerError.SAVE_FAILED]. */
        val detail: String? = null,
    ) : ServerState

    /** enrollmentPreview, the key in the Keystore, enrollDevice, then serverInfo. [user] once the code is checked. */
    data class Connecting(val address: String, val code: String, val user: String? = null) : ServerState

    /**
     * The code was typed, so the phone has no key to check the server against: [key] is the one the server showed
     * ([news.app.server.shortKey]), for the owner to compare with the "Key" `enroll-code` printed before anything is
     * made.
     */
    data class ConfirmKey(val address: String, val code: String, val user: String, val key: String) : ServerState

    data class Connected(
        val address: String,
        val user: String,
        val deviceName: String,
        /** serverInfo.version. */
        val version: String,
        /** The server groups stories; otherwise each headline is a story of its own. */
        val storiesOn: Boolean,
        /** When the server last read the sites: its newest sighting of a headline. */
        val lastRead: Instant?,
        /** How many addresses the phone keeps for the server, [address] among them. */
        val addressCount: Int = 1,
        /** When the DHT last found the server at a new address; null if it never had to. */
        val dhtFoundAt: Instant? = null,
        val certificateExpires: Instant,
        /** Set while the server doesn't answer; the feed stays as it was meanwhile. */
        val unreachableSince: Instant? = null,
        /** The user is the server's admin (`me`): they change its settings and manage its people. */
        val admin: Boolean = false,
    ) : ServerState
}

/** The admin page's state: the users, an invite just made (its QR shown), whether a call runs, and its error. */
data class AdminState(
    val users: List<news.app.server.ServerUser>? = null,
    val invite: news.app.server.Invite? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** An admin call's failure as the page says it. */
internal fun adminError(e: Throwable): String = when ((e as? news.app.server.ServerException)?.code) {
    news.app.server.NewsServer.FORBIDDEN -> "רק מנהל יכול לעשות את זה"
    "BAD_REQUEST" -> e.message ?: "השרת לא קיבל את השינוי"
    null -> if (e is news.app.server.ServerException) e.message ?: "השרת לא קיבל את השינוי" else "השרת לא עונה"
    else -> e.message ?: "השרת לא קיבל את השינוי"
}

/**
 * The sites left out of the feed, from the server's list: those it doesn't read, those this user hid there, and
 * those the phone hid that the server doesn't know (kept for when it does).
 */
internal fun feedHidden(local: Set<String>, server: List<ServerSource>): Set<String> {
    val known = server.mapTo(HashSet()) { it.id }
    return local.filterTo(HashSet()) { it !in known } + server.filter { !it.enabled || it.hidden }.map { it.id }
}

enum class ServerError {
    /** This phone already sent the code to enrollDevice: the server took it, so it doesn't work again. */
    CODE_USED,

    /** The server enrolled the phone, but keeping its certificate on the phone failed ([ServerState.NotConnected.detail]). */
    SAVE_FAILED,

    /** EnrollmentRejected INVALID_CODE: wrong, expired or already used. */
    CODE_EXPIRED,

    /** Too many wrong codes from this address: wait a minute. */
    RATE_LIMITED,

    /** No answer at the address, or not a news server. */
    UNREACHABLE,

    /** Not an address ([news.app.server.serverUrl]), or none: the QR code had none and none was typed. */
    BAD_ADDRESS,

    /** The server's key isn't the one in the QR code. */
    WRONG_KEY,

    /** Anything else: the Keystore, a malformed request. */
    FAILED,
}

class NewsViewModel(app: Application) : AndroidViewModel(app) {

    private val newsApp = app as NewsApplication
    val repository = newsApp.repository
    private val settingsStore = newsApp.settingsStore
    private val fetcher = newsApp.articleFetcher
    private val backgroundLog = BackgroundLog(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings

    private val _headlines = MutableStateFlow(
        settingsStore.loadFilters().let { (sites, categories) ->
            HeadlinesState(hiddenSources = _settings.value.hiddenSources, sites = sites, categories = categories, onlyGenerated = settingsStore.onlyGenerated)
        },
    )
    val headlines: StateFlow<HeadlinesState> = _headlines

    /**
     * The lists the headline screen draws, computed on a background thread. The ordering (the heavy part) is recomputed
     * only when an ordering input changes ([FeedKey]); scrolling past new headlines, which happens constantly as the
     * list moves, changes only [HeadlinesState.pastUrls] and takes just the cheap badge overlay ([Ordered.withPast]).
     */
    val feed: StateFlow<FeedView> = combine(
        _headlines
            .map { s -> FeedKey(s.items, s.older, s.hiddenSources, s.stories, s.sites, s.categories, s.onlyNew, s.onlyNewUrls, s.view, s.newUrls, s.topicMatches) }
            .distinctUntilChanged()
            .map { it.ordered() },
        _headlines.map { it.pastUrls }.distinctUntilChanged(),
    ) { ordered, pastUrls -> ordered.withPast(pastUrls) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedView())

    /** The reader, or null on the headline list. */
    private val _reader = MutableStateFlow<ReaderState?>(null)
    val reader: StateFlow<ReaderState?> = _reader

    /** The articles the reader has asked for, by URL. Cleared when it closes. */
    private val _articles = MutableStateFlow<Map<String, ArticleState>>(emptyMap())
    val articles: StateFlow<Map<String, ArticleState>> = _articles

    private val readHistory = ReadHistory(File(app.filesDir, ReadHistory.FILE))

    /** The feed as it was when the owner last left the app, for what counts as new. */
    /** The headlines the owner has viewed: their row went up off the screen ([scrolledPast]). */
    private val viewedHistory = ReadHistory(File(app.filesDir, VIEWED_FILE), limit = VIEWED_LIMIT)

    /** The report URLs of the generated stories opened ([HeadlinesState.storiesOpened]), kept between runs. */
    private val storiesOpenedHistory = ReadHistory(File(app.filesDir, "stories-opened.txt"), limit = 10_000)
    private var viewed: Set<String> = emptySet()

    /** When the feed first had each headline: the age of one without a publish time ([newUrlsOf]). */
    private val firstSeen = FirstSeen(File(app.filesDir, FirstSeen.FILE))

    /** The new ones of [items], by the viewed and [read] ones and [AppSettings.newWithinHours]. Reads and writes a file. */
    private suspend fun newOf(items: List<ArticleSummary>, read: Set<String>): Set<String> {
        val seen = withContext(readDispatcher) { firstSeen.note(items.map { it.url }) }
        val hours = _settings.value.newWithinHours
        val since = if (hours > 0) System.currentTimeMillis() - hours * 3_600_000L else null
        return newUrlsOf(items, viewed, read, since, seen)
    }

    /** The read and viewed histories ([ReadHistory]) are used one call at a time, off the main thread. */
    private val readDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings

    private val _downloads = MutableStateFlow(ArticleDownloads())
    val downloads: StateFlow<ArticleDownloads> = _downloads

    /** Articles read in the reader just closed, whose marks pop in on the list ([ReadMark]); cleared after a moment. */
    private val _justRead = MutableStateFlow<Set<String>>(emptySet())
    val justRead: StateFlow<Set<String>> = _justRead
    private val readInReader = HashSet<String>()

    /** The article the reader shows, as it settles on each page. */
    private var readerAt: String? = null

    /** On closing the reader, the article it was on, for the list to keep in view ([returned] once done). */
    private val _returnTo = MutableStateFlow<String?>(null)
    val returnTo: StateFlow<String?> = _returnTo

    /** Article fills and downloads not yet applied ([flushFilled]). */
    private val pending = HashMap<String, ArticleSummary>()
    private var downloadedSinceFlush = 0

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage

    private val _archiveImport = MutableStateFlow<ArchiveImport?>(null)

    /** The import of the archive's headlines into the feed's older part ([importArchive]): running, or its result. */
    val archiveImport: StateFlow<ArchiveImport?> = _archiveImport

    private val _backgroundRuns = MutableStateFlow<List<BackgroundRun>>(emptyList())
    val backgroundRuns: StateFlow<List<BackgroundRun>> = _backgroundRuns

    private val articleJobs = mutableMapOf<String, Job>()
    private var fetchJob: Job? = null
    private var storiesJob: Job? = null
    private var olderJob: Job? = null

    /** Every older headline on the phone ([FeedCache.older]), newest first; the feed shows the first [olderShown]. */
    private var olderAll: List<ArticleSummary> = emptyList()
    private var olderShown = 0
    private var topicsJob: Job? = null

    /**
     * Every headline of the last refresh, in its order, filled in as their articles arrive: the feed
     * ([HeadlinesState.items]) is those of them whose article is on the phone ([onPhone]), so no headline shows with
     * only the headline's data (often no time, a cut summary). The rest wait for their articles ([fetchArticles]).
     * Touched on the main thread only.
     */
    private var listed: List<ArticleSummary> = emptyList()
    private val onPhone = HashSet<String>()

    /** The headlines [admit] filled from their saved articles in this run of the app: each is read once. */
    private val filledFromSaved = HashSet<String>()

    /**
     * Makes [items] the listed headlines and returns the feed of them: those whose article is on the phone. [fill]: also
     * fills each from its saved article (once per run); without it (the saved feed at launch, shown at once) that is
     * left to [fillFromSaved].
     */
    private suspend fun admit(items: List<ArticleSummary>, fill: Boolean = true): List<ArticleSummary> {
        val done = HashSet(filledFromSaved)
        val (here, filled) = withContext(Dispatchers.IO) {
            val here = HashSet<String>()
            val filled = HashMap<String, ArticleSummary>()
            items.forEach { item ->
                if (!repository.hasArticle(item.url)) return@forEach
                here += item.url
                // Filled from its saved article once per run of the app: one downloaded earlier (by the background
                // refresh, a run that ran out of time) kept its list page's data, and one filled by an older build
                // lacks what it learned to read since (the video mark): the subtitle, picture, time and video from
                // the article, as the list page's are not.
                if (fill && item.url !in done) repository.savedArticle(item.url)?.let { filled[item.url] = item.filledFrom(it) }
            }
            here to filled
        }
        if (fill) filledFromSaved.addAll(here)
        listed = items.map { filled[it.url] ?: it }
        newsApp.mediaDates.noteHeadlines(listed)
        onPhone.clear()
        onPhone.addAll(here)
        return listed.filter { it.url in onPhone }
    }

    /**
     * After the saved feed shows at launch ([admit] without filling): its headlines filled from their saved articles, as
     * [admit] does once per run, in the background, the feed and its stories updated with those that changed. Left to the
     * refresh's own [admit] when a refresh replaced the listed headlines meanwhile.
     */
    private fun fillFromSaved() {
        viewModelScope.launch {
            val base = listed
            val todo = base.filter { it.url in onPhone && it.url !in filledFromSaved }
            if (todo.isEmpty()) return@launch
            val changed = withContext(Dispatchers.IO) {
                todo.mapNotNull { item -> repository.savedArticle(item.url)?.let(item::filledFrom)?.takeIf { it != item }?.let { item.url to it } }.toMap()
            }
            if (listed !== base) return@launch
            filledFromSaved.addAll(todo.map { it.url })
            if (changed.isEmpty()) return@launch
            listed = listed.map { changed[it.url] ?: it }
            newsApp.mediaDates.noteHeadlines(changed.values.toList())
            val fix = { list: List<ArticleSummary> -> list.map { changed[it.url] ?: it } }
            _headlines.update { s ->
                s.copy(
                    items = listed.filter { it.url in onPhone },
                    stories = (s.stories as? StoriesState.Ready)?.let { r -> r.copy(stories = r.stories.map { Story(fix(it.reports)) }, forItems = fix(r.forItems)) } ?: s.stories,
                )
            }
        }
    }

    /**
     * How much of every saved topic every visible headline mentions, on the topic slider's scale: topic →
     * url → score ([news.app.stories.wordScores]). Computed by [matchTopics]; switching a topic on or off, or
     * moving the threshold, only picks from it again ([applyTopics]).
     */
    private val topicScores = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    /** Whether the topics' closeness is being computed: a topic was added, the feed or its stories changed. */
    private val _topicsWorking = MutableStateFlow(false)
    val topicsWorking: StateFlow<Boolean> = _topicsWorking

    /** How many headlines each saved topic brings at the threshold, on or not: for the sheet and settings. */
    val topicCounts: StateFlow<Map<String, Int>> = combine(topicScores, _settings) { scores, s ->
        s.topics.associate { t -> t.text to scores[t.text].orEmpty().count { it.value >= s.topicThreshold } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Topics saved on: the feed waits for their matches (an empty list meanwhile, not the whole feed).
        applyTopics()
        // New stories (a refresh, the saved feed) bring other headlines: match them.
        viewModelScope.launch {
            _headlines.map { (it.stories as? StoriesState.Ready)?.forItems }.distinctUntilChanged().collect { matchTopics() }
        }
        viewModelScope.launch {
            val read = withContext(readDispatcher) { readHistory.all() }
            _headlines.update { it.copy(read = it.read + read) }
        }
        // The last feed (from the app or the background refresh) shows at once, with the stories saved for it, so
        // Stories opens without the network; the refresh then replaces it.
        val restore = viewModelScope.launch {
            // The saved feed is read alongside the histories, not after them.
            val savedFeed = async(Dispatchers.IO) { newsApp.feedCache.loadHeadlines() }
            viewed = withContext(readDispatcher) {
                // Before the viewed list (an update from the seen snapshot): what the feed holds now counts as viewed,
                // so it doesn't all turn new at once.
                if (!viewedHistory.exists) {
                    newsApp.feedCache.loadHeadlines()?.items?.map { it.url }?.let(viewedHistory::addAll)
                    File(app.filesDir, "seen.txt").delete()
                }
                viewedHistory.all()
            }
            val storiesOpened = withContext(readDispatcher) { storiesOpenedHistory.all() }
            _headlines.update { it.copy(storiesOpened = storiesOpened) }
            val saved = savedFeed.await()
            if (saved != null && _headlines.value.items.isEmpty()) {
                // At once: which are on the phone only; filling them from their saved articles (reading every one)
                // follows in the background.
                val cached = saved.copy(items = admit(saved.items, fill = false))
                val fresh = newOf(listed, _headlines.value.read)
                _headlines.update { s ->
                    if (s.items.isNotEmpty()) {
                        s
                    } else {
                        s.copy(
                            items = cached.items,
                            failures = cached.failures,
                            refreshedAt = cached.fetchedAt?.let(Instant::fromEpochMilliseconds),
                            newUrls = fresh,
                            stories = cachedStories(cached, s.hiddenSources) ?: s.stories,
                        )
                    }
                }
                fillFromSaved()
            }
            _headlines.update { it.copy(starting = false) }
            // Nothing saved for Stories: group the cached feed now rather than after the network answers.
            if (_headlines.value.view.groupsStories) findStories()
        }
        // Opening the app refreshes by the same rule as coming back to it ([refreshesOnOpen]), against the time the
        // saved feed was fetched, by the app or a background refresh: Samsung ends a backgrounded app, so most returns
        // are a new start, and they refreshed every time. After the saved feed is in (its time is that feed's).
        viewModelScope.launch {
            restore.join()
            loadOlder()
            if (refreshesOnOpen(_headlines.value.refreshedAt, _settings.value.refreshOnReturnMinutes, Clock.System.now())) refresh()
            // Not stale: the server's stories and generated articles are still asked for, without a pull to refresh.
            else if (newsApp.server.storiesOn && _headlines.value.visibleItems.isNotEmpty()) findStories(quiet = true, force = true)
        }
        BackgroundRefresh.schedule(app, _settings.value.backgroundMinutes)
        // The server's generated articles, as they come with its stories.
        viewModelScope.launch {
            newsApp.server.writing.collect { w -> _headlines.update { it.copy(summaries = w.summaries) } }
        }
        // The news from the server, and it doesn't answer: the feed says so under its title.
        viewModelScope.launch {
            newsApp.server.state.map { it is ServerState.Connected }
                .distinctUntilChanged()
                .collect { on -> _headlines.update { it.copy(serverOn = on) } }
        }
        viewModelScope.launch {
            newsApp.server.state.map { it is ServerState.Connected && it.unreachableSince != null }
                .distinctUntilChanged()
                .collect { down -> _headlines.update { it.copy(serverDown = down) } }
        }
    }

    /**
     * Back in the app: fetches the headlines again when the feed on screen is older than
     * [AppSettings.refreshOnReturnMinutes] (0: never) ([refreshesOnOpen]). Not before the saved feed or the first fetch
     * is in (opening the app decides then) or while one runs.
     */
    fun refreshIfStale() {
        val at = _headlines.value.refreshedAt ?: return
        if (refreshesOnOpen(at, _settings.value.refreshOnReturnMinutes, Clock.System.now())) refresh()
        // Not stale: the server's stories (and generated articles) are asked for anyway, so they needn't a refresh.
        else if (newsApp.server.storiesOn && _headlines.value.visibleItems.isNotEmpty()) findStories(quiet = true, force = true)
    }

    /** Fetches every site's headlines; [after]: a job to wait for first, while the screen already says it refreshes. */
    fun refresh(after: Job? = null) {
        if (_headlines.value.refreshing) return
        _headlines.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            after?.join()
            // The user's own choices from the server first: the sites they hid and what they read on their other devices.
            if (newsApp.server.inUse) {
                newsApp.server.sources()?.let(::showServerSources)
                syncRead()
            }
            val result = repository.latest(skip = _settings.value.hiddenSources)
            val before = _headlines.value
            var items = withFilledFrom(result.items, listed)
            // New headlines show only with their articles (their time, picture and subtitle) and in their stories:
            // first the articles, then the server's stories, while the feed on screen stays as it was; then the new
            // feed and its stories at once. Those whose article isn't in by then wait for it ([fetchArticles]).
            var stories: StoriesState? = null
            val known = before.items.mapTo(HashSet()) { it.url }
            val fills = articlesOf(items.filter { it.url !in known && it.sourceId !in before.hiddenSources })
            items = admit(items.map { fills[it.url] ?: it })
            if (before.items.isNotEmpty()) {
                stories = groupForShowing(items.filter { it.sourceId !in before.hiddenSources })
            }
            val fresh = newOf(listed, _headlines.value.read)
            _headlines.update {
                it.copy(
                    items = items,
                    failures = result.failures,
                    refreshing = false,
                    refreshedAt = Instant.fromEpochMilliseconds(result.fetchedAt ?: System.currentTimeMillis()),
                    newUrls = fresh,
                    // Folded into the viewed ones, which the new ones leave out now.
                    pastUrls = emptySet(),
                    // Only new: what is still new now, the viewed ones gone.
                    onlyNewUrls = if (it.onlyNew) fresh else it.onlyNewUrls,
                    stories = stories ?: it.stories,
                )
            }
            // The rest of the articles (in advance, pictures and times); the stories are regrouped after them.
            fetchArticles()
        }
    }

    /**
     * The articles of [items] (new headlines), a few at a time, within [NEW_ARTICLES_MS]: each headline filled from its
     * article (time, picture, subtitle). The download line shows them coming in; the
     * stories are asked for once, after them ([groupForShowing]). Those not in by then wait ([fetchArticles]).
     */
    private suspend fun articlesOf(items: List<ArticleSummary>): Map<String, ArticleSummary> {
        if (items.isEmpty()) return emptyMap()
        val filled = java.util.concurrent.ConcurrentHashMap<String, ArticleSummary>()
        _downloads.value = ArticleDownloads(done = 0, total = items.size, running = true)
        withTimeoutOrNull(NEW_ARTICLES_MS) {
            perSite(items) { item ->
                val article = runCatching { repository.article(item.sourceId, item.url) }.getOrNull() ?: return@perSite
                filled[item.url] = item.filledFrom(article)
                _downloads.update { it.copy(done = it.done + 1) }
            }
        }
        _downloads.update { it.copy(running = false) }
        return HashMap(filled)
    }

    /** The server's stories of [items], asked for before they are shown ([refresh]); null when it sent none. */
    private suspend fun groupForShowing(items: List<ArticleSummary>): StoriesState.Ready? {
        val (text, threshold) = grouping()
        newsApp.server.stories(items)?.let { return StoriesState.Ready(newestFirst(it, items), items, threshold, text = text) }
        return if (newsApp.server.storiesOn) null else ungrouped(items)
    }

    /** Brings the rest of the feed's articles and fills the headlines from them, as [ArticleFetcher] does. */
    private fun fetchArticles() {
        fetchJob?.cancel()
        val settings = _settings.value
        // The waiting headlines too: each joins the feed once its article is on the phone ([flushFilled]).
        val items = listed.filter { it.sourceId !in settings.hiddenSources }
        val queue = fetcher.queue(items)
        val onPhone = queue.count { repository.hasArticle(it.url) }
        _downloads.value = ArticleDownloads(
            done = onPhone,
            total = queue.size,
            before = onPhone,
            running = true,
        )
        fetchJob = viewModelScope.launch {
            // Articles arrive several a second: the feed and its counter take them at most once a second, since every
            // new feed state is filtered and ordered again.
            val flusher = launch {
                while (true) {
                    delay(FLUSH_MS)
                    flushFilled()
                }
            }
            val filled = fetcher.run(
                items,
                onFilled = { updated -> synchronized(pending) { pending[updated.url] = updated } },
                onDownloaded = { synchronized(pending) { downloadedSinceFlush++ } },
            )
            flusher.cancel()
            flushFilled()
            _downloads.update { it.copy(running = false) }
            // The headlines whose articles came after the refresh's own grouping join their stories, quietly: the
            // refresh already showed its progress once.
            if (_headlines.value.view.groupsStories) findStories(quiet = true)
            saveFeed(filled)
            // The headlines that left the feed with this refresh join its older part.
            loadOlder()
            // The pictures this run brought may have taken the caches past their quotas.
            newsApp.scope.launch { runCatching { newsApp.trimMedia() } }
        }
    }

    /** Applies the articles' fills and the download count gathered since the last call, in one update each. */
    private fun flushFilled() {
        val (fills, downloaded) = synchronized(pending) {
            (HashMap(pending) to downloadedSinceFlush).also { pending.clear(); downloadedSinceFlush = 0 }
        }
        if (fills.isNotEmpty()) {
            listed = listed.map { fills[it.url] ?: it }
            newsApp.mediaDates.noteHeadlines(fills.values.toList())
            onPhone.addAll(fills.keys)
            _headlines.update { s -> s.copy(items = listed.filter { it.url in onPhone }) }
        }
        if (downloaded > 0) _downloads.update { it.copy(done = it.done + downloaded) }
    }

    /**
     * Reads the older headlines on the phone ([FeedCache.older]): those the feed showed before, but its own; the
     * feed keeps showing as many of them as it did ([olderShown]).
     */
    private fun loadOlder() {
        olderJob?.cancel()
        val except = listed.mapTo(HashSet()) { it.url }
        olderJob = viewModelScope.launch {
            olderAll = withContext(Dispatchers.IO) {
                runCatching { newsApp.feedCache.older(except, available = repository::canOpen) }.getOrDefault(emptyList())
            }
            showOlder()
        }
    }

    /** The list is scrolled near its end: the next [OLDER_PAGE] older headlines join it. */
    fun showMoreOlder() {
        if (olderShown >= olderAll.size) return
        olderShown += OLDER_PAGE
        showOlder()
    }

    /** Shows the first [olderShown] of [olderAll], grouped into stories with the rest. */
    private fun showOlder() {
        val older = olderAll.take(olderShown)
        if (older == _headlines.value.older) return
        newsApp.mediaDates.noteHeadlines(older)
        _headlines.update { it.copy(older = older) }
        if (_headlines.value.view.groupsStories) findStories(quiet = true)
    }

    /**
     * Writes the feed as the app has it, with the stories found for it, so the next start opens on both.
     */
    private suspend fun saveFeed(items: List<ArticleSummary> = listed) {
        val s = _headlines.value
        val ready = s.stories as? StoriesState.Ready
        val previous = if (ready == null) withContext(Dispatchers.IO) { newsApp.feedCache.loadHeadlines() } else null
        withContext(Dispatchers.IO) {
            newsApp.feedCache.saveHeadlines(
                Headlines(
                    items = items,
                    failures = s.failures,
                    fetchedAt = s.refreshedAt?.toEpochMilliseconds(),
                    stories = ready?.stories?.map { story -> story.reports.map { it.url } } ?: previous?.stories,
                    storiesThreshold = ready?.threshold ?: previous?.storiesThreshold,
                    storiesText = ready?.text?.name ?: previous?.storiesText,
                ),
            )
        }
    }

    /** The stories saved with a cached feed, as [StoriesState.Ready] over that feed's visible headlines; null without any. */
    private fun cachedStories(cached: Headlines, hidden: Set<String>): StoriesState.Ready? {
        val groups = cached.stories ?: return null
        val items = cached.items.filter { it.sourceId !in hidden }
        val byUrl = items.associateBy { it.url }
        val stories = groups.mapNotNull { group -> group.mapNotNull { byUrl[it] }.takeIf { it.size > 1 }?.let(::Story) }
        // Grouped by another text than the setting's: shown until the regrouping that the differing text calls for.
        val text = StoryText.entries.firstOrNull { it.name == cached.storiesText } ?: StoryText.SUBTITLE
        val threshold = cached.storiesThreshold ?: grouping().second
        return StoriesState.Ready(newestFirst(stories, items), items, threshold, text = text)
    }

    /** Shows only [ids]' headlines and the stories they are in (empty: every site). */
    fun showSites(ids: Set<String>) {
        _headlines.update { it.copy(sites = ids) }
        settingsStore.saveFilters(ids, _headlines.value.categories)
    }

    /** Shows only the headlines of [ids] ([OTHER_CATEGORY] for those with none; empty: every category). */
    fun showCategories(ids: Set<String>) {
        _headlines.update { it.copy(categories = ids) }
        settingsStore.saveFilters(_headlines.value.sites, ids)
    }

    /** Shows only what is new, as it is now ([HeadlinesState.onlyNewUrls]), or everything again. */
    fun showOnlyNew(only: Boolean) {
        _headlines.update { it.copy(onlyNew = only, onlyNewUrls = if (only) it.newUrls - it.pastUrls - it.read else emptySet()) }
    }

    /** Only new, back at the top of the list: what is still new now; the ones viewed meanwhile leave. */
    fun refreshOnlyNew() {
        if (_headlines.value.onlyNew) showOnlyNew(true)
    }

    fun openSettings() {
        _backgroundRuns.value = backgroundLog.runs()
        updateStorage()
        _showSettings.value = true
        loadServerSources()
        loadServerGrouping()
        _admin.value = AdminState()
    }

    private val _serverGrouping = MutableStateFlow<ServerGrouping?>(null)

    /** With the server on and grouping stories: its grouping settings, for Settings › סיפורים; null otherwise. */
    val serverGrouping: StateFlow<ServerGrouping?> = _serverGrouping

    private fun loadServerGrouping() {
        if (!newsApp.server.inUse) {
            _serverGrouping.value = null
            _fullArticles.value = null
            _storyLength.value = null
            _rewrite.value = null
            return
        }
        viewModelScope.launch { _serverGrouping.value = newsApp.server.grouping() }
        viewModelScope.launch { _fullArticles.value = newsApp.server.fullArticles() }
        viewModelScope.launch { _storyLength.value = newsApp.server.storyLength() }
        viewModelScope.launch { _rewrite.value = newsApp.server.rewrite() }
    }

    private val _rewrite = MutableStateFlow<news.app.server.Rewrite?>(null)

    /** With the server writing summaries: how often it writes a story again, and its reserve; else null. */
    val rewrite: StateFlow<news.app.server.Rewrite?> = _rewrite

    fun setRewrite(minutes: Int? = null, onNewSite: Boolean? = null, reserveAfter: Int? = null) {
        _rewrite.value?.let { r -> _rewrite.value = r.copy(minutes = minutes ?: r.minutes, onNewSite = onNewSite ?: r.onNewSite, reserveAfter = reserveAfter ?: r.reserveAfter) }
        viewModelScope.launch { _rewrite.value = newsApp.server.setRewrite(minutes, onNewSite, reserveAfter) ?: newsApp.server.rewrite() }
    }

    private val _storyLength = MutableStateFlow<String?>(null)

    /** With the server writing summaries: how long its stories are (SHORT, MEDIUM, LONG, FULL); else null. */
    val storyLength: StateFlow<String?> = _storyLength

    fun setStoryLength(length: String) {
        _storyLength.value = length
        viewModelScope.launch { _storyLength.value = newsApp.server.setStoryLength(length) ?: newsApp.server.storyLength() }
    }

    private val _fullArticles = MutableStateFlow<Boolean?>(null)

    /** With the server writing summaries: whether Gemini reads the full articles (Settings › סיפורים); else null. */
    val fullArticles: StateFlow<Boolean?> = _fullArticles

    fun setFullArticles(on: Boolean) {
        _fullArticles.value = on
        viewModelScope.launch { _fullArticles.value = newsApp.server.setFullArticles(on) ?: newsApp.server.fullArticles() }
    }

    private var regroupJob: Job? = null

    /**
     * Changes the server's grouping (Settings › סיפורים with the server on): it regroups at once, and its stories are
     * asked for again a few seconds later.
     */
    fun setServerGrouping(strictness: Int? = null, text: StoryText? = null, days: Int? = null, byGemini: Boolean? = null) {
        viewModelScope.launch {
            val updated = newsApp.server.setGrouping(strictness, text, days, byGemini) ?: return@launch
            _serverGrouping.value = updated
            regroupJob?.cancel()
            regroupJob = viewModelScope.launch {
                delay(REGROUP_WAIT_MS)
                findStories(force = true)
            }
        }
    }

    private val _serverSources = MutableStateFlow<List<ServerSource>?>(null)

    /** With the server on: the sites it reads, for Settings › אתרים; null otherwise, or before it answers. */
    val serverSources: StateFlow<List<ServerSource>?> = _serverSources

    private fun loadServerSources() {
        if (!newsApp.server.inUse) {
            _serverSources.value = null
            return
        }
        viewModelScope.launch { newsApp.server.sources()?.let(::showServerSources) }
    }

    /**
     * The server's sites on the sites page, and the feed showing those it reads: a site switched off on the server is
     * hidden on the phone too, so turning the server off later keeps the same choice.
     */
    private fun showServerSources(list: List<ServerSource>) {
        _serverSources.value = list
        val hidden = feedHidden(_settings.value.hiddenSources, list)
        if (hidden != _settings.value.hiddenSources) updateSettings(_settings.value.copy(hiddenSources = hidden))
    }

    /**
     * Shows [id] in this user's feed or hides it ("מוצג בפיד שלי" on the sites page): their choice, kept on the server
     * for all their devices; the feed follows at once, and goes back if the server didn't take it.
     */
    fun setSourceHidden(id: String, hidden: Boolean) {
        val before = _serverSources.value ?: return
        showServerSources(before.map { if (it.id == id) it.copy(hidden = hidden) else it })
        viewModelScope.launch {
            val updated = newsApp.server.setSourceHidden(id, hidden)
            showServerSources((_serverSources.value ?: before).map { if (it.id == id) updated ?: it.copy(hidden = !hidden) else it })
        }
    }

    // --- Read marks across the user's devices ([ReadSync]) ---

    private var readFlush: Job? = null

    /** Sends the read marks waiting, a moment after the last one, so a run of swipes goes in one request. */
    private fun sendReadSoon(urls: Collection<String>) {
        if (!newsApp.server.inUse) return
        newsApp.scope.launch {
            newsApp.readSync.queue(urls)
            readFlush?.cancel()
            readFlush = newsApp.scope.launch {
                delay(READ_FLUSH_MS)
                newsApp.readSync.flush()
            }
        }
    }

    /** What waits goes to the server; what the user read on their other devices joins the read marks here. */
    private suspend fun syncRead() {
        newsApp.readSync.flush()
        val elsewhere = newsApp.readSync.pull() ?: return
        val known = _headlines.value.read
        val fresh = elsewhere.filter { it !in known }
        if (fresh.isEmpty()) return
        withContext(readDispatcher) { readHistory.addAll(fresh) }
        _headlines.update { it.copy(read = it.read + fresh) }
    }

    // --- Admin: the server's people (Settings › משתמשים, admins only) ---

    private val _admin = MutableStateFlow(AdminState())

    /** The users and their devices, the invite just made, and the last error, for the admin page. */
    val admin: StateFlow<AdminState> = _admin

    fun loadUsers() {
        _admin.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            newsApp.server.users().fold(
                onSuccess = { users -> _admin.update { it.copy(users = users, loading = false) } },
                onFailure = { e -> _admin.update { it.copy(loading = false, error = adminError(e)) } },
            )
        }
    }

    fun createInvite(user: String, role: news.app.server.UserRole) {
        val name = user.trim()
        if (name.isEmpty()) return
        _admin.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            newsApp.server.createInvite(name, role).fold(
                onSuccess = { invite -> _admin.update { it.copy(invite = invite, loading = false) }; loadUsers() },
                onFailure = { e -> _admin.update { it.copy(loading = false, error = adminError(e)) } },
            )
        }
    }

    fun dismissInvite() = _admin.update { it.copy(invite = null) }

    fun setUserRole(user: String, role: news.app.server.UserRole) = adminChange { newsApp.server.setUserRole(user, role) }

    fun revokeDevice(serial: String) = adminChange { newsApp.server.revokeDevice(serial) }

    fun removeUser(user: String) = adminChange { newsApp.server.removeUser(user) }

    /** An admin change, then the list again (it shows the change as the server keeps it). */
    private fun adminChange(change: suspend () -> Result<*>) {
        _admin.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            change().onFailure { e -> _admin.update { it.copy(error = adminError(e)) } }
            loadUsers()
        }
    }

    /** One site's settings on the server (the sites page with the server on); the feed follows a site switched on or off. */
    fun setServerSource(id: String, enabled: Boolean? = null, feed: Boolean? = null, waitMs: Int? = null) {
        viewModelScope.launch {
            val updated = newsApp.server.setSource(id, enabled, feed, waitMs) ?: return@launch
            showServerSources(_serverSources.value.orEmpty().map { if (it.id == id) updated else it })
            if (enabled != null) refreshNow()
        }
    }

    fun closeSettings() {
        _showSettings.value = false
        _serverPage.value = false
    }

    // --- The news server's paper: העיתון שלי and the generated article pages ---

    private val _summaryStory = MutableStateFlow<Story?>(null)

    /** The story whose generated article is open as a page. */
    val summaryStory: StateFlow<Story?> = _summaryStory

    fun showSummary(story: Story?) {
        // Back to the feed: it brings the story's card into view, where it was ([returnTo]).
        if (story == null) _summaryStory.value?.let { _returnTo.value = it.lead.url }
        _summaryStory.value = story
        story?.let(::markStoryOpened)
        story?.let(::loadThreadsOf)
        story?.let(::loadStoryArticles)
    }

    private val _storyArticles = MutableStateFlow<Map<String, Article>>(emptyMap())

    /** The articles of the generated story open, by URL, as they come in: its page's pictures and videos. */
    val storyArticles: StateFlow<Map<String, Article>> = _storyArticles

    private var storyArticlesJob: Job? = null

    private fun loadStoryArticles(story: Story) {
        storyArticlesJob?.cancel()
        val unrelated = story.reports.firstNotNullOfOrNull { _headlines.value.summaries[it.url] }?.unrelated.orEmpty().toSet()
        val reports = story.reports.filter { it.url !in unrelated }
        _storyArticles.update { known -> known.filterKeys { url -> reports.any { it.url == url } } }
        storyArticlesJob = viewModelScope.launch {
            reports.filter { it.url !in _storyArticles.value }.forEach { report ->
                launch {
                    val article = runCatching { repository.article(report.sourceId, report.url) }.getOrNull() ?: return@launch
                    _storyArticles.update { it + (report.url to article) }
                }
            }
        }
    }

    private val _threadLayers = MutableStateFlow<List<ThreadLayer>>(emptyList())

    /** Thread pages and the articles opened from them, over the feed and its article page; the last on top. */
    val threadLayers: StateFlow<List<ThreadLayer>> = _threadLayers

    private val _threadPages = MutableStateFlow<Map<String, news.app.server.ServerThread>>(emptyMap())

    /** The thread pages fetched so far (on demand, from the server), by id. */
    val threadPages: StateFlow<Map<String, news.app.server.ServerThread>> = _threadPages

    /** Opens [ref]'s page, [currentId] (the story it was opened from) highlighted. */
    fun openThread(ref: news.app.server.ThreadRef, currentId: String?) {
        _threadLayers.update { it + ThreadLayer.Page(ref, currentId) }
        loadThread(ref.id)
    }

    private val _allThreads = MutableStateFlow<List<news.app.server.ThreadRef>?>(null)

    /** Every thread, for the threads list; null while it loads. */
    val allThreads: StateFlow<List<news.app.server.ThreadRef>?> = _allThreads

    /** The threads list (the bottom bar's ⏱), fetched again each time it opens. */
    fun openThreads() {
        _threadLayers.update { it + ThreadLayer.All }
        refreshThreads()
    }

    private val _threadsRefreshing = MutableStateFlow(false)

    /** The threads list is being fetched (its pull to refresh). */
    val threadsRefreshing: StateFlow<Boolean> = _threadsRefreshing

    fun refreshThreads() {
        if (_threadsRefreshing.value) return
        _threadsRefreshing.value = true
        viewModelScope.launch {
            _allThreads.value = newsApp.server.threads() ?: _allThreads.value ?: emptyList()
            _threadsRefreshing.value = false
        }
    }

    private val _threadsMap = MutableStateFlow<List<news.app.server.ServerThread>?>(null)

    /** Every thread with its stories, for the map; null while it loads. */
    val threadsMap: StateFlow<List<news.app.server.ServerThread>?> = _threadsMap

    /** The threads map (the title bar's button), fetched each time it opens. */
    fun openMap() {
        _threadLayers.update { it + ThreadLayer.Map }
        viewModelScope.launch { _threadsMap.value = newsApp.server.threadsMap() ?: _threadsMap.value ?: emptyList() }
    }

    private val _threadMapView = MutableStateFlow(settingsStore.threadMapView)

    /** The threads map's view, kept between runs. */
    val threadMapView: StateFlow<ThreadMapView> = _threadMapView

    fun setThreadMapView(view: ThreadMapView) {
        _threadMapView.value = view
        settingsStore.threadMapView = view
    }

    /** The map's story: its article page; its thread's page at it when there is nothing to show. */
    fun openMapStory(story: news.app.server.ThreadStory, thread: news.app.server.ThreadRef) {
        if (!openThreadStory(story, thread)) openThread(thread, story.id)
    }

    /** Back from the top thread layer. */
    fun closeThreadLayer() {
        _threadLayers.update { it.dropLast(1) }
    }

    /**
     * A thread's story ([thread]: the one it was opened from) as its article page: the feed's story when the feed has
     * it, else one made from the thread's copy, its reports from its footnotes. False when there is nothing to show
     * (no footnotes).
     */
    fun openThreadStory(story: news.app.server.ThreadStory, thread: news.app.server.ThreadRef? = null): Boolean {
        val s = _headlines.value
        val inFeed = (s.stories as? StoriesState.Ready)?.stories.orEmpty()
            .firstOrNull { st -> st.reports.any { s.summaries[it.url]?.id == story.id } }
        if (inFeed != null) {
            _threadLayers.update { it + ThreadLayer.Article(inFeed) }
            markStoryOpened(inFeed)
            loadThreadsOf(inFeed)
            loadStoryArticles(inFeed)
            return true
        }
        val (made, summary) = threadStoryPage(story, thread) ?: return false
        _threadLayers.update { it + ThreadLayer.Article(made, summary) }
        summary.threads.forEach { loadThread(it.id) }
        loadStoryArticles(made)
        return true
    }

    /** The pages of [story]'s threads, for its article page's related stories. */
    private fun loadThreadsOf(story: Story) {
        val summary = story.reports.firstNotNullOfOrNull { _headlines.value.summaries[it.url] } ?: return
        summary.threads.take(2).forEach { loadThread(it.id) }
    }

    private val _storyVersions = MutableStateFlow<Map<String, List<StorySummary>>>(emptyMap())

    /** The versions of the stories opened ([openVersions]), newest first, by story id; fetched again on each opening. */
    val storyVersions: StateFlow<Map<String, List<StorySummary>>> = _storyVersions

    /** Opens [summary]'s story's versions over the feed or its page, and fetches them from the server. */
    fun openVersions(summary: StorySummary) {
        if (summary.id.isEmpty()) return
        _threadLayers.update { it + ThreadLayer.Versions(summary.id, summary.title) }
        viewModelScope.launch {
            // None when the server doesn't answer: the page says it has none, rather than loading for good.
            val versions = newsApp.server.storyVersions(summary.id).orEmpty()
            _storyVersions.update { it + (summary.id to versions) }
        }
    }

    private fun loadThread(id: String) {
        viewModelScope.launch {
            val page = newsApp.server.thread(id) ?: return@launch
            _threadPages.update { it + (id to page) }
        }
    }

    /** The feed's switch between the stories' generated articles and their original headlines. */
    fun showOriginal(original: Boolean) {
        _headlines.update { it.copy(showOriginal = original, flipped = emptySet()) }
    }

    /** Only the stories with a generated article, or the whole feed. */
    fun showOnlyGenerated(only: Boolean) {
        _headlines.update { it.copy(onlyGenerated = only) }
        settingsStore.onlyGenerated = only
    }

    /** One story, by its lead's URL, to the other view than the feed's switch. */
    fun flipStory(leadUrl: String) {
        _headlines.update { it.copy(flipped = if (leadUrl in it.flipped) it.flipped - leadUrl else it.flipped + leadUrl) }
    }

    /** [story] is read, as a generated story: all its reports now, so only a new site makes it unread again. */
    private fun markStoryOpened(story: Story) {
        val urls = story.reports.map { it.url }
        scrolledPast(urls)
        if (_headlines.value.storiesOpened.containsAll(urls)) return
        _headlines.update { it.copy(storiesOpened = it.storiesOpened + urls) }
        viewModelScope.launch(readDispatcher) { storiesOpenedHistory.addAll(urls) }
    }

    /** A footnote's article in the reader: the feed's headline when it has it, else one made from the footnote. */
    fun openFootnote(note: Footnote) {
        val known = _headlines.value.allItems.firstOrNull { it.url == note.url }
        open(known ?: ArticleSummary(note.sourceId.orEmpty(), note.url, note.title.orEmpty()))
    }

    // --- The news server (Settings › שרת החדשות) ---

    /** The connection to the news server, as its Settings page shows it. */
    val server: StateFlow<ServerState> = newsApp.server.state

    private val _serverPage = MutableStateFlow(false)

    /** Settings shows the server's page: opened there, or by an enrollment link. */
    val serverPage: StateFlow<Boolean> = _serverPage

    fun showServerPage(show: Boolean) {
        _serverPage.value = show
        if (!show) _statsOpen.value = false
    }

    private val _statsOpen = MutableStateFlow(false)

    /** The server's statistics page is open (over the server page). */
    val statsOpen: StateFlow<Boolean> = _statsOpen

    private val _serverStats = MutableStateFlow<ServerStats?>(null)

    /** What the server has done, as last asked; null before the first answer. */
    val serverStats: StateFlow<ServerStats?> = _serverStats

    private val _statsLoading = MutableStateFlow(false)
    val statsLoading: StateFlow<Boolean> = _statsLoading

    fun showStats(show: Boolean) {
        _statsOpen.value = show
        if (show) refreshStats()
    }

    fun refreshStats() {
        if (_statsLoading.value) return
        _statsLoading.value = true
        viewModelScope.launch {
            newsApp.server.stats()?.let { _serverStats.value = it }
            _statsLoading.value = false
        }
    }

    /** A scanned QR code or a tapped `news://enroll` link: the server's page, enrolling with it. */
    fun enrollWith(link: EnrollmentLink) {
        openSettings()
        _serverPage.value = true
        newsApp.server.enroll(link)
        afterConnecting()
    }

    /** A whole enrollment link pasted into the address or the code goes on as a scanned one ([enrollFromText]). */
    fun setServerAddress(address: String) {
        if (!enrollFromText(address)) newsApp.server.setAddress(address)
    }

    fun setServerCode(code: String) {
        if (!enrollFromText(code)) newsApp.server.setCode(code)
    }

    /**
     * Enrolls with the `news://enroll` link in [text] (copied from a message, where it can't be tapped), as a scanned
     * one: its key pinned, nothing to confirm. False when [text] has none.
     */
    fun enrollFromText(text: String): Boolean {
        val link = findEnrollmentLink(text) ?: return false
        enrollWith(link)
        return true
    }

    fun connectServer() {
        newsApp.server.connect()
    }

    fun confirmServerKey() {
        newsApp.server.confirmKey()
        afterConnecting()
    }

    fun cancelServer() = newsApp.server.cancel()

    /** Once connected, the feed comes from the server: refreshed then, rather than at the next refresh. */
    private fun afterConnecting() {
        viewModelScope.launch {
            // Enrolling takes seconds; one that fails or waits for the key just lets this go.
            withTimeoutOrNull(ENROLL_WAIT_MS) { newsApp.server.state.first { it is ServerState.Connected } } ?: return@launch
            refreshNow()
        }
    }

    fun retryServer() {
        newsApp.server.retry()
        refreshNow()
    }

    fun disconnectServer() {
        newsApp.server.disconnect()
        // Another server keeps its own read marks: nothing waits for this one, and the next asks from the start.
        newsApp.scope.launch { newsApp.readSync.reset() }
        refreshNow()
    }

    /** The feed again from the server, and its stories. */
    private fun refreshNow() {
        if (_headlines.value.refreshing) return
        refresh()
    }

    fun updateSettings(new: AppSettings) {
        val old = _settings.value
        _settings.value = new
        settingsStore.save(new)
        _headlines.update { it.copy(hiddenSources = new.hiddenSources) }
        // A site switched back on was left out of the last refresh.
        if ((old.hiddenSources - new.hiddenSources).isNotEmpty()) refresh()
        if (new.newWithinHours != old.newWithinHours) viewModelScope.launch {
            val fresh = newOf(listed, _headlines.value.read)
            _headlines.update { it.copy(newUrls = fresh, pastUrls = it.pastUrls.filterTo(HashSet()) { u -> u in fresh }) }
        }
        if (new.articleCacheMb != old.articleCacheMb) repository.articleQuotaBytes = new.articleCacheMb.toLong() shl 20
        if (new.imageCacheMb < old.imageCacheMb || new.videoCacheMb < old.videoCacheMb) newsApp.scope.launch { runCatching { newsApp.trimMedia() } }
        if (new.backgroundMinutes != old.backgroundMinutes) BackgroundRefresh.schedule(getApplication(), new.backgroundMinutes, restart = true)
        if (new.hiddenSources != old.hiddenSources && _headlines.value.view.groupsStories) findStories()
        // A topic added needs its closeness; a topic switched, removed, or the threshold moved, only a new pick.
        if ((new.topics.map { it.text } - old.topics.map { it.text }.toSet()).isNotEmpty()) matchTopics()
        else if (new.topics != old.topics || new.topicThreshold != old.topicThreshold) applyTopics()
    }

    /** Saves [text] as a topic of the topic search, switched on (an existing one is switched on). */
    fun addTopic(text: String) {
        val topic = text.trim().replace(Regex("\\s+"), " ")
        if (topic.isEmpty()) return
        val s = _settings.value
        val topics = if (s.topics.any { it.text == topic }) s.topics.map { if (it.text == topic) it.copy(on = true) else it }
        else s.topics + SavedTopic(topic, on = true)
        updateSettings(s.copy(topics = topics))
    }

    /** Switches the saved topic [text] on or off. */
    fun toggleTopic(text: String) {
        val s = _settings.value
        updateSettings(s.copy(topics = s.topics.map { if (it.text == text) it.copy(on = !it.on) else it }))
    }

    /** Deletes the saved topic [text]. */
    fun removeTopic(text: String) {
        val s = _settings.value
        updateSettings(s.copy(topics = s.topics.filterNot { it.text == text }))
    }

    /**
     * Scores every saved topic against the visible headlines by their words ([wordScores]), then picks the feed's rows
     * ([applyTopics]). Nothing without topics.
     */
    private fun matchTopics() {
        topicsJob?.cancel()
        val s = _settings.value
        if (s.topics.isEmpty()) {
            _topicsWorking.value = false
            applyTopics()
            return
        }
        val items = _headlines.value.visibleItems
        topicsJob = viewModelScope.launch {
            _topicsWorking.value = true
            val result = runCatching { withContext(Dispatchers.Default) { s.topics.associate { it.text to wordScores(it.text, items) } } }
            if (result.exceptionOrNull() is CancellationException) return@launch
            topicScores.value = result.getOrDefault(emptyMap())
            _topicsWorking.value = false
            applyTopics()
        }
    }

    /** Picks the feed's rows by the topics on and the threshold, from the scores computed last. */
    private fun applyTopics() {
        val s = _settings.value
        val on = s.topics.filter { it.on }.map { it.text }
        val matches = if (on.isEmpty()) null else topicMatches(topicScores.value, on, s.topicThreshold)
        _headlines.update { it.copy(topicMatches = matches) }
    }

    /** Archives to [folder] (picked by the user) from now on, keeping access to it across restarts. */
    fun chooseArchive(folder: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.takePersistableUriPermission(folder, ARCHIVE_ACCESS)
        _settings.value.archiveFolder?.let(Uri::parse)?.takeIf { it != folder }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, ARCHIVE_ACCESS) }
        }
        updateSettings(_settings.value.copy(archiveFolder = folder.toString()))
        newsApp.useArchive(folder)
        updateStorage()
    }

    /** Stops archiving; the folder and what is in it stay. */
    fun stopArchive() {
        _settings.value.archiveFolder?.let(Uri::parse)?.let { old ->
            runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(old, ARCHIVE_ACCESS) }
        }
        updateSettings(_settings.value.copy(archiveFolder = null))
        newsApp.useArchive(null)
        updateStorage()
    }

    /**
     * Once: the headlines of every article in the archive join the feed's older part ([FeedCache.addHeadlines]), each
     * as its article has it; their articles stay in the archive and open from it. Reads the whole archive, which takes
     * minutes; the feed takes them in as they come.
     */
    fun importArchive() {
        val archive = newsApp.archive ?: return
        if (_archiveImport.value?.running == true) return
        viewModelScope.launch {
            _archiveImport.value = ArchiveImport(read = 0, total = archive.articleCount, added = 0, running = true)
            withContext(Dispatchers.IO) {
                val batch = ArrayList<ArticleSummary>()
                fun flush() {
                    val added = newsApp.feedCache.addHeadlines(batch)
                    batch.clear()
                    _archiveImport.update { it?.copy(added = it.added + added) }
                }
                runCatching {
                    archive.articles().forEach { article ->
                        newsApp.mediaDates.noteArticle(article)
                        batch += ArticleSummary(article.sourceId, article.url, article.title).filledFrom(article)
                        _archiveImport.update { it?.copy(read = it.read + 1) }
                        if (batch.size >= IMPORT_BATCH) flush()
                    }
                }
                flush()
            }
            _archiveImport.update { it?.copy(running = false) }
            loadOlder()
        }
    }

    /** Archives what the caches hold now, rather than waiting for the day's run ([DailyArchive]). */
    fun archiveNow() = DailyArchive.runNow(getApplication())

    /** Clears the feed's cached articles and pictures; the archive stays. */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                newsApp.feedCache.clear()
                SingletonImageLoader.get(getApplication()).run {
                    diskCache?.clear()
                    memoryCache?.clear()
                }
            }
            _downloads.update { it.copy(done = 0) }
            loadOlder()
            updateStorage()
        }
    }

    fun clearBackgroundLog() {
        backgroundLog.clear()
        _backgroundRuns.value = emptyList()
    }

    private fun updateStorage() {
        viewModelScope.launch {
            val archive = newsApp.archive
            _storage.value = withContext(Dispatchers.IO) {
                StorageInfo(
                    feedBytes = newsApp.feedCache.bytes,
                    cachedArticles = newsApp.feedCache.articleCount,
                    imageBytes = NewsApplication.imageCacheDir(getApplication()).walkBottomUp().filter { it.isFile }.sumOf { it.length() },
                    archiveFolder = _settings.value.archiveFolder,
                    archiveAvailable = archive != null,
                    archivedArticles = archive?.articleCount ?: 0,
                )
            }
            // Sizing the archive walks every folder through the document provider, so it comes second.
            if (archive != null) {
                val bytes = withContext(Dispatchers.IO) { runCatching { archive.bytes() }.getOrNull() }
                _storage.update { it.copy(archiveBytes = bytes) }
            }
        }
    }

    /**
     * The server's stories over the visible headlines, unless the current result already covers exactly them. Earlier
     * stories stay on screen while the new ones are asked for; when the server doesn't answer, they stay as they were.
     * [force]: the server regrouped (its grouping changed) though the feed didn't.
     */
    private fun findStories(@Suppress("UNUSED_PARAMETER") quiet: Boolean = false, force: Boolean = false) {
        val items = _headlines.value.visibleItems
        val (text, threshold) = grouping()
        val current = _headlines.value.stories
        if (!force && current is StoriesState.Ready && current.forItems == items && current.threshold == threshold && current.text == text) return
        storiesJob?.cancel()
        storiesJob = viewModelScope.launch {
            val fromServer = newsApp.server.stories(items)
            val ready = when {
                fromServer != null -> StoriesState.Ready(newestFirst(fromServer, items), items, threshold, text = text)
                // The server answers but doesn't group: each headline its own story.
                !newsApp.server.storiesOn && newsApp.server.answering -> ungrouped(items)
                else -> return@launch
            }
            _headlines.update { it.copy(stories = ready) }
            saveFeed()
        }
    }

    /** Each of [items] a story of its own, for a server that doesn't group stories. */
    private fun ungrouped(items: List<ArticleSummary>): StoriesState.Ready {
        val (text, threshold) = grouping()
        return StoriesState.Ready(newestFirst(items.map { Story(listOf(it)) }, items), items, threshold, text = text)
    }

    /** The server's grouping as last heard ([serverGrouping]): what the stories are compared by, and at what similarity. */
    private fun grouping(): Pair<StoryText, Float> =
        (_serverGrouping.value?.text ?: StoryText.TITLE_AND_SUBTITLE) to (_serverGrouping.value?.threshold?.toFloat() ?: 0f)

    /**
     * Opens the reader at [summary], to swipe through the headlines in the order the current view shows them. In
     * Stories the swipes go through the unread stories only ([unreadQueue]).
     */
    fun open(summary: ArticleSummary) {
        val state = _headlines.value
        // A source of a generated story: the story is opened.
        (state.stories as? StoriesState.Ready)?.stories?.firstOrNull { s -> s.reports.any { it.url == summary.url } }
            ?.takeIf { s -> s.reports.any { it.url in state.summaries } }?.let(::markStoryOpened)
        val all = feed.value.readingOrder.ifEmpty { state.readingOrder }
        val opened = all.indexOfFirst { p -> p.any { it.url == summary.url } }
        val pages = if (state.view.groupsStories && opened >= 0) unreadQueue(all, state.read, opened) else all
        val page = pages.indexOfFirst { p -> p.any { it.url == summary.url } }
        _reader.value = if (page < 0) {
            ReaderState(listOf(listOf(summary)), 0, 0)
        } else {
            ReaderState(pages, page, pages[page].indexOfFirst { it.url == summary.url })
        }
    }

    /** Loads [summary]'s article for the reader, unless it is loaded or loading already ([retry]: unless loading). */
    fun load(summary: ArticleSummary, retry: Boolean = false) {
        val url = summary.url
        val known = _articles.value[url]
        if (known is ArticleState.Loading || (known != null && !retry)) return
        _articles.update { it + (url to ArticleState.Loading(summary)) }
        articleJobs[url] = viewModelScope.launch {
            val state = runCatching { repository.article(summary.sourceId, url, userAsked = true) }.fold(
                onSuccess = { ArticleState.Loaded(summary, it) },
                onFailure = { ArticleState.Failed(summary, it.message ?: it.toString()) },
            )
            if (_reader.value != null) _articles.update { it + (url to state) }
            if (state is ArticleState.Loaded) {
                listed = listed.map { if (it.url == url) it.filledFrom(state.article) else it }
                _headlines.update { s -> s.copy(items = s.items.map { if (it.url == url) it.filledFrom(state.article) else it }) }
            }
            articleJobs.remove(url)
        }
    }

    fun markRead(summary: ArticleSummary) {
        readerAt = summary.url
        readAhead(summary)
        if (summary.url in _headlines.value.read) return
        readInReader += summary.url
        // Read is viewed too: it leaves the new ones.
        scrolledPast(listOf(summary.url))
        _headlines.update { it.copy(read = it.read + summary.url) }
        viewModelScope.launch(readDispatcher) { readHistory.add(summary.url) }
        sendReadSoon(listOf(summary.url))
    }

    /**
     * Loads the next [AppSettings.readAhead] unread pages after [summary]'s in the reader ([upcomingUnread]), each
     * page's first report, and then its pictures, so they open at once when the reader gets there.
     */
    private fun readAhead(summary: ArticleSummary) {
        val count = _settings.value.readAhead
        val pages = _reader.value?.pages ?: return
        val page = pages.indexOfFirst { p -> p.any { it.url == summary.url } }
        if (count == 0 || page < 0) return
        upcomingUnread(pages, _headlines.value.read + summary.url, page, count).map { pages[it].first() }.forEach { next ->
            load(next)
            viewModelScope.launch {
                articleJobs[next.url]?.join()
                val article = (_articles.value[next.url] as? ArticleState.Loaded)?.article ?: return@launch
                fetcher.prefetchImages(
                    listOfNotNull(article.imageUrl, next.imageUrl) + article.blocks.filterIsInstance<ArticleBlock.Image>().map { it.url },
                )
            }
        }
    }

    /** Marks every headline in the feed as read (settings), the hidden sites' too. */
    fun markAllRead() {
        val unread = _headlines.value.let { s -> s.items.map { it.url }.filter { it !in s.read } }
        if (unread.isEmpty()) return
        _headlines.update { it.copy(read = it.read + unread) }
        viewModelScope.launch(readDispatcher) { readHistory.addAll(unread) }
        sendReadSoon(unread)
    }

    /**
     * Marks everything in the feed as viewed (settings): no headline is new any more, and every generated story counts
     * as opened, so the חדשים badge goes to 0. Nothing is marked read.
     */
    fun markAllViewed() {
        val s = _headlines.value
        scrolledPast(s.allItems.map { it.url })
        val generated = s.allItems.map { it.url }.filter { it in s.summaries && it !in s.storiesOpened }
        val opened = s.summaries.keys.filter { it !in s.storiesOpened } + generated
        if (opened.isEmpty()) return
        _headlines.update { it.copy(storiesOpened = it.storiesOpened + opened) }
        viewModelScope.launch(readDispatcher) { storiesOpenedHistory.addAll(opened) }
    }

    /** Back in the app: read marks added meanwhile (a notification's "mark as read") join the ones on screen. */
    fun reloadRead() {
        viewModelScope.launch {
            val read = withContext(readDispatcher) { readHistory.reload() }
            _headlines.update { it.copy(read = it.read + read) }
        }
    }

    fun clearReadHistory() {
        _headlines.update { it.copy(read = emptySet()) }
        viewModelScope.launch(readDispatcher) { readHistory.clear() }
    }

    /**
     * The owner viewed [urls]: their row's bottom went up off the screen, or showed at the end of the list. They are
     * no longer new, for good ([viewedHistory]); they keep their place among the new ones, so the list doesn't jump.
     */
    fun scrolledPast(urls: Collection<String>) {
        val fresh = urls.filter { it !in viewed }
        if (fresh.isEmpty()) return
        viewed = viewed + fresh
        viewModelScope.launch(readDispatcher) { viewedHistory.addAll(fresh) }
        val s = _headlines.value
        val past = fresh.filter { it in s.newUrls && it !in s.pastUrls }
        if (past.isNotEmpty()) _headlines.update { it.copy(pastUrls = it.pastUrls + past) }
    }

    /** Downloads [urls] into the picture cache ahead of the scroll ([AppSettings.listAhead]). */
    fun prefetchListImages(urls: Collection<String>) {
        fetcher.prefetchImages(urls)
    }

    /** The list has brought [returnTo] into view. */
    fun returned() {
        _returnTo.value = null
    }

    fun close() {
        articleJobs.values.forEach { it.cancel() }
        articleJobs.clear()
        _reader.value = null
        _returnTo.value = readerAt
        readerAt = null
        _articles.value = emptyMap()
        _justRead.value = readInReader.toSet()
        readInReader.clear()
        viewModelScope.launch {
            delay(JUST_READ_MS)
            _justRead.value = emptySet()
        }
    }

    private companion object {
        const val ARCHIVE_ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** How long marks of articles just read stay ready to animate after the reader closes. */
        const val JUST_READ_MS = 2_000L

        /** How often arriving articles are applied to the feed ([flushFilled]). */
        const val FLUSH_MS = 1_000L

        /** How long a refresh waits for the new headlines' articles before grouping them ([articlesOf]). */
        const val NEW_ARTICLES_MS = 20_000L

        /** How long after a grouping change the server's stories are asked for again: it regroups in a few seconds. */
        const val REGROUP_WAIT_MS = 6_000L

        /** Read marks go to the server this long after the last one ([sendReadSoon]). */
        const val READ_FLUSH_MS = 3_000L

        /** How long a refresh waits for an enrollment to connect ([afterConnecting]). */
        const val ENROLL_WAIT_MS = 120_000L

        /** How long [embedAhead] waits when nothing new was ready to embed. */
        const val AHEAD_MS = 2_000L

        /** How many headlines an archive import adds to the index at a time ([importArchive]). */
        const val IMPORT_BATCH = 200

        /** The viewed headlines' file ([scrolledPast]), and how many of the latest it keeps at least. */
        const val VIEWED_FILE = "viewed.txt"
        const val VIEWED_LIMIT = 20_000

        /** How many older headlines join the feed each time it is scrolled near its end ([showMoreOlder]). */
        const val OLDER_PAGE = 100
    }
}
