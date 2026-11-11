package news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import news.app.server.ServerThread
import news.app.server.ThreadRef
import news.app.server.ThreadStory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

// Threads: the long affairs the server links its generated stories into (a war, an investigation, coalition talks),
// many to many. From the UI lab's threads round (ux/threads): (1) a line per thread on a generated story's card, (2) the
// thread's page, a timeline by year and month, (3) the thread's earlier and later stories on a story's article page.

/** What is open over the feed from threads: a thread's page, or a story's article opened from one. */
sealed interface ThreadLayer {
    /** [ref]'s page; [currentId]: the story it was opened from, highlighted on the timeline. */
    data class Page(val ref: ThreadRef, val currentId: String?) : ThreadLayer

    /** Every thread, latest updated first ([threads]: null while it loads). */
    data object All : ThreadLayer

    /** The threads map: how the threads meet, over time. */
    data object Map : ThreadLayer

    /**
     * A story opened from a thread: the feed's, or, for one the feed no longer has, made from the thread's copy
     * ([summary], its reports from its footnotes).
     */
    data class Article(val story: news.app.stories.Story, val summary: news.app.server.StorySummary? = null) : ThreadLayer
}

/**
 * A thread's story the feed doesn't have, as an article page's story and its generated article: its reports are its
 * footnotes' articles, newest first; [thread] (the thread it was opened from) its thread. Null without footnotes.
 */
internal fun threadStoryPage(story: news.app.server.ThreadStory, thread: ThreadRef?): Pair<news.app.stories.Story, news.app.server.StorySummary>? {
    val reports = story.sentences.flatMap { it.footnotes }.groupBy { it.url }.values
        .map { notes ->
            val note = notes.first()
            news.core.model.ArticleSummary(
                sourceId = notes.firstNotNullOfOrNull { it.sourceId }.orEmpty(),
                url = note.url,
                title = notes.firstNotNullOfOrNull { it.title }.orEmpty(),
                publishedAt = notes.firstNotNullOfOrNull { it.publishedAt },
                video = notes.any { it.video },
            )
        }
        .sortedWith(compareBy(nullsLast(reverseOrder())) { it.publishedAt })
    if (reports.isEmpty()) return null
    val summary = news.app.server.StorySummary(
        title = story.title,
        sentences = story.sentences,
        writtenAt = story.writtenAt,
        id = story.id,
        threads = listOfNotNull(thread),
    )
    return news.app.stories.Story(reports) to summary
}

/** Which sentence a related story shows under its title (settings): whole, never cut. */
enum class RelatedLine(val title: String) { MOST_CITED("המשפט המצוטט ביותר"), FIRST("המשפט הראשון"), NONE("ללא") }

/** A story's preview: its sentence with the most sources (the first on a tie), its first, or none; whole, never cut. */
internal fun ThreadStory.lead(line: RelatedLine = RelatedLine.MOST_CITED): String? = when (line) {
    RelatedLine.MOST_CITED -> sentences.maxByOrNull { it.footnotes.size }?.text
    RelatedLine.FIRST -> sentences.firstOrNull()?.text
    RelatedLine.NONE -> null
}

/** When a thread's story began: its earliest report's publishing time, else when the server first wrote it. */
internal val ThreadStory.startedAt: Instant
    get() = firstReportAt ?: sentences.flatMap { it.footnotes }.mapNotNull { it.publishedAt }.minOrNull()?.takeIf { it < firstWrittenAt } ?: firstWrittenAt

/**
 * A story's time on the timeline, under its day's label: "22:40"; its span when its last report came 30 minutes or more
 * later, "22:40–23:15" the same day, "22:40 → אתמול 09:15", "→ יום ה׳ 09:15" or "→ 12.11" on a later one.
 */
