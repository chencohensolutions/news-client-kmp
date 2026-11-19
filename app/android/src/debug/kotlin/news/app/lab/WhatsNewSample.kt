package news.app.lab

import news.app.server.Footnote
import news.app.server.Sentence
import news.app.server.StorySummary
import news.app.server.StoryUpdate
import news.app.stories.Story
import news.core.model.ArticleSummary
import kotlin.time.Instant

// The lab's made-up updated story for the what's new screens: an interchange closed after a beam fell, written four
// times over three hours on made-up sites (*.example). Times are relative to now, so "לפני 20 דק׳" reads right.

internal object WhatsNewSample {
    fun ago(m: Int): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis() - m * 60_000L)

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

    val names: Map<String, String> get() = PreviewData.sites.associate { it.id to it.name }

    private val sentences = listOf(
        Sentence("קורת בטון קרסה הבוקר במחלף הצפוני, והמחלף נסגר לתנועה.", listOf(note(r1), note(r2))),
        Sentence("הקורה נפלה על נתיב ריק; איש לא נפגע.", listOf(note(r2))),
        Sentence("שלושה נתיבים סגורים, והמשטרה מסרה שהמחלף יישאר סגור לפחות עד מחר בבוקר.", listOf(note(r3), note(r2))),
        Sentence("נפתח מסלול עוקף דרך הכביש הישן; צפויים עיכובים של כחצי שעה.", listOf(note(r5))),
        Sentence("ראש העירייה אמר שכל הגשרים בעיר ייבדקו.", listOf(note(r4))),
        Sentence("המחלף שופץ לפני ארבע שנים.", listOf(note(r6))),
    )

    /** The four versions' updates, oldest first, as the server sends them; [kinds] false: a server before kinds. */
    private fun updates(kinds: Boolean) = listOf(
        StoryUpdate(ago(185), listOf(sentences[0]), emptyList()),
        StoryUpdate(
            ago(150),
            listOf(Sentence("המשטרה: המחלף יישאר סגור לפחות עד מחר בבוקר.", listOf(note(r2), note(r1))), sentences[1]),
            if (kinds) listOf("development", "detail") else emptyList(),
        ),
        StoryUpdate(
            ago(40),
            listOf(Sentence("שלושה נתיבים סגורים, לא שניים כפי שדווח קודם.", listOf(note(r3))), Sentence("ראש העירייה: \"נבדוק את כל הגשרים בעיר\".", listOf(note(r4)))),
            if (kinds) listOf("correction", "reaction") else emptyList(),
        ),
        StoryUpdate(ago(20), listOf(sentences[3], sentences[5]), if (kinds) listOf("development", "background") else emptyList()),
    )

    fun summary(kinds: Boolean = true) = StorySummary(
        title = "המחלף הצפוני סגור אחרי קריסת קורה; נפתח מסלול עוקף",
        sentences = sentences,
        writtenAt = ago(20),
        id = "lab-interchange",
        reports = story.reports.map { it.url },
        version = 4,
        versions = 4,
        updates = updates(kinds),
    )
}
