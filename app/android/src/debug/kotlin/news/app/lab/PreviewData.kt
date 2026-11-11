package news.app.lab

import news.app.BackgroundRun
import news.app.Site
import news.app.StorageInfo
import news.app.stories.Story
import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleSummary
import news.core.model.ArticleVideo
import kotlin.time.Instant

// Made-up data for the UI lab: invented sites, headlines and articles in Hebrew, with Hebrew-length titles and
// placeholder pictures. Nothing reads it but the lab.
internal object PreviewData {

    /** The made-up sites the samples come from. */
    val sites: List<Site> = listOf(
        Site("harbor", "חדשות הנמל", "https://harbor.example"),
        Site("valley", "קול העמק", "https://valley.example"),
        Site("summit", "הפסגה", "https://summit.example"),
        Site("meridian", "מרידיאן", "https://meridian.example"),
        Site("lantern", "הפנס", "https://lantern.example"),
    )

    private val base = Instant.parse("2026-10-30T08:00:00Z")

    private fun at(minutesBefore: Int) = Instant.fromEpochMilliseconds(base.toEpochMilliseconds() - minutesBefore * 60_000L)

    private fun picture(n: Int) = "https://picsum.photos/seed/lab$n/800/450"

    private fun item(site: String, n: Int, title: String, category: String, minutesBefore: Int, summary: String? = null, picture: Boolean = true) =
        ArticleSummary(
            sourceId = site, url = "https://$site.example/news/$n", title = title, summary = summary,
            imageUrl = if (picture) picture(n) else null, publishedAt = at(minutesBefore), category = category,
        )

    /** A morning's feed: five sites, interleaved as the feed shows them, every headline with a category. */
    val headlines: List<ArticleSummary> = listOf(
        item("harbor", 101, "מועצת העיר אישרה תקציב חדש לשיפוץ הטיילת", "israel", 5),
        item("valley", 102, "החקלאים בעמק מדווחים על יבול תפוחים שיא", "economy", 9, summary = "עונה גשומה וקרירה הביאה את היבול הגבוה מזה עשור"),
        item("summit", 103, "סטארט־אפ מקומי גייס 40 מיליון דולר לפיתוח סוללות", "tech", 14),
        item("meridian", 104, "המשלחת הדיפלומטית חזרה מסבב שיחות בבירות אירופה", "world", 18),
        item("lantern", 105, "הנבחרת הצעירה עלתה לגמר אחרי ניצחון בהארכה", "sport", 22),
        item("harbor", 106, "תחנת רכבת חדשה תיפתח בעוד חודשיים", "israel", 27, summary = "התחנה תשרת כ-20 אלף נוסעים ביום"),
        item("valley", 107, "בית החולים האזורי פותח מחלקה לרפואה דחופה לילדים", "health", 31),
        item("summit", 108, "מדד המחירים עלה ב-0.3% בחודש האחרון", "economy", 36),
        item("meridian", 109, "ועדת החינוך דנה בקיצור חופשת הקיץ", "politics", 40),
        item("lantern", 110, "פסטיבל המוזיקה בפארק יתקיים השנה בשלושה ערבים", "culture", 45, picture = false),
        item("harbor", 111, "הנמל הצפוני יקלוט ספינות משא גדולות יותר", "economy", 50),
        item("valley", 112, "מתנדבים שתלו אלף עצים לאורך הנחל", "israel", 56),
        item("summit", 113, "חברת התקשורת תשדרג את רשת הסיבים בצפון", "tech", 61),
        item("meridian", 114, "שר האוצר הציג את עיקרי התוכנית הכלכלית", "politics", 66, summary = "התוכנית כוללת הקלות במס לעסקים קטנים"),
        item("lantern", 115, "מחקר: שינה טובה משפרת את הזיכרון גם אצל מבוגרים", "health", 72),
        item("harbor", 116, "עבודות תשתית יחסמו את הכביש הראשי בסוף השבוע", "israel", 78),
        item("valley", 117, "המוזיאון האזורי מציג תערוכה של צילומי טבע", "culture", 84),
        item("summit", 118, "הבורסה סיימה את השבוע בעליות", "economy", 90),
    )