internal fun spanOf(story: ThreadStory, today: java.time.LocalDate): String {
    val start = story.startedAt
    val end = story.lastReportAt?.takeIf { it - start >= 30.minutes } ?: return timeOf(start)
    val day = localDate(start)
    val endDay = localDate(end)
    return when {
        endDay == day -> "${timeOf(start)}–${timeOf(end)}"
        endDay == today.minusDays(1) || endDay == today -> "${timeOf(start)} → ${dayName(endDay, today)} ${timeOf(end)}"
        java.time.temporal.ChronoUnit.DAYS.between(day, endDay) < 7 ->
            "${timeOf(start)} → ${endDay.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("he"))} ${timeOf(end)}"
        else -> "${timeOf(start)} → ${endDay.dayOfMonth}.${endDay.monthValue}"
    }
}

private fun localDate(at: Instant): LocalDate =
    java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(ZoneId.systemDefault()).toLocalDate()

/** "היום", "אתמול", else "יום רביעי, 11 בנובמבר": a day's label on the thread's timeline. */
internal fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "היום"
    today.minusDays(1) -> "אתמול"
    else -> "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("he"))}, ${d.dayOfMonth} ב${monthName(d)}"
}

/** "14:05", in the phone's time zone. */
internal fun timeOf(at: Instant): String =
    java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

