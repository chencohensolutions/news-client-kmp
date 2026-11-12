package news.app

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.remember
import news.core.model.ArticleBlock
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.stories.Story
import java.util.Date
import kotlin.time.Duration.Companion.hours

// The news server's generated articles (news-server-go, Gemini): Hebrew, facts only, each fact once, every sentence with
// footnotes, the articles it comes from; where the sites state different facts, a sentence each saying which says what.
// From the UI lab's summaries round (docs/ux-history/summaries/).
//
// - The stories feed: a story with a generated article shows it in its card ([GeneratedPart]).
// - A card opens the article as a page ([SummaryArticleScreen]): the sites inline in the text, the disagreements, the
//   pictures and videos of its articles, the sources by time.
// A footnote's tap opens its article in the reader.

/**
 * A story card's generated article, in place of its lead's title and subtitle, in the card's own design: "✦ כתבה
 * מ־N אתרים" (and ⚖ when the sites disagree, which opens the article page), the written title (a tap opens the page)
 * and every fact with its sites as footnotes.
 * [original]: the lead's own title and subtitle instead. The ⇄ chip flips this card to the other one.
 */
@Composable
internal fun GeneratedPart(
    summary: StorySummary,
    sites: Int,
    lead: news.core.model.ArticleSummary,
    names: Map<String, String>,
    original: Boolean,
    onFlip: () -> Unit,
    onArticle: () -> Unit,
    onFootnote: (Footnote) -> Unit = {},
    labels: SourceLabels = SourceLabels.BELOW,
    /** Each sentence on a line of its own (after its labels, inline), not one flowing paragraph. */
    sentenceLines: Boolean = false,
    /** A thread's line tapped: its page, this story highlighted. */
    onThread: (news.app.server.ThreadRef, String) -> Unit = { _, _ -> },
    /** "חדש" or "דיווח חדש", first on the "✦ כתבה מ־N אתרים" line; null for none. */
    badge: String? = null,
    /**
     * Only this many of its sentences ([AppSettings.feedSentences]), then "להמשך הכתבה", which opens the page, and no
     * disagreements (the ⚖ chip still says there are); 0: all.
     */
    maxSentences: Int = 0,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!original) {
                badge?.let { NewBadge(it) }
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (sites == 1) "כתבה מאתר אחד" else "כתבה מ־$sites אתרים", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (summary.contradictions.isNotEmpty()) DisagreeChip(onArticle)
            } else {
                Text("כותרת המקור · ${names[lead.sourceId] ?: lead.sourceId}", style = MaterialTheme.typography.labelMedium, color = muted)
            }
            Spacer(Modifier.weight(1f))
            FlipChip(if (original) "לכתבה המסוכמת" else "למקור", onFlip)
        }
        if (original) {
            Text(lead.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            (lead.subtitle ?: lead.summary)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        } else {
            // The whole article as clean text, then what the sites disagree on, then its sources once each.
            Text(summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onArticle))
            val cut = maxSentences > 0 && summary.sentences.size > maxSentences
            val sentences = if (cut) summary.sentences.take(maxSentences) else summary.sentences
            val contradictions = if (cut) emptyList() else summary.contradictions
            val body = MaterialTheme.typography.bodyLarge
            when (labels) {
                SourceLabels.BELOW -> {
                    val gap = if (sentenceLines) "\n" else " "
                    Text(sentences.joinToString(gap) { it.text }, style = body)
                    if (contradictions.isNotEmpty()) Disagreements(contradictions) { Text(it.text, style = body) }
                    // One label a site: its first article, with the film icon when any of its articles has video.
                    val bySite = numbering(summary.sentences + summary.contradictions).groupBy { it.sourceId ?: it.url }.values
                        .map { notes -> notes.first().copy(video = notes.any { it.video }) }
                    SourcesRow(bySite, names, onFootnote)
                }
                SourceLabels.PER_SENTENCE -> {
                    sentences.forEach { Fact(it, names, onFootnote) }
                    if (contradictions.isNotEmpty()) Disagreements(contradictions) { Fact(it, names, onFootnote) }
                }
                SourceLabels.INLINE -> {
                    val inline = body.copy(lineHeight = 28.sp)
                    Inline(sentences, names, onFootnote, inline, lines = sentenceLines)
                    if (contradictions.isNotEmpty()) Disagreements(contradictions) { Inline(listOf(it), names, onFootnote, inline) }
                }
            }
            if (cut) {
                Text(
                    (summary.sentences.size - maxSentences).let { left -> "להמשך הכתבה · " + if (left == 1) "עוד משפט אחד" else "עוד $left משפטים" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onArticle).padding(vertical = 2.dp),
                )
            }
            ThreadLines(summary.threads, { onThread(it, summary.id) }, Modifier.padding(top = 6.dp))
        }
    }
}

