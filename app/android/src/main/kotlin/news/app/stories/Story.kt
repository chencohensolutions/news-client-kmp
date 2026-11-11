package news.app.stories

import news.core.model.ArticleSummary
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Several sites' reports of one event; the first, [lead], is shown as the story's main headline. */
data class Story(val reports: List<ArticleSummary>) {
    val lead: ArticleSummary get() = reports.first()

    /** When the story was last reported: its newest report's time, if any report has one. */
    val latest: Instant? get() = reports.mapNotNull { it.publishedAt }.maxOrNull()
}

/**
 * Newest first, inside each story and across them: a story's reports go newest first, so its lead is the newest
 * report, and stories go by that time; untimed reports and stories come last, in their order in [feed].
 */
internal fun newestFirst(stories: List<Story>, feed: List<ArticleSummary>): List<Story> {
    val position = feed.withIndex().associate { (i, item) -> item.url to i }
    val reportOrder = compareByDescending<ArticleSummary, Instant?>(nullsFirst()) { it.publishedAt }
        .thenBy { position[it.url] ?: Int.MAX_VALUE }
    return latestFirst(stories.map { Story(it.reports.sortedWith(reportOrder)) }, feed)
}

/**
 * Those of [items] published in the last [days] days, the ones stories are grouped from; 0: all. One without a time
 * stays: it is in the feed now, and its age is unknown.
 */
internal fun withinDays(items: List<ArticleSummary>, days: Int, now: Instant = Clock.System.now()): List<ArticleSummary> =
    if (days <= 0) items else items.filter { item -> item.publishedAt?.let { now - it <= days.days } ?: true }

/**
 * [stories] by their newest report, newest first (untimed last, in their order in [feed]); their reports as they are.
 * Only the reports [counts] lets through set a story's time: a story shown for one site goes by that site's report.
 */
internal fun latestFirst(stories: List<Story>, feed: List<ArticleSummary>, counts: (ArticleSummary) -> Boolean = { true }): List<Story> {
    val position = feed.withIndex().associate { (i, item) -> item.url to i }
    val time = { story: Story -> story.reports.filter(counts).mapNotNull { it.publishedAt }.maxOrNull() }
    return stories.sortedWith(compareByDescending(nullsFirst(), time).thenBy { position[it.lead.url] ?: Int.MAX_VALUE })
}

