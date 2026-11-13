package news.app

import news.app.server.ChangeKind
import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.SentenceChange
import news.app.server.StoryChanges
import news.app.server.StorySummary
import news.app.server.TextPart
import news.app.stories.Story
import news.core.model.ArticleSummary
import news.app.lab.PreviewData
import kotlin.time.Instant

// The "מה חדש" round's made-up story: an interchange closed after a beam fell, written four times over three hours.
// Made-up sites (PreviewData.sites, *.example) and headlines; the times are relative to now so "לפני 20 דק׳" reads right.

internal object WhatsNewSample {
    private fun ago(m: Int) = Instant.fromEpochMilliseconds(System.currentTimeMillis() - m * 60_000L)

    private fun report(site: String, n: Int, title: String, minutes: Int) = ArticleSummary(
        sourceId = site, url = "https://$site.example/news/$n", title = title, publishedAt = ago(minutes), category = "israel",
        imageUrl = "https://picsum.photos/seed/whatsnew$n/800/450",
    )

    private val r1 = report("harbor", 301, "קורת בטון קרסה במחלף הצפוני; המחלף נסגר", 185)
    private val r2 = report("valley", 302, "קריסה במחלף: הקורה נפלה על נתיב ריק", 150)
    private val r3 = report("summit", 303, "שלושה נתיבים סגורים במחלף הצפוני, לא שניים", 45)
    private val r4 = report("meridian", 304, "ראש העירייה: נבדוק את כל הגשרים בעיר", 40)
    private val r5 = report("lantern", 305, "נפתח מסלול עוקף למחלף הסגור", 22)
    private val r6 = report("harbor", 306, "המחלף שקרס שופץ לפני ארבע שנים", 20)

    private fun note(r: ArticleSummary) = Footnote(r.url, r.sourceId, r.title, publishedAt = r.publishedAt)

    val story = Story(listOf(r5, r6, r3, r4, r2, r1))

    val names: Map<String, String> = PreviewData.sites.associate { it.id to it.name }

    /** Every update, newest first: version 4 (20 min), 3 (40 min), 2 (2.5 hours); version 1 is the story itself. */
    val updates: List<StoryUpdateItem> = listOf(
        StoryUpdateItem(UpdateKind.DEVELOPMENT, "נפתח מסלול עוקף דרך הכביש הישן; צפויים עיכובים של כחצי שעה.", listOf(note(r5)), ago(20), 4),
        StoryUpdateItem(UpdateKind.BACKGROUND, "המחלף שופץ לפני ארבע שנים.", listOf(note(r6)), ago(20), 4),
        StoryUpdateItem(UpdateKind.CORRECTION, "שלושה נתיבים סגורים, לא שניים כפי שדווח קודם.", listOf(note(r3)), ago(40), 3, was = "2 נתיבים", now = "3 נתיבים"),
        StoryUpdateItem(UpdateKind.REACTION, "ראש העירייה: \"נבדוק את כל הגשרים בעיר\".", listOf(note(r4)), ago(40), 3),
        StoryUpdateItem(UpdateKind.DEVELOPMENT, "המשטרה: המחלף יישאר סגור לפחות עד מחר בבוקר.", listOf(note(r2), note(r1)), ago(150), 2),
        StoryUpdateItem(UpdateKind.DETAIL, "הקורה נפלה על נתיב ריק; איש לא נפגע.", listOf(note(r2)), ago(150), 2),
    )

    private val sentences = listOf(
        Sentence("קורת בטון קרסה הבוקר במחלף הצפוני, והמחלף נסגר לתנועה.", listOf(note(r1), note(r2))),
        Sentence("הקורה נפלה על נתיב ריק; איש לא נפגע.", listOf(note(r2))),
        Sentence("שלושה נתיבים סגורים, והמשטרה מסרה שהמחלף יישאר סגור לפחות עד מחר בבוקר.", listOf(note(r3), note(r2))),
        Sentence("נפתח מסלול עוקף דרך הכביש הישן; צפויים עיכובים של כחצי שעה.", listOf(note(r5))),
        Sentence("ראש העירייה אמר שכל הגשרים בעיר ייבדקו.", listOf(note(r4))),
        Sentence("המחלף שופץ לפני ארבע שנים.", listOf(note(r6))),
    )

    /** The latest version, as the app has it today: the words it added since version 3 marked ([StoryChanges]). */
    val summary = StorySummary(
        title = "המחלף הצפוני סגור אחרי קריסת קורה; נפתח מסלול עוקף",
        sentences = sentences,
        writtenAt = ago(20),
        id = "lab-interchange",
        reports = story.reports.map { it.url },
        version = 4,
        versions = 4,
        changes = StoryChanges(
            since = ago(40),
            title = "שלושה נתיבים סגורים במחלף הצפוני",
            sentences = listOf(
                SentenceChange(ChangeKind.SAME, listOf(TextPart(sentences[0].text, false))),
                SentenceChange(ChangeKind.SAME, listOf(TextPart(sentences[1].text, false))),
                SentenceChange(ChangeKind.CHANGED, listOf(TextPart("שלושה נתיבים סגורים, ", false), TextPart("והמשטרה מסרה ש", true), TextPart("המחלף יישאר סגור לפחות עד מחר בבוקר.", false))),
                SentenceChange(ChangeKind.NEW, listOf(TextPart(sentences[3].text, true))),
                SentenceChange(ChangeKind.CHANGED, listOf(TextPart("ראש העירייה ", false), TextPart("אמר שכל הגשרים בעיר ייבדקו.", true))),
                SentenceChange(ChangeKind.NEW, listOf(TextPart(sentences[5].text, true))),
            ),
        ),
    )

    /** Last opened an hour ago: versions 3 and 4 are new to the reader. */
    fun state(lastSeenMinutes: Int? = 60) = WhatsNewState(story, summary, updates, ago(185), lastSeenMinutes?.let(::ago))

    /** Another story in the feed, not updated, beside the updated one. */
    val other: ArticleSummary = PreviewData.stories[1].reports.first()
}
