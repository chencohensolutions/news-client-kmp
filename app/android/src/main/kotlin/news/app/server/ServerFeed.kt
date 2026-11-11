package news.app.server

import kotlinx.serialization.Serializable
import news.app.stories.Story
import news.core.model.ArticleSummary
import news.core.model.InstantIsoSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A headline as the server answers it: the app's [ArticleSummary] and when the server first and last saw it on its site. */
@Serializable
data class ServerHeadline(
    val summary: ArticleSummary,
    @Serializable(with = InstantIsoSerializer::class) val firstSeen: Instant,
    @Serializable(with = InstantIsoSerializer::class) val lastSeen: Instant,
)

/**
 * The server's headlines as the phone keeps them between refreshes, so each refresh asks only for what changed
 * (`headlines(since)`), and the feed the app shows from them. Pure: the store reads and writes [rows].
 */
object ServerFeed {

    /**
     * A site's headlines seen this long before its newest sighting still count as on its page: the server reads a
     * site's pages one after another, so one read's sightings spread over a few minutes.
     */
    val ON_PAGE: Duration = 15.minutes

    /** Headlines not seen for this long are forgotten: the feed's older part is kept by the phone's cache, not here. */
    val KEEP: Duration = 3.days

    /** How far back the first ask goes, with nothing kept yet: the sites' pages of about the last day. */
    val FIRST_ASK: Duration = 1.days

    /** [known] with [fresh] in: a headline seen again, or filled in from its article, replaces the one kept. */
    fun merge(known: List<ServerHeadline>, fresh: List<ServerHeadline>): List<ServerHeadline> {
        val byUrl = LinkedHashMap<String, ServerHeadline>()
        known.forEach { byUrl[it.summary.url] = it }
        fresh.forEach { row -> byUrl[row.summary.url] = row.copy(firstSeen = minOf(row.firstSeen, byUrl[row.summary.url]?.firstSeen ?: row.firstSeen)) }
        return byUrl.values.toList()
    }

    /** [rows] without those last seen more than [KEEP] before [now]. */
    fun prune(rows: List<ServerHeadline>, now: Instant): List<ServerHeadline> = rows.filter { now - it.lastSeen <= KEEP }

    /** What to ask for next: the newest sighting kept (the server answers what it saw at or after it); null: [FIRST_ASK]. */
    fun since(rows: List<ServerHeadline>): Instant? = rows.maxOfOrNull { it.lastSeen }

    /**
     * The feed: each site's headlines on its page at the server's last read of it (seen within [ON_PAGE] of the
     * site's newest sighting), newest first by when the server first saw them, the sites interleaved, one headline
     * of each in turn. [skip]: hidden sites.
     */
    fun current(rows: List<ServerHeadline>, skip: Set<String> = emptySet()): List<ArticleSummary> {
        val perSite = rows.filter { it.summary.sourceId !in skip }.groupBy { it.summary.sourceId }.toSortedMap().values.map { site ->
            val newest = site.maxOf { it.lastSeen }
            site.filter { newest - it.lastSeen <= ON_PAGE }
                .sortedWith(compareByDescending<ServerHeadline> { it.firstSeen }.thenByDescending { it.summary.publishedAt })
                .map { it.summary }
        }
        val longest = perSite.maxOfOrNull { it.size } ?: 0
        return buildList { for (i in 0 until longest) perSite.forEach { list -> list.getOrNull(i)?.let(::add) } }
    }

    /** When the server last read the sites: its newest sighting. */
    fun lastRead(rows: List<ServerHeadline>): Instant? = since(rows)

    /**
     * The server's stories ([groups]: each story's report URLs, as `stories` answers them) over the feed's [items]:
     * reports the feed doesn't show are left out, and so is a story left with fewer than two sites, as the phone's
     * own grouping keeps only those ([news.app.stories.StoryFinder.find]).
     */
    /**
     * The generated articles of the server's [stories] for the feed's [shown] stories ([stories]), by every URL of a
     * shown story's reports, so the feed finds a story's article by any of them. Stories not written yet have none.
     */
    fun summaries(stories: List<ServerStory>, shown: List<Story>): Map<String, StorySummary> {
        val byUrl = HashMap<String, StorySummary>()
        stories.forEach { story -> story.summary?.let { summary -> story.urls.forEach { byUrl[it] = summary } } }
        val out = HashMap<String, StorySummary>()
        shown.forEach { story ->
            val summary = story.reports.firstNotNullOfOrNull { byUrl[it.url] } ?: return@forEach
            story.reports.forEach { out[it.url] = summary }
        }
        return out
    }

    fun stories(groups: List<List<String>>, items: List<ArticleSummary>): List<Story> {
        val byUrl = items.associateBy { it.url }
        return groups.mapNotNull { urls ->
            val reports = urls.distinct().mapNotNull(byUrl::get)
            Story(reports).takeIf { reports.mapTo(HashSet()) { it.sourceId }.size >= 2 }
        }
    }
}
