package news.app.lab

import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.stories.Story
import news.core.model.ArticleSummary
import kotlin.time.Instant

// Made-up generated articles and a digest for PreviewData's stories, in the news server's shape (StorySummary), for
// the lab's summaries screen. The sentences say only what the stories' own headlines and summaries say; the
// disagreement in the ferry story is made up, to show how one looks.

/** A sentence and the URLs of the articles it comes from. */
internal class SampleFact(val text: String, val sources: List<String>)

internal class SampleSummary(
    val story: Story,
    val title: String,
    val sentences: List<SampleFact>,
    val contradictions: List<SampleFact>,
    val writtenAt: Instant,
)

internal object SummariesSample {

    private fun minutesAgo(m: Int) = Instant.fromEpochMilliseconds(System.currentTimeMillis() - m * 60_000L)

    private val ferry = PreviewData.stories[0].reports
    private val heat = PreviewData.stories[1].reports
    private val library = PreviewData.stories[2].reports
    private val robots = PreviewData.stories[3].reports

    private fun urls(reports: List<ArticleSummary>, vararg sites: String) = sites.map { site -> reports.first { it.sourceId == site }.url }

    val stories: List<SampleSummary> get() = listOf(
        SampleSummary(
            PreviewData.stories[1],
            "גל חום: עד 38 מעלות בסוף השבוע",
            listOf(
                SampleFact("טמפרטורות של עד 38 מעלות צפויות בסוף השבוע.", urls(heat, "valley", "meridian")),
                SampleFact("השירות המטאורולוגי ממליץ להימנע ממאמץ בשעות הצהריים.", urls(heat, "valley")),
                SampleFact("עומס החום הכבד ביותר צפוי בעמקים.", urls(heat, "meridian")),
            ),
            emptyList(),
            minutesAgo(3),
        ),
        SampleSummary(
            PreviewData.stories[0],
            "מעבורת חדשה תחבר בין הנמלים בצפון",
            listOf(
                SampleFact("קו מעבורת חדש יחבר בין שני הנמלים בצפון.", urls(ferry, "harbor", "summit", "lantern")),
                SampleFact("ההפלגה הראשונה תצא בחודש הבא.", urls(ferry, "summit")),
                SampleFact("השיט יימשך כ-40 דקות, שש פעמים ביום.", urls(ferry, "harbor")),
            ),
            listOf(
                SampleFact("חדשות הנמל כותב ששש הפלגות ייצאו כל יום; הפסגה כותב על הפלגה ראשונה בחודש הבא, בלי לומר כמה ביום.", urls(ferry, "harbor", "summit")),
            ),
            minutesAgo(7),
        ),
        SampleSummary(
            PreviewData.stories[2],
            "הספרייה העירונית תיפתח גם בלילות",
            listOf(
                SampleFact("הספרייה העירונית תהיה פתוחה גם בשעות הלילה, בניסוי עד חצות בימי חמישי.", urls(library, "harbor", "valley")),
            ),
            emptyList(),
            minutesAgo(12),
        ),
        SampleSummary(
            PreviewData.stories[3],
            "רובוטים יחלקו חבילות ברחובות העיר",
            listOf(
                SampleFact("רובוט השליחויות הראשון יצא לרחובות העיר.", urls(robots, "summit", "meridian")),
                SampleFact("בניסוי יחלקו רובוטים חבילות בשכונות.", urls(robots, "meridian")),
            ),
            emptyList(),
            minutesAgo(20),
        ),
    )

    /** What is covered most in the last hour, a sentence per story, each with its sources. */
    val digest: List<SampleFact> get() = listOf(
        SampleFact("גל חום יביא עד 38 מעלות בסוף השבוע.", urls(heat, "valley", "meridian")),
        SampleFact("קו מעבורת חדש יחבר בין הנמלים בצפון.", urls(ferry, "harbor", "summit", "lantern")),
        SampleFact("הספרייה העירונית תיפתח בניסוי גם בלילות.", urls(library, "harbor", "valley")),
        SampleFact("רובוטי שליחויות יוצאים לרחובות העיר.", urls(robots, "summit", "meridian")),
    )

    val digestTitle = "גל חום בסוף השבוע"

    val digestWrittenAt: Instant get() = minutesAgo(2)

    /** Every report of the sample stories by URL, for a footnote's site and headline. */
    val reports: Map<String, ArticleSummary> get() = PreviewData.stories.flatMap { it.reports }.associateBy { it.url }
}

/** A sample fact as the server sends it: its footnotes from the sample stories' reports. */
internal fun SampleFact.toSentence() = Sentence(
    text,
    sources.mapNotNull(SummariesSample.reports::get).map { Footnote(it.url, it.sourceId, it.title) },
)

internal fun SampleSummary.toStorySummary() = StorySummary(title, sentences.map { it.toSentence() }, contradictions.map { it.toSentence() }, writtenAt)


/** The sample stories' generated articles by every report URL, as [news.app.server.NewsServer.writing] has them. */
internal fun sampleSummaries(): Map<String, StorySummary> =
    SummariesSample.stories.flatMap { s -> val written = s.toStorySummary(); s.story.reports.map { it.url to written } }.toMap()