/** A day over its stories on the timeline: a small pill, as a chat's date. */
@Composable
internal fun DayLabel(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

private fun monthName(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("he"))

/** (1) "חלק מ־<name> · N סיפורים מאז <month>": which thread a story is part of; a tap opens the thread. */
@Composable
fun ThreadLine(thread: ThreadRef, onClick: () -> Unit) {
    val first = localDate(thread.startedAt)
    val count = if (thread.storyCount == 1) "סיפור אחד" else "${thread.storyCount} סיפורים"
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timeline, null, Modifier.size(18.dp))
            // The name on its line, the details under it, smaller.
            Column(Modifier.weight(1f)) {
                Text(thread.name, style = MaterialTheme.typography.labelLarge)
                Text("$count מאז ${monthName(first)} ${first.year}", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** The lines of a story's threads, one each (a story can be part of several). */
@Composable
fun ThreadLines(threads: List<ThreadRef>, onThread: (ThreadRef) -> Unit, modifier: Modifier = Modifier) {
    if (threads.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { threads.forEach { t -> ThreadLine(t) { onThread(t) } } }
}

/**
 * (2) A thread's page: its name, "עד כה" (the story so far), then a timeline under year and month headings, each point
 * a story (date, title, its whole text); newest on top or from the start. A story of the feed opens its article
 * ([onStory]). Then the related threads.
 */
@Composable
fun ThreadScreen(
    ref: ThreadRef,
    page: ServerThread?,
    currentId: String?,
    onStory: (ThreadStory) -> Boolean,
    onThread: (ThreadRef) -> Unit,
    onBack: () -> Unit,
) {
    var fromStart by rememberSaveable(ref.id) { mutableStateOf(false) }
    AppLayout {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    val count = page?.stories?.size ?: ref.storyCount
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                        Text("פרשה · ${if (count == 1) "סיפור אחד" else "$count סיפורים"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(ref.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    if (page == null) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        return@Column
                    }
                    if (page.summary.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp))
                                    Text("עד כה", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(page.summary, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!fromStart, { fromStart = false }, label = { Text("מהחדש") })
                        FilterChip(fromStart, { fromStart = true }, label = { Text("מההתחלה") })
                    }
                    val stories = page.stories.sortedBy { it.startedAt }.let { if (fromStart) it else it.reversed() }
                    // Grouped by day: a year's heading when it changes (or isn't this one), a day's label once over its stories,
                    // and each story its time.
                    val today = LocalDate.now()
                    val years = stories.map { localDate(it.startedAt).year }.distinct()
                    var lastYear = -1
                    var lastDay: LocalDate? = null
                    stories.forEachIndexed { i, story ->
                        val date = localDate(story.startedAt)
                        if (date.year != lastYear && (years.size > 1 || date.year != today.year)) {
                            Text("${date.year}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        lastYear = date.year
                        if (date != lastDay) {
                            DayLabel(dayName(date, today), Modifier.padding(top = if (lastDay == null) 4.dp else 10.dp, bottom = 8.dp))
                            lastDay = date
                        }
                        val now = story.id == currentId
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable {
                                onStory(story)
                            },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.padding(top = 4.dp).size(if (now) 14.dp else 10.dp).clip(CircleShape)
                                        .background(if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                )
                                if (i < stories.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                            }
                            Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
                                // When it began, to its last report when later; a tiny ✎ only when rewritten well after that.
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(spanOf(story, today), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    val reported = story.lastReportAt ?: story.startedAt
                                    if (story.writtenAt - reported > 30.minutes && localDate(story.writtenAt) == localDate(reported)) {
                                        Text("✎ ${timeOf(story.writtenAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(story.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal)
                                // The story's whole generated text.
                                Text(story.sentences.joinToString(" ") { it.text }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (page.related.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("פרשות קשורות", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        ThreadLines(page.related, onThread)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * (3) The end of a story's article page: "סיפורים קשורים", the lines of its threads, then in the first thread with a
 * page what came after this story and what before it.
 */
@Composable
fun ThreadRelated(
    currentId: String,
    threads: List<ThreadRef>,
    pages: Map<String, ServerThread>,
    onThread: (ThreadRef) -> Unit,
    onStory: (ThreadStory) -> Boolean,
    line: RelatedLine = RelatedLine.MOST_CITED,
) {
    if (threads.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("סיפורים קשורים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ThreadLines(threads, onThread)
        val page = threads.firstNotNullOfOrNull { pages[it.id] } ?: return@Column
        val stories = page.stories.sortedBy { it.startedAt }
        val at = stories.indexOfFirst { it.id == currentId }
        if (at < 0) return@Column
        val after = stories.drop(at + 1)
        val before = stories.subList(0, at).takeLast(3).reversed()
        val today = LocalDate.now()
        // A day's label once over its stories, then each with its time.
        val byDay = @Composable { list: List<ThreadStory> ->
            var lastDay: LocalDate? = null
            list.forEach { s ->
                val d = localDate(s.startedAt)
                if (d != lastDay) {
                    DayLabel(dayName(d, today))
                    lastDay = d
                }
                RelatedRow(s, onStory, line)
            }
        }
        if (after.isNotEmpty()) {
            Text("אחר כך", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            byDay(after)
        }
        if (before.isNotEmpty()) {
            Text("קודם בפרשה", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            byDay(before)
        }
    }
}

@Composable
private fun RelatedRow(story: ThreadStory, onStory: (ThreadStory) -> Boolean, line: RelatedLine) {
    Row(Modifier.fillMaxWidth().clickable { onStory(story) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(timeOf(story.startedAt), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f)) {
            Text(story.title, style = MaterialTheme.typography.bodyLarge)
            story.lead(line)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Every thread the server has linked, latest updated first; a tap opens its page, a pull fetches them again. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreen(threads: List<ThreadRef>?, refreshing: Boolean, onRefresh: () -> Unit, onThread: (ThreadRef) -> Unit, onBack: () -> Unit) {
    AppLayout {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                    Text("פרשות", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    threads?.let {
                        Text("  · ${it.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when {
                            threads == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            threads.isEmpty() -> Text(
                                "עוד אין פרשות. השרת מקשר את הסיפורים לפרשות פעם ביום.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp),
                            )
                            else -> threads.forEach { t ->
                                ThreadRow(t) { onThread(t) }
                                HorizontalDivider()
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** A thread in the list: its name, how many stories since when, its last update, and the start of "עד כה". */
@Composable
private fun ThreadRow(thread: ThreadRef, onClick: () -> Unit) {
    val first = localDate(thread.startedAt)
    val count = if (thread.storyCount == 1) "סיפור אחד" else "${thread.storyCount} סיפורים"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(thread.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "$count · מאז ${monthName(first)} ${first.year}" + (ago(thread.updatedAt)?.let { " · עודכן $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (thread.summary.isNotBlank()) {
                Text(thread.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.padding(top = 2.dp))
    }
}