    /** Reports of the same events from different sites, as the server groups them. */
    val stories: List<Story> = listOf(
        Story(listOf(
            item("harbor", 201, "מעבורת חדשה תחבר בין שני הנמלים בצפון", "israel", 7, summary = "השיט יימשך כ-40 דקות ויפעל שש פעמים ביום"),
            item("summit", 202, "קו מעבורת יחבר בין הנמלים: הפלגה ראשונה בחודש הבא", "israel", 12),
            item("lantern", 203, "המעבורת לצפון יוצאת לדרך: כל מה שצריך לדעת", "israel", 20, picture = false),
        )),
        Story(listOf(
            item("valley", 204, "גל חום: עד 38 מעלות צפויות בסוף השבוע", "israel", 15, summary = "השירות המטאורולוגי ממליץ להימנע ממאמץ בשעות הצהריים"),
            item("meridian", 205, "החום חוזר: אזהרה מפני עומס חום כבד בעמקים", "israel", 25),
        )),
        Story(listOf(
            item("harbor", 206, "הספרייה העירונית תהיה פתוחה גם בשעות הלילה", "culture", 33),
            item("valley", 207, "ניסוי: הספרייה תפעל עד חצות בימי חמישי", "culture", 47, picture = false),
        )),
        Story(listOf(
            item("summit", 208, "רובוט שליחויות ראשון יצא לרחובות העיר", "tech", 52),
            item("meridian", 209, "רובוטים יחלקו חבילות בשכונות: הניסוי מתחיל", "tech", 70),
        )),
    )

    /** Every headline the stories use, so a Stories screen and its feed agree. */
    val storyHeadlines: List<ArticleSummary> = stories.flatMap { it.reports }

    val article: Article = Article(
        sourceId = "harbor",
        url = "https://harbor.example/news/106",
        title = "תחנת רכבת חדשה תיפתח בעוד חודשיים בשכונה המזרחית",
        subtitle = "התחנה תשרת כ-20 אלף נוסעים ביום • עבודות הגישה לתחנה יסתיימו עד סוף החודש • קווי אוטובוס חדשים יחברו אליה",
        author = "כתב העיר",
        publishedAt = at(27),
        imageUrl = picture(106),
        body = listOf(
            "תחנת הרכבת החדשה בשכונה המזרחית תיפתח לנוסעים בעוד כחודשיים, כך נמסר היום (שישי) מחברת התשתיות. התחנה, שנבנתה במשך שלוש שנים, תשרת לפי ההערכות כ-20 אלף נוסעים ביום.",
            "לצד פתיחת התחנה יופעלו שלושה קווי אוטובוס חדשים שיחברו אליה את השכונות הסמוכות. העירייה מסרה כי חניון של 400 מקומות ייפתח לצד התחנה.",
            "תושבי השכונה בירכו על הפתיחה הקרובה, אך ביקשו להגביר את האכיפה על חניה בלתי חוקית ברחובות הסמוכים.",
            "בחברת התשתיות אמרו כי הנסיעה מהתחנה למרכז העיר תימשך כ-12 דקות.",
        ),
    )

    /** A phone with a feed cached and an archive in Documents. */
    val storage: StorageInfo = StorageInfo(
        feedBytes = 9_400_000, cachedArticles = 412, imageBytes = 61_000_000,
        archiveFolder = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FNews%20archive",
        archiveAvailable = true, archivedArticles = 3_184, archiveBytes = 412_000_000,
    )

    /** A morning of background refreshes every 5 minutes, on battery, newest first. */
    val backgroundRuns: List<BackgroundRun> = (0 until 24).map { i ->
        BackgroundRun(
            startedAt = System.currentTimeMillis() - i * 5 * 60_000L, durationMs = 4_200L + i % 3 * 900, cpuMs = 1_300L + i % 4 * 200,
            bytesReceived = 2_400_000L + i % 5 * 300_000, batteryPercent = 81 + i / 6, charging = false, headlines = 410, articlesDownloaded = i % 4,
        )
    }