/** ⇄ and where it goes: one story to the other view than the feed's switch. */
@Composable
private fun FlipChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** A story's generated article as a page: numbered footnotes in the text, the disagreements, the sources by number. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SummaryArticleScreen(
    story: Story,
    summary: StorySummary,
    names: Map<String, String>,
    onFootnote: (Footnote) -> Unit,
    onBack: () -> Unit,
    /** The articles of the story's reports, as they come in: its pictures and videos, each with its site. */
    articles: Map<String, news.core.model.Article> = emptyMap(),
    /** The end of the page: its threads and their earlier and later stories ([ThreadRelated]). */
    related: @Composable () -> Unit = {},
) {
    val numbered = numbering(summary.sentences + summary.contradictions)
    val context = LocalContext.current
    val sites = citedSites(story, summary)
    val media = remember(story, articles, summary.unrelated) { storyMedia(story, summary, articles) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppLayout {
        // The reader's frame: its top bar (back, share), the picture across, the title in its style.
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("כתבה מסוכמת", maxLines = 1)
                            }
                            Text(
                                if (sites.size == 1) "מאתר אחד" else "מ־${sites.size} אתרים",
                                style = MaterialTheme.typography.labelMedium,
                                color = muted,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val text = summary.title + "\n\n" + summary.sentences.joinToString(" ") { it.text }
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        }) { Icon(Icons.Filled.Share, "שיתוף") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                story.reports.firstNotNullOfOrNull { it.imageUrl }?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(summary.title, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp), fontWeight = FontWeight.Bold)
                    Text(
                        "נכתב ${ago(summary.writtenAt)} מ־${story.reports.count { it.url !in summary.unrelated }} כתבות · עובדות בלבד",
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                    // The settings it was written with, quietly.
                    Text(
                        summary.writtenWith?.let { w ->
                            val length = when (w.length) { "SHORT" -> "קצר"; "MEDIUM" -> "בינוני"; "LONG" -> "ארוך"; "FULL" -> "מלא"; else -> w.length }
                            "אורך: $length · " + if (w.fullArticles) "כתבות מלאות" else "600 תווים ראשונים"
                        } ?: "נכתב לפני שמירת ההגדרות",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    // The sites' labels in the text after each sentence, as the feed's inline labels.
                    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 32.sp)
                    summary.sentences.forEach { Inline(listOf(it), names, onFootnote, body) }
                    if (summary.contradictions.isNotEmpty()) {
                        Disagreements(summary.contradictions) { Inline(listOf(it), names, onFootnote, MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)) }
                    }
                }
                if (media.isNotEmpty()) StoryMedia(media, names, onFootnote)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    // The sources are the articles the text cites. The story's sites it doesn't cite (their articles read
                    // without a fact taken from them, or not read) go in one quiet line under them.
                    val citingSites = numbered.mapNotNullTo(HashSet()) { it.sourceId }
                    val unread = story.reports.filter { r -> r.url !in summary.unrelated && r.sourceId !in citingSites && numbered.none { it.url == r.url } }
                    Sources(numbered, names, onFootnote, { url -> story.reports.firstOrNull { it.url == url }?.publishedAt }, summary.headlineOnly.toSet())
                    if (unread.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("בסיפור גם:", style = MaterialTheme.typography.labelMedium, color = muted)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                unread.distinctBy { it.sourceId }.forEach { r ->
                                    SitePill(r.sourceId, shortSiteName(r.sourceId, names), onClick = { onFootnote(Footnote(r.url, r.sourceId, r.title, r.video, r.publishedAt)) }, video = r.video)
                                }
                            }
                        }
                    }
                    related()
                    // Grouped into this story but about another event: not in the article, listed apart.
                    val offTopic = story.reports.filter { it.url in summary.unrelated }
                    if (offTopic.isNotEmpty()) {
                        Text("לא קשור לסיפור", style = MaterialTheme.typography.titleSmall, color = muted, modifier = Modifier.padding(top = 8.dp))
                        offTopic.forEach { report ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onFootnote(Footnote(report.url, report.sourceId, report.title)) }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                SitePill(report.sourceId, shortSiteName(report.sourceId, names))
                                Text(report.title, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The sites the text cites (not those about another event); all of the story's when it cites none. */
internal fun citedSites(story: Story, summary: StorySummary): List<String> {
    val cited = (summary.sentences + summary.contradictions).flatMap { it.footnotes }.filter { it.url !in summary.unrelated }
        .mapNotNull { it.sourceId ?: story.reports.firstOrNull { r -> r.url == it.url }?.sourceId }.distinct()
    return cited.ifEmpty { story.reports.filter { it.url !in summary.unrelated }.map { it.sourceId }.distinct() }
}

/**
 * [story]'s reports Gemini read ([StorySummary.reports]; for a story written before the server said, the ones its text
 * cites) and the rest of them, unread; neither has those about another event.
 */
private fun readAndUnread(story: Story, summary: StorySummary): Pair<List<news.core.model.ArticleSummary>, List<news.core.model.ArticleSummary>> {
    val related = story.reports.filter { it.url !in summary.unrelated }
    val read = summary.reports.toSet().ifEmpty { (summary.sentences + summary.contradictions).flatMap { it.footnotes }.mapTo(HashSet()) { it.url } }
    return related.partition { it.url in read }
}

/** A picture or video of one of a story's articles, and the article it is from. */
private class StoryMediaItem(val article: news.core.model.Article, val image: ArticleBlock.Image?, val video: news.core.model.ArticleVideo?)

/**
 * The pictures and videos of [story]'s articles (not those about another event), the lead's first, each once: a picture
 * by its file ([photoKey], else its URL), across the articles too; the top picture of the page is left out.
 */
private fun storyMedia(story: Story, summary: StorySummary, articles: Map<String, news.core.model.Article>): List<StoryMediaItem> {
    val top = story.reports.firstNotNullOfOrNull { it.imageUrl }
    val seen = HashSet<String>()
    top?.let { seen += photoKey(it) ?: it }
    val items = ArrayList<StoryMediaItem>()
    // The reports read first, then the story's others: each picture's site is in the sources or the "בסיפור גם" line.
    val (read, unread) = readAndUnread(story, summary)
    for (report in read + unread) {
        val article = articles[report.url] ?: continue
        article.video?.let { v ->
            val key = v.stream ?: v.embed ?: v.poster ?: article.url
            if (seen.add("video:$key")) items += StoryMediaItem(article, null, v)
        }
        val images = listOfNotNull(article.imageUrl?.let { ArticleBlock.Image(it) }) + article.blocks.filterIsInstance<ArticleBlock.Image>()
        for (image in images) {
            if (seen.add(photoKey(image.url) ?: image.url)) items += StoryMediaItem(article, image, null)
        }
    }
    return items
}

/** The story's pictures and videos, after its text: each across the page, its caption and its site under it. */
@Composable
private fun StoryMedia(media: List<StoryMediaItem>, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "תמונות ווידאו מהכתבות",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        media.forEach { item ->
            val open = { onFootnote(Footnote(item.article.url, item.article.sourceId, item.article.title, video = item.video != null)) }
            Column(Modifier.fillMaxWidth()) {
                if (item.video != null) {
                    VideoCard(item.video, item.article, fallbackPoster = item.article.imageUrl)
                } else if (item.image != null) {
                    AsyncImage(
                        model = item.image.url,
                        contentDescription = item.image.caption,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = open),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SitePill(item.article.sourceId, shortSiteName(item.article.sourceId, names), video = item.video != null)
                    Text(
                        item.image?.caption ?: item.article.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "⚖ האתרים חלוקים": the sites state different facts about the story; [onClick] shows where. */
@Composable
internal fun DisagreeChip(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Balance, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Text("האתרים חלוקים", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun Disagreements(sentences: List<Sentence>, sentence: @Composable (Sentence) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("מה שונה בין האתרים", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            sentences.forEach { sentence(it) }
        }
    }
}

/** A footnote's label: its site's short name, or "מקור" when the server no longer knows it. */
private fun label(note: Footnote, names: Map<String, String>) = note.sourceId?.let { shortSiteName(it, names) } ?: "מקור"

/** A sentence and, under it, a label per source: the site's name, which opens that site's article. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Fact(sentence: Sentence, names: Map<String, String>, onFootnote: (Footnote) -> Unit, bullet: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text((if (bullet) "• " else "") + sentence.text, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sentence.footnotes.forEach { note -> FootnotePill(note, names, onFootnote) }
        }
    }
}

/** Where a generated card shows its sources' labels (settings). */
enum class SourceLabels(val title: String) {
    /** One row of labels under the article, each source once. */
    BELOW("בשורה מתחת לכתבה"),
    /** Under each sentence, its own labels. */
    PER_SENTENCE("מתחת לכל משפט"),
    /** In the text, right after each sentence, on the same line. */
    INLINE("בתוך הטקסט"),
}

/** The article's sources, one label each (a film icon by those with video); a tap opens that source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesRow(notes: List<Footnote>, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    if (notes.isEmpty()) return
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.forEach { FootnotePill(it, names, onFootnote) }
    }
}

/** A footnote's site label, with a film icon when its article has a video; a tap opens the article. */
@Composable
private fun FootnotePill(note: Footnote, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    SitePill(note.sourceId.orEmpty(), label(note, names), onClick = { onFootnote(note) }, video = note.video)
}

/** Each source's footnote number, by first use. */
private fun numbering(sentences: List<Sentence>): List<Footnote> = sentences.flatMap { it.footnotes }.distinctBy { it.url }

/**
 * The sources by number, newest first by when each was published ([publishedAt] when the footnote doesn't say): a
 * day's label once over its sources, then each with its time, number, site and headline; a tap opens it.
 */
@Composable
private fun Sources(
    numbered: List<Footnote>,
    names: Map<String, String>,
    onFootnote: (Footnote) -> Unit,
    publishedAt: (String) -> kotlin.time.Instant? = { null },
    /** Those read only by title and subtitle: a quiet "כותרת בלבד" under them. */
    headlineOnly: Set<String> = emptySet(),
) {
    Text("מקורות", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val today = java.time.LocalDate.now()
    val zone = java.time.ZoneId.systemDefault()
    val timed = numbered.map { note -> note to (note.publishedAt ?: publishedAt(note.url)) }
        .sortedWith(compareBy(nullsLast(reverseOrder())) { it.second })
    var lastDay: java.time.LocalDate? = null
    timed.forEach { (note, at) ->
        val day = at?.let { java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()).atZone(zone).toLocalDate() }
        if (day != null && day != lastDay) {
            DayLabel(dayName(day, today), Modifier.padding(top = 6.dp))
            lastDay = day
        }
        Row(
            Modifier.fillMaxWidth().clickable { onFootnote(note) }.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // The time, the site's label under it, in one column; the headline beside them.
            Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    at?.let(::timeOf) ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                FootnotePill(note, names, onFootnote)
                if (note.url in headlineOnly) {
                    Text("כותרת בלבד", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(note.title ?: note.url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

/** [sentences] as one paragraph, each followed by its sources' labels, in the text. */
@Composable
private fun Inline(sentences: List<Sentence>, names: Map<String, String>, onFootnote: (Footnote) -> Unit, style: TextStyle, modifier: Modifier = Modifier, lines: Boolean = false) {
    val inline = HashMap<String, InlineTextContent>()
    // Each label's room in the line is its measured width (the pill's text, its padding, the film icon), so the labels
    // sit close together, a hair space apart.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pillStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val text = buildAnnotatedString {
        sentences.forEachIndexed { i, sentence ->
            if (i > 0) append(if (lines) "\n" else " ")
            append(sentence.text)
            sentence.footnotes.forEachIndexed { j, note ->
                val name = label(note, names)
                val id = "${note.url}#$i"
                append(if (j == 0) "\u2009" else "\u200A")
                appendInlineContent(id, name)
                val width = with(density) {
                    (measurer.measure(name, pillStyle, maxLines = 1).size.width + (12 + if (note.video) 17 else 0).dp.roundToPx()).toSp()
                }
                inline[id] = InlineTextContent(Placeholder(width, 18.sp, PlaceholderVerticalAlign.TextCenter)) {
                    FootnotePill(note, names, onFootnote)
                }
            }
        }
    }
    Text(text, style = style, inlineContent = inline, modifier = modifier)
}

/**
 * The generated stories still to read, the חדשים badge with the server's summaries: a story with a generated article
 * is unread until one of its reports was opened ([opened]: the URLs of every story opened, its card, its article page or
 * a source), and again when a site joins it that none of the opened reports came from (a new site, not a rewrite).
 * Only stories with a report in the last [withinHours] hours count (0: all), as "חדש עד" says.
 */
internal fun unreadGenerated(
    stories: List<Story>,
    summaries: Map<String, StorySummary>,
    opened: Set<String>,
    withinHours: Int,
    now: kotlin.time.Instant = kotlin.time.Clock.System.now(),
): List<Story> = stories.filter { story ->
    val reports = story.reports
    if (reports.none { it.url in summaries }) return@filter false
    if (withinHours > 0 && reports.none { r -> r.publishedAt?.let { now - it <= withinHours.hours } ?: false }) return@filter false
    val seenSites = reports.filter { it.url in opened }.mapTo(HashSet()) { it.sourceId }
    seenSites.isEmpty() || reports.any { it.sourceId !in seenSites }
}
