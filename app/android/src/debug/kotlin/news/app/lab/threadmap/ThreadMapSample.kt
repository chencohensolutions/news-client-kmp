package news.app

// Made-up threads for the lab's threads map: their names, so-far texts, related threads, and stories with their
// titles, first sentences, sites, and when their first and last reports were published. Lab only.

internal class MapThread(val id: String, val name: String, val related: List<String>, val summary: String = "")
internal class MapStory(val id: String, val title: String, val at: Long, val threads: List<String>, val lastAt: Long = at, val sites: List<String> = emptyList(), val text: String = "")

private const val DAY = 24 * 60 * 60 * 1000L
private const val START = 1794000000000L

internal val sampleMapThreads: List<MapThread> = listOf(
    MapThread("th-transport", "התחבורה בצפון", listOf("th-budget"), "קו מעבורת חדש, תחנת רכבת והגשר המשופץ משנים את התנועה באזור."),
    MapThread("th-budget", "התוכנית הכלכלית", listOf("th-transport", "th-education"), "שר האוצר הציג את התוכנית, והדיון בוועדת הכספים בדרך."),
    MapThread("th-weather", "גל החום", listOf(), "טמפרטורות גבוהות במיוחד צפויות בכל המדינה, בעיקר בעמקים."),
    MapThread("th-education", "שנת הלימודים", listOf("th-budget"), "ועדת החינוך דנה בשינויים בלוח החופשות ובתקציב בתי הספר."),
    MapThread("th-tech", "רובוטים בעיר", listOf(), "ניסוי של רובוטי שליחויות יוצא לדרך בכמה שכונות."),
)

internal val sampleMapStories: List<MapStory> = listOf(
    MapStory("s01", "מעבורת חדשה תחבר בין הנמלים", START, listOf("th-transport"), START + DAY, listOf("harbor", "summit", "lantern"), "קו מעבורת יחבר בין שני הנמלים בצפון."),
    MapStory("s02", "תחנת רכבת חדשה בשכונה המזרחית", START + DAY / 2, listOf("th-transport"), START + 2 * DAY, listOf("harbor", "valley"), "התחנה תיפתח בעוד חודשיים."),
    MapStory("s03", "הגשר על הנחל נפתח מחדש", START + 3 * DAY, listOf("th-transport"), START + 3 * DAY, listOf("lantern", "valley"), "הגשר המשופץ כולל שביל אופניים."),
    MapStory("s04", "שר האוצר הציג את התוכנית הכלכלית", START + DAY, listOf("th-budget"), START + 2 * DAY, listOf("meridian", "summit"), "התוכנית כוללת הקלות במס לעסקים קטנים."),
    MapStory("s05", "תוספת תקציב לתחבורה הציבורית", START + 2 * DAY, listOf("th-budget", "th-transport"), START + 3 * DAY, listOf("summit", "harbor"), "קווי אוטובוס בין־עירוניים יקבלו תוספת."),
    MapStory("s06", "ועדת הכספים תדון בתוכנית בשבוע הבא", START + 4 * DAY, listOf("th-budget"), START + 4 * DAY, listOf("meridian"), "אישור סופי צפוי לפני סוף השנה."),
    MapStory("s07", "גל חום: עד 38 מעלות בסוף השבוע", START + 3 * DAY, listOf("th-weather"), START + 4 * DAY, listOf("valley", "meridian"), "השירות המטאורולוגי ממליץ להימנע ממאמץ."),
    MapStory("s08", "עומס על רשת החשמל בגלל החום", START + 4 * DAY, listOf("th-weather"), START + 4 * DAY, listOf("summit", "lantern"), "חברת החשמל קוראת לחסוך בשעות השיא."),
    MapStory("s09", "דיון על קיצור חופשת הקיץ", START + 2 * DAY, listOf("th-education"), START + 3 * DAY, listOf("meridian", "valley"), "ועדת החינוך שמעה את עמדות ההורים."),
    MapStory("s10", "רובוט שליחויות ראשון ברחובות", START + 3 * DAY, listOf("th-tech"), START + 4 * DAY, listOf("summit", "meridian"), "הניסוי מתחיל בשתי שכונות."),
)