    /** An article with photos in its text and a video in an embedded player. */
    val mediaArticle: Article = Article(
        sourceId = "meridian",
        url = "https://meridian.example/news/114",
        title = "שר האוצר הציג את עיקרי התוכנית הכלכלית לשנה הבאה",
        subtitle = "התוכנית כוללת הקלות במס לעסקים קטנים, השקעה בתחבורה ציבורית והגדלת התקציב למחקר • הדיון בכנסת ייפתח בשבוע הבא",
        author = "הכתב הכלכלי",
        publishedAt = at(66),
        imageUrl = picture(114),
        body = emptyList(),
        blocks = listOf(
            ArticleBlock.Text("שר האוצר הציג היום את עיקרי התוכנית הכלכלית לשנה הבאה. לדבריו, התוכנית נועדה להקל על עסקים קטנים ולהאיץ את הצמיחה."),
            ArticleBlock.Image(picture(301), "מסיבת העיתונאים במשרד האוצר | צילום להמחשה"),
            ArticleBlock.Text("בין הצעדים: הפחתה של נקודת אחוז במס לעסקים שמחזורם נמוך, ותוספת תקציב לקווי אוטובוס בין־עירוניים."),
            ArticleBlock.Text("כלכלנים העריכו כי ההשפעה על הגירעון תהיה מתונה, אך הזהירו מפני עלייה בהוצאות בשנים הבאות."),
            ArticleBlock.Image(picture(302), "גרף התחזית שהוצג במסיבת העיתונאים"),
            ArticleBlock.Text("הדיון בוועדת הכספים ייפתח בשבוע הבא, ואישור סופי צפוי לפני סוף השנה."),
        ),
        video = ArticleVideo(poster = picture(303), embed = "https://player.example/embed/1"),
    )

    /** An article whose video is a public MP4, played in the app's own player. */
    val streamArticle: Article = Article(
        sourceId = "lantern",
        url = "https://lantern.example/news/401",
        title = "הגשר על הנחל נפתח מחדש אחרי שנה של שיפוצים",
        subtitle = "הגשר המשופץ רחב יותר, וכולל שביל אופניים והולכי רגל • התנועה בכביש העוקף צפויה לרדת",
        author = "צוות החדשות",
        publishedAt = at(3),
        imageUrl = picture(401),
        blocks = listOf(
            ArticleBlock.Text("הגשר על הנחל נפתח הבוקר מחדש לתנועה, לאחר שנה של עבודות שיפוץ והרחבה."),
            ArticleBlock.Image(picture(402), "הגשר לאחר השיפוץ (צילום להמחשה)"),
            ArticleBlock.Text("לצד שני נתיבי הנסיעה נבנה שביל נפרד לאופניים ולהולכי רגל, ותאורה חדשה הותקנה לכל אורכו."),
            ArticleBlock.Text("ברשות המקומית אמרו כי פתיחת הגשר תקצר את זמן הנסיעה בין שתי השכונות בכעשר דקות."),
        ),
        video = ArticleVideo(poster = picture(403), stream = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", duration = null),
    )

    /** The summary the media article was opened from. */
    val mediaArticleSummary: ArticleSummary = ArticleSummary(mediaArticle.sourceId, mediaArticle.url, mediaArticle.title, imageUrl = mediaArticle.imageUrl)

    /** The summary the article was opened from. */
    val articleSummary: ArticleSummary = ArticleSummary(article.sourceId, article.url, article.title, imageUrl = article.imageUrl)

    /** The feed was fetched four minutes ago. */
    val refreshedAt: Instant get() = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 4 * 60_000)

    /** Eight headlines arrived since the last visit. */
    val newUrls: Set<String> = (headlines.take(7) + stories[0].reports.take(1)).map { it.url }.toSet()

    /** Three headlines and one story report were opened. */
    val read: Set<String> = (headlines.drop(7).take(3) + stories[2].reports.take(1)).map { it.url }.toSet()

    /** The story a pull in the lab brings in: two sites' reports of the bridge reopening. */
    val incomingStory: Story = Story(
        listOf(
            ArticleSummary(
                sourceId = "lantern", url = streamArticle.url, title = streamArticle.title, imageUrl = streamArticle.imageUrl,
                publishedAt = streamArticle.publishedAt, category = "israel",
            ),
            item("valley", 402, "אחרי שנה: הגשר על הנחל חוזר לפעול", "israel", 4),
        ),
    )
}
