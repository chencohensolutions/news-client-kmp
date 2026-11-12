package news.app

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.foundation.selection.selectable
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Dns
import news.app.server.ServerSource
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.roundToInt
import news.app.stories.STRICTNESS_MAX
import news.app.stories.STRICTNESS_MIN
import news.app.stories.STRICTNESS_STEP
import news.app.stories.StoryText
import news.core.model.Categories

/** The statistics page as Settings shows it ([StatsScreen]). */
class StatsPage(val stats: news.app.server.ServerStats?, val loading: Boolean, val onRefresh: () -> Unit, val onBack: () -> Unit)

private enum class SettingsPage { MAIN, SOURCES, NOTIFICATIONS, DATA, LOG, ABOUT, USERS }

/**
 * Settings, two levels. The first page holds what the owner changes: the sites (one row, with a page of switches
 * behind it), headlines, reading, stories and the background refresh, as list rows with icons. What is only read,
 * storage and the archive, the background log, about, has pages of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    sources: List<Site>,
    stories: StoriesState,
    downloads: ArticleDownloads,
    storage: StorageInfo,
    backgroundRuns: List<BackgroundRun>,
    onChange: (AppSettings) -> Unit,
    onChooseArchive: (Uri) -> Unit,
    onStopArchive: () -> Unit,
    archiveImport: ArchiveImport? = null,
    onImportArchive: () -> Unit = {},
    onArchiveNow: () -> Unit = {},
    onClearCache: () -> Unit,
    onClearBackgroundLog: () -> Unit,
    readCount: Int,
    onClearReadHistory: () -> Unit,
    unreadCount: Int = 0,
    onMarkAllRead: () -> Unit = {},
    topicCounts: Map<String, Int> = emptyMap(),
    onMarkAllViewed: () -> Unit = {},
    server: ServerState = ServerState.NotConnected(),
    serverActions: ServerActions = ServerActions(),
    serverPage: Boolean = false,
    onServerPage: (Boolean) -> Unit = {},
    /** The server's statistics page, over its page; null when it isn't open. */
    stats: StatsPage? = null,
    /** The sites the server reads, for the sites page; null before it answers. */
    serverSources: List<ServerSource>? = null,
    /** One site's settings on the server: switched on or off, from its RSS feed, the wait between its requests. */
    onServerSource: (id: String, enabled: Boolean?, feed: Boolean?, waitMs: Int?) -> Unit = { _, _, _, _ -> },
    /** The server's grouping, shown and changed in the stories section; null when it doesn't group or answer. */
    serverGrouping: news.app.server.ServerGrouping? = null,
    onServerGrouping: (strictness: Int?, text: StoryText?, days: Int?) -> Unit = { _, _, _ -> },
    /** Who groups the server's stories: Gemini (true) or the model. */
    onServerGroupingMethod: (Boolean) -> Unit = {},
    /** With the server writing summaries: whether Gemini reads the full articles; null hides the switch. */
    /** With the server writing summaries: its stories' length (SHORT, MEDIUM, LONG, FULL); null hides the choice. */
    storyLength: String? = null,
    onStoryLength: (String) -> Unit = {},
    /** With the server writing summaries: how often it writes a story again; null hides the rows. */
    rewrite: news.app.server.Rewrite? = null,
    onRewrite: (minutes: Int?, onNewSite: Boolean?, reserveAfter: Int?) -> Unit = { _, _, _ -> },
    fullArticles: Boolean? = null,
    onFullArticles: (Boolean) -> Unit = {},
    /** Shows a site in this user's feed (false) or hides it (true): their own choice, on the server. */
    onSourceHidden: (id: String, hidden: Boolean) -> Unit = { _, _ -> },
    /** The admin page's users, invite and error. */
    adminState: AdminState = AdminState(),
    adminActions: AdminActions = AdminActions(),
    onBack: () -> Unit,
) {
    // Only the server's admins change its settings (the sites it reads, grouping, writing); the others see them read-only.
    val admin = (server as? ServerState.Connected)?.admin == true
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    // The news server's page (Settings › שרת החדשות) is its own screen; an enrollment link opens it from anywhere.
    if (serverPage && stats != null) {
        BackHandler(onBack = stats.onBack)
        StatsScreen(stats.stats, stats.loading, sources.associate { it.id to it.name }, stats.onRefresh, stats.onBack)
        return
    }
    if (serverPage) {
        BackHandler { onServerPage(false) }
        ServerScreen(server, serverActions)
        return
    }
    AppLayout {
        BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (page) {
                                SettingsPage.MAIN -> "הגדרות"
                                SettingsPage.SOURCES -> "אתרים"
                                SettingsPage.NOTIFICATIONS -> "התראות"
                                SettingsPage.DATA -> "אחסון וארכיון"
                                SettingsPage.LOG -> "יומן הרענונים"
                                SettingsPage.ABOUT -> "אודות"
                                SettingsPage.USERS -> "משתמשים ומכשירים"
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                when (page) {
                    SettingsPage.MAIN -> MainPage(
                        settings, sources, stories, downloads, storage, backgroundRuns, readCount,
                        onChange, onClearReadHistory, unreadCount, onMarkAllRead, topicCounts,
                        onMarkAllViewed = onMarkAllViewed,
                        serverStatus = serverStatus(server),
                        serverGrouping = serverGrouping,
                        onServerGrouping = onServerGrouping,
                        onServerGroupingMethod = onServerGroupingMethod,
                        storyLength = storyLength,
                        onStoryLength = onStoryLength,
                        rewrite = rewrite,
                        onRewrite = onRewrite,
                        fullArticles = fullArticles,
                        onFullArticles = onFullArticles,
                        siteCount = serverSources?.let { list -> list.count { it.enabled && !it.hidden } to list.size },
                        admin = admin,
                        onOpenServer = { onServerPage(true) },
                        onOpen = { page = it },
                    )
                    SettingsPage.SOURCES -> if (serverSources != null) {
                        // Everyone picks the sites of their own feed; only an admin changes how the server reads them.
                        Muted(
                            "״בפיד שלי״: האתרים שמופיעים בפיד שלכם; הבחירה נשמרת בשרת לכל המכשירים שלכם. " +
                                if (admin) {
                                    "מתחת לכל אתר, איך השרת קורא אותו, לכל המשתמשים: אתר כבוי לא נקרא. לאתר עם פיד RSS אפשר לקרוא את הכותרות מהפיד, ולכל אתר נקבע כמה השרת מחכה בין בקשה לבקשה."
                                } else {
                                    "איך השרת קורא כל אתר מוצג לקריאה בלבד: רק מנהל השרת משנה אותו."
                                },
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        serverSources.forEach { source ->
                            SwitchRow(
                                title = source.name,
                                subtitle = source.baseUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.") +
                                    when {
                                        !source.enabled -> " · השרת לא קורא אותו"
                                        source.hidden -> " · מוסתר מהפיד שלי"
                                        else -> " · בפיד שלי"
                                    },
                                checked = source.enabled && !source.hidden,
                                enabled = source.enabled,
                                onChange = { on -> onSourceHidden(source.id, !on) },
                            )
                            SwitchRow(
                                title = "השרת קורא את האתר",
                                subtitle = "לכל המשתמשים",
                                checked = source.enabled,
                                onChange = { on -> onServerSource(source.id, on, null, null) },
                                enabled = admin,
                                inset = 24.dp,
                            )
                            if (source.enabled && source.hasFeed) {
                                SwitchRow(
                                    title = "מפיד ה-RSS",
                                    subtitle = "הכותרות מהפיד של האתר במקום מהעמודים שלו",
                                    checked = source.feed,
                                    onChange = { on -> onServerSource(source.id, null, on, null) },
                                    enabled = admin,
                                    inset = 24.dp,
                                )
                            }
                            if (source.enabled) {
                                ChoiceRow(
                                    title = "המתנה בין כתבות",
                                    options = (SITE_WAITS_MS + source.waitMs).distinct().sorted().map { ms -> ms to waitName(ms) },
                                    selected = source.waitMs,
                                    onSelect = { ms -> onServerSource(source.id, null, null, ms) },
                                    inset = 24.dp,
                                    enabled = admin,
                                )
                            }
                        }
                    } else {
                        Muted("השרת לא ענה עדיין. רשימת האתרים מגיעה ממנו.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    SettingsPage.USERS -> AdminPage(adminState, adminActions)
                    SettingsPage.NOTIFICATIONS -> NotificationsPage(settings, sources, onChange)
                    SettingsPage.DATA -> DataPage(storage, settings, onChange, onClearCache, onChooseArchive, onStopArchive, archiveImport, onImportArchive, onArchiveNow)
                    SettingsPage.LOG -> LogPage(backgroundRuns, onClearBackgroundLog)
                    SettingsPage.ABOUT -> AboutPage(sources)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MainPage(
    settings: AppSettings,
    sources: List<Site>,
    stories: StoriesState,
    downloads: ArticleDownloads,
    storage: StorageInfo,
    backgroundRuns: List<BackgroundRun>,
    readCount: Int,
    onChange: (AppSettings) -> Unit,
    onClearReadHistory: () -> Unit,
    unreadCount: Int = 0,
    onMarkAllRead: () -> Unit = {},
    topicCounts: Map<String, Int> = emptyMap(),
    onMarkAllViewed: () -> Unit = {},
    serverStatus: String,
    serverGrouping: news.app.server.ServerGrouping?,
    onServerGrouping: (strictness: Int?, text: StoryText?, days: Int?) -> Unit,
    onServerGroupingMethod: (Boolean) -> Unit = {},
    /** With the server writing summaries: its stories' length (SHORT, MEDIUM, LONG, FULL); null hides the choice. */
    storyLength: String? = null,
    onStoryLength: (String) -> Unit = {},
    /** With the server writing summaries: how often it writes a story again; null hides the rows. */
    rewrite: news.app.server.Rewrite? = null,
    onRewrite: (minutes: Int?, onNewSite: Boolean?, reserveAfter: Int?) -> Unit = { _, _, _ -> },
    fullArticles: Boolean? = null,
    onFullArticles: (Boolean) -> Unit = {},
    /** The server's sites in this user's feed, and all; null before it answers (the ones kept on the phone are counted then). */
    siteCount: Pair<Int, Int>?,
    /** The user is the server's admin: its settings can be changed here, and its people managed. */
    admin: Boolean = false,
    onOpenServer: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    NavRow(Icons.Filled.Dns, "שרת החדשות", serverStatus, onOpenServer)

    val (on, all) = siteCount ?: (sources.count { it.id !in settings.hiddenSources } to sources.size)
    NavRow(Icons.Filled.Language, "אתרים", "$on בפיד שלי מתוך $all") { onOpen(SettingsPage.SOURCES) }
    if (admin) {
        NavRow(Icons.Filled.Group, "משתמשים ומכשירים", "הזמנה, תפקידים, ביטול מכשירים · מנהל") { onOpen(SettingsPage.USERS) }
    }

    Section("כותרות")
    SwitchRow(
        title = "תוויות סינון בכותרת",
        subtitle = "האתרים והנושאים שנבחרו מוצגים מתחת לכותרת, עם ✕ להסרה. אתר או נושא אחד מופיע בכותרת עצמה בכל מקרה.",
        checked = settings.showFilterLabels,
        onChange = { onChange(settings.copy(showFilterLabels = it)) },
    )
    ChoiceRow(
        title = "חדש עד",
        subtitle = "כותרת היא חדשה אם עוד לא גללתם מעבר לה ולא קראתם אותה, ופורסמה בשעות האחרונות. כותרת בלי שעת פרסום נספרת מהרגע שהופיעה בפיד.",
        options = NEW_WITHIN_HOURS_CHOICES.map { it to if (it == 0) "ללא הגבלה" else "$it שע׳" },
        selected = settings.newWithinHours,
        onSelect = { onChange(settings.copy(newWithinHours = it)) },
    )
    SwitchRow(
        title = "גלילה נעצרת בראש כתבה",
        subtitle = "גלילה מהירה ממשיכה לכתבה הבאה ומאטה בעדינות, כך שהיא נעצרת בראש המסך מתחת לשורת ההתראות. כתבה ארוכה מהמסך נגללת כרגיל באמצעה.",
        checked = settings.snapToRows,
        onChange = { onChange(settings.copy(snapToRows = it)) },
    )
    SwitchRow(
        title = "תמונה מתחת לכותרת",
        subtitle = "התמונה של סיפור מופיעה אחרי הכותרת, תקציר הכתבה ותווית הנושא, מעל רשימת הכתבות הקשורות. כבוי: התמונה מעל הכותרת.",
        checked = settings.pictureBelowText,
        onChange = { onChange(settings.copy(pictureBelowText = it)) },
    )
    ChoiceRow(
        title = "תמונות מראש בגלילה",
        subtitle = "בזמן גלילה, התמונות של הכותרות הבאות יורדות מראש, כך שהן מוכנות כשמגיעים אליהן.",
        options = LIST_AHEAD_COUNTS.map { it to if (it == 0) "כבוי" else "$it" },
        selected = settings.listAhead,
        onSelect = { onChange(settings.copy(listAhead = it)) },
    )

    Section("קריאה")
    SwitchRow(
        title = "וידאו נצמד למעלה",
        subtitle = "סרטון שמתנגן נשאר בראש המסך כשגוללים למטה, כך שאפשר להמשיך לקרוא את הכתבה תוך כדי צפייה.",
        checked = settings.stickyVideo,
        onChange = { onChange(settings.copy(stickyVideo = it)) },
    )
    Muted(
        when {
            downloads.running -> "מביא כתבות מהשרת… ${downloads.done} מתוך ${downloads.total}"
            downloads.total > 0 -> "${downloads.done} מתוך ${downloads.total} כתבות בטלפון"
            else -> "מתחיל אחרי הרענון הבא"
        },
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ChoiceRow(
        title = "טעינה מראש בזמן קריאה",
        subtitle = "בזמן קריאה, הכתבות הבאות שלא נקראו נטענות מראש עם התמונות שלהן.",
        options = READ_AHEAD_COUNTS.map { it to if (it == 0) "כבוי" else "$it" },
        selected = settings.readAhead,
        onSelect = { onChange(settings.copy(readAhead = it)) },
    )
    ListItem(
        headlineContent = { Text("סימוני קריאה") },
        supportingContent = { Text("$readCount כתבות סומנו כנקראו; כותרת שנפתחה מוצגת בהירה יותר") },
        leadingContent = { Icon(Icons.Filled.DoneAll, null) },
        trailingContent = { TextButton(onClick = onClearReadHistory, enabled = readCount > 0) { Text("ניקוי") } },
    )
    var confirmAllRead by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("סימון הכל כנקרא") },
        supportingContent = { Text(if (unreadCount > 0) "$unreadCount כתבות בפיד עוד לא נקראו" else "כל הכתבות בפיד נקראו") },
        leadingContent = { Icon(Icons.Filled.Checklist, null) },
        trailingContent = { TextButton(onClick = { confirmAllRead = true }, enabled = unreadCount > 0) { Text("סימון") } },
    )
    if (confirmAllRead) {
        AlertDialog(
            onDismissRequest = { confirmAllRead = false },
            title = { Text("לסמן הכל כנקרא?") },
            text = { Text("כל $unreadCount הכתבות בפיד יסומנו כנקראו. אפשר לבטל רק בניקוי כל סימוני הקריאה.") },
            confirmButton = { TextButton(onClick = { confirmAllRead = false; onMarkAllRead() }) { Text("סימון") } },
            dismissButton = { TextButton(onClick = { confirmAllRead = false }) { Text("ביטול") } },
        )
    }

    var confirmAllViewed by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("סימון הכל כנצפה") },
        supportingContent = { Text("כל הכותרות והכתבות המסוכמות בפיד יפסיקו להיות חדשות, והמונה של חדשים יתאפס") },
        leadingContent = { Icon(Icons.Filled.Visibility, null) },
        trailingContent = { TextButton(onClick = { confirmAllViewed = true }) { Text("סימון") } },
    )
    if (confirmAllViewed) {
        AlertDialog(
            onDismissRequest = { confirmAllViewed = false },
            title = { Text("לסמן הכל כנצפה?") },
            text = { Text("שום כותרת או כתבה מסוכמת בפיד לא תסומן עוד כחדשה. סימוני הקריאה לא משתנים.") },
            confirmButton = { TextButton(onClick = { confirmAllViewed = false; onMarkAllViewed() }) { Text("סימון") } },
            dismissButton = { TextButton(onClick = { confirmAllViewed = false }) { Text("ביטול") } },
        )
    }

    Section("סיפורים")
    ChoiceRow(
        title = "תוויות המקורות בכתבה מסוכמת",
        subtitle = "איפה מופיעים שמות האתרים שמהם נכתבה הכתבה, בכרטיס שלה בפיד.",
        options = SourceLabels.entries.map { it to it.title },
        selected = settings.sourceLabels,
        onSelect = { onChange(settings.copy(sourceLabels = it)) },
    )
    if (!admin && (storyLength != null || rewrite != null || fullArticles != null || serverGrouping != null)) {
        Muted("הגדרות הכתיבה והקיבוץ של השרת מוצגות לקריאה בלבד: רק מנהל השרת משנה אותן.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (storyLength != null) {
        ChoiceRow(
            title = "אורך כתבה",
            subtitle = "כמה משפטים כותב Gemini לכל סיפור; יותר כשיותר אתרים מדווחים. ״מלא״: כל עובדה שבכתבות, פעם אחת. חל על סיפורים שנכתבים מעכשיו.",
            options = listOf("SHORT" to "קצר", "MEDIUM" to "בינוני", "LONG" to "ארוך", "FULL" to "מלא"),
            selected = storyLength,
            onSelect = onStoryLength,
            enabled = admin,
        )
    }
    if (rewrite != null) {
        ChoiceRow(
            title = "כתיבה מחדש של סיפור",
            subtitle = "סיפור שמצטרפות אליו כתבות נכתב מחדש לכל היותר בתדירות הזו, כדי לחסוך בקשות ל-Gemini.",
            options = (listOf(0, 30, 60) + rewrite.minutes).distinct().map { it to if (it == 0) "תמיד" else "כל $it דק׳" },
            selected = rewrite.minutes,
            onSelect = { onRewrite(it, null, null) },
            enabled = admin,
        )
        SwitchRow(
            title = "אתר חדש כותב מיד",
            subtitle = "אתר שעוד לא היה בסיפור כותב אותו מחדש מיד, בלי לחכות. כבוי חוסך את רוב הבקשות: רוב הכתיבות מחדש הן אתרים שמצטרפים.",
            checked = rewrite.onNewSite,
            onChange = { onRewrite(null, it, null) },
            enabled = admin,
        )
        ChoiceRow(
            title = "שמירת רזרבה",
            subtitle = "אחרי מספר הבקשות הזה ביום נכתבים רק סיפורים חדשים, לא עדכונים.",
            options = (listOf(0, 300, 350) + rewrite.reserveAfter).distinct().map { it to if (it == 0) "כבוי" else "אחרי $it בקשות ביום" },
            selected = rewrite.reserveAfter,
            onSelect = { onRewrite(null, null, it) },
            enabled = admin,
        )
    }
    if (fullArticles != null) {
        SwitchRow(
            title = "קרא כתבות מלאות",
            subtitle = "כותרת, כותרת משנה וגוף הכתבה המלא, מנוקה. כבוי: 600 התווים הראשונים.",
            checked = fullArticles,
            onChange = onFullArticles,
            enabled = admin,
        )
    }
    ChoiceRow(
        title = "שורה בסיפורים קשורים",
        subtitle = "המשפט שמופיע מתחת לכותרת של סיפור קשור, ובמפת הפרשות. תמיד שלם.",
        options = RelatedLine.entries.map { it to it.title },
        selected = settings.relatedLine,
        onSelect = { onChange(settings.copy(relatedLine = it)) },
    )
    ChoiceRow(
        title = "משפטים בכרטיס בפיד",
        subtitle = "כמה משפטים מהכתבה המסוכמת מופיעים בכרטיס שלה בפיד. הכתבה המלאה נפתחת בלחיצה על הכותרת.",
        options = FEED_SENTENCES_CHOICES.map { it to if (it == 0) "ללא הגבלה" else "$it" },
        selected = settings.feedSentences,
        onSelect = { onChange(settings.copy(feedSentences = it)) },
    )
    SwitchRow(
        title = "רק הגרסה האחרונה של כל סיפור",
        subtitle = "כשסיפור נכתב מחדש (הצטרפו אליו כתבות), הפיד מראה רק את הגרסה החדשה. בדף הכתבה: מה השתנה מהגרסה הקודמת, מודגש, וכל הגרסאות הקודמות. כבוי: כל קבוצת כתבות בכרטיס משלה, גם כשכמה מהן גרסאות של אותו סיפור.",
        checked = settings.newestVersionOnly,
        onChange = { onChange(settings.copy(newestVersionOnly = it)) },
    )
    SwitchRow(
        title = "כל משפט בשורה חדשה",
        subtitle = "בכתבה מסוכמת, כל משפט מתחיל בשורה חדשה (אחרי תוויות המקורות שלו כשהן בתוך הטקסט). כבוי: פסקה אחת רציפה.",
        checked = settings.sentenceLines,
        onChange = { onChange(settings.copy(sentenceLines = it)) },
    )
    // The server's grouping: read from the server and changed there.
    if (serverGrouping != null) {
        // Who groups: the model, or Gemini (only when the server has its key). With Gemini the model's strictness and
        // text don't apply: dimmed, with a note.
        if (serverGrouping.geminiAvailable) {
            ChoiceRow(
                title = "קיבוץ לפי",
                subtitle = if (serverGrouping.byGemini) "Gemini משבץ כל סבב את הכותרות החדשות בסיפורים; כשהוא לא עונה, המודל מקבץ." else "מודל המשמעות בשרת, לפי רמת ההתאמה.",
                options = listOf(false to "מודל", true to "Gemini"),
                selected = serverGrouping.byGemini,
                onSelect = onServerGroupingMethod,
                enabled = admin,
            )
        }
        InfoRow("מודל", "${serverGrouping.model} · בשרת (נקבע בהגדרות השרת)")
        Column(Modifier.alpha(if (serverGrouping.byGemini) 0.45f else 1f)) {
            if (serverGrouping.byGemini) {
                Text(
                    "בקיבוץ של Gemini, רמת ההתאמה וההשוואה חלות רק כשהמודל מחליף אותו.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            StrictnessRow(serverGrouping.strictness, enabled = admin) { onServerGrouping(it, null, null) }
            ChoiceRow(
                title = "השוואה לפי",
                subtitle = storyTextNote(serverGrouping.text) + " בשרת.",
                options = STORY_TEXT_CHOICES,
                selected = serverGrouping.text,
                onSelect = { onServerGrouping(null, it, null) },
                enabled = admin,
            )
        }
        ChoiceRow(
            title = "טווח הסיפורים",
            subtitle = "השרת בונה סיפורים רק מכותרות מהימים האחרונים. ״הכל״: מכל הכותרות שהוא שומר.",
            options = (STORY_DAYS_CHOICES + serverGrouping.days).distinct().map { it to if (it == 0) "הכל" else if (it == 1) "יום" else "$it ימים" },
            selected = serverGrouping.days,
            onSelect = { onServerGrouping(null, null, it) },
            enabled = admin,
        )
    } else {
        InfoRow("קיבוץ", "השרת לא מקבץ סיפורים, או שעוד לא ענה")
    }

    Section("חיפוש לפי נושא")
    TopicThresholdRow(settings.topicThreshold, settings.topics, topicCounts) { onChange(settings.copy(topicThreshold = it)) }

    Section("רענון ברקע")
    ChoiceRow(
        title = "רענון בחזרה לאפליקציה",
        subtitle = "כשחוזרים לאפליקציה והרענון האחרון היה לפני יותר מהזמן הזה, הכותרות מתרעננות מעצמן.",
        options = REFRESH_ON_RETURN_CHOICES.map { it to if (it == 0) "כבוי" else "$it דק׳" },
        selected = settings.refreshOnReturnMinutes,
        onSelect = { onChange(settings.copy(refreshOnReturnMinutes = it)) },
    )
    ChoiceRow(
        title = "תדירות",
        subtitle = "כל כמה זמן להביא כותרות וכתבות מהשרת כשהאפליקציה סגורה. אנדרואיד עשוי להמתין יותר כשהטלפון במנוחה.",
        options = BACKGROUND_INTERVALS.map { it to intervalName(it) },
        selected = settings.backgroundMinutes,
        onSelect = { onChange(settings.copy(backgroundMinutes = it)) },
    )
    val notifyWhat = listOfNotNull(
        if (settings.notifySources.isEmpty()) null else "${settings.notifySources.size} אתרים",
        if (settings.notifyCategories.isEmpty()) null else "${settings.notifyCategories.size} נושאים",
    ).joinToString(" · ").ifEmpty { "כל האתרים והנושאים" }
    NavRow(Icons.Filled.Notifications, "התראות", if (settings.notifications) "כתבות חדשות מהרענון ברקע · $notifyWhat" else "כבויות") { onOpen(SettingsPage.NOTIFICATIONS) }
    val day = backgroundRuns.filter { it.startedAt > System.currentTimeMillis() - DAY_MS }
    NavRow(
        Icons.Filled.History, "יומן הרענונים",
        if (day.isEmpty()) "עדיין לא רץ ברקע" else "${day.size} ריצות ב-24 השעות האחרונות · ${size(day.sumOf { it.bytesReceived })} הורדו",
    ) { onOpen(SettingsPage.LOG) }

    Section("נתונים")
    NavRow(
        Icons.Filled.Storage, "אחסון וארכיון",
        "${storage.cachedArticles} כתבות · ${size(storage.feedBytes + storage.imageBytes)} בטלפון" +
            (if (storage.archiveFolder != null) " · בארכיון ${storage.archivedArticles} כתבות" else " · בלי ארכיון"),
    ) { onOpen(SettingsPage.DATA) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    NavRow(Icons.Filled.Info, "אודות", "גרסה $version · ${sources.size} אתרים") { onOpen(SettingsPage.ABOUT) }
}

@Composable
private fun DataPage(
    storage: StorageInfo,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onChoose: (Uri) -> Unit,
    onStop: () -> Unit,
    archiveImport: ArchiveImport?,
    onImport: () -> Unit,
    onArchiveNow: () -> Unit,
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Section("בטלפון")
    InfoRow("כתבות ופיד", "${storage.cachedArticles} כתבות · ${size(storage.feedBytes)}")
    InfoRow("תמונות", size(storage.imageBytes))
    InfoRow("סך הכול", size(storage.feedBytes + storage.imageBytes))
    ChoiceRow(
        title = "מקום לכתבות",
        subtitle = "הכתבות נשמרות גם אחרי שירדו מהפיד, כך שלא יורדות שוב, ובגלילה למטה בפיד מופיעות הקודמות. מעבר לזה הישנות נמחקות; התמונות והסרטונים נספרים בנפרד.",
        options = ARTICLE_CACHE_SIZES.map { mb ->
            mb to when {
                mb == 0 -> "ללא"
                mb >= 1024 -> "${mb / 1024}GB"
                else -> "${mb}MB"
            }
        },
        selected = settings.articleCacheMb,
        onSelect = { onChange(settings.copy(articleCacheMb = it)) },
    )
    ChoiceRow(
        title = "מקום לתמונות",
        subtitle = "מעבר לזה התמונות הישנות נמחקות. ללא הגבלה: כל עוד נשאר 1GB פנוי בטלפון. חל מהפתיחה הבאה של האפליקציה.",
        options = IMAGE_CACHE_SIZES.map { mb ->
            mb to when {
                mb == 0 -> "ללא"
                mb >= 1024 -> "${mb / 1024}GB"
                else -> "${mb}MB"
            }
        },
        selected = settings.imageCacheMb,
        onSelect = { onChange(settings.copy(imageCacheMb = it)) },
    )
    ChoiceRow(
        title = "מקום לסרטונים",
        subtitle = "סרטונים שמתנגנים נשמרים במטמון כדי שלא יירדו שוב; מעבר לזה הישנים נמחקים.",
        options = VIDEO_CACHE_SIZES.map { mb -> mb to if (mb >= 1024) "${mb / 1024}GB" else "${mb}MB" },
        selected = settings.videoCacheMb,
        onSelect = { onChange(settings.copy(videoCacheMb = it)) },
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Muted("הניקוי מסיר את הכתבות והתמונות השמורות; הן יורדות שוב כשצריך. הארכיון נשאר.")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClearCache) { Text("ניקוי המטמון") }
    }
    Section("ארכיון")
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onChoose) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Muted(
            "שומר כל כתבה שהאפליקציה מורידה, עם תמונותיה והסרטונים שנוגנו, בתיקייה שתבחרו, לפי שנה / חודש / יום / אתר. " +
                "ההעתקה לתיקייה נעשית פעם ביום, בזמן טעינה, מהמטמון. התיקייה נשארת גם אחרי הסרת האפליקציה; אחרי התקנה " +
                "מחדש בוחרים אותה שוב. התמונות מוסתרות מהגלריה.",
        )
        Spacer(Modifier.height(8.dp))
        val folder = storage.archiveFolder
        when {
            folder == null -> Button(onClick = { pick.launch(null) }) { Text("בחירת תיקיית ארכיון") }
            !storage.archiveAvailable -> {
                Text("אין גישה לתיקיית הארכיון. בחרו אותה שוב.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pick.launch(Uri.parse(folder)) }) { Text("בחירת תיקיית ארכיון") }
            }
            else -> {
                Text(archiveName(folder), style = MaterialTheme.typography.bodyLarge)
                Muted("${storage.archivedArticles} כתבות · " + (storage.archiveBytes?.let(::size) ?: "סופר…"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick.launch(Uri.parse(folder)) }) { Text("החלפת תיקייה") }
                    OutlinedButton(onClick = onStop) { Text("הפסקת הארכוב") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onArchiveNow) { Text("ארכוב עכשיו") }
                Spacer(Modifier.height(16.dp))
                Muted("הכותרות של כל הכתבות בארכיון מצטרפות לפיד, מתחת לכותרות האחרונות, בגלילה למטה. הכתבות עצמן נשארות בארכיון ונפתחות ממנו. לוקח כמה דקות.")
                Spacer(Modifier.height(8.dp))
                when {
                    archiveImport?.running == true -> {
                        LinearProgressIndicator(
                            progress = { if (archiveImport.total > 0) archiveImport.read.toFloat() / archiveImport.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Muted("מייבא… ${archiveImport.read} מתוך ${archiveImport.total} · ${archiveImport.added} כותרות חדשות")
                    }
                    else -> {
                        OutlinedButton(onClick = onImport) { Text("ייבוא כותרות מהארכיון לפיד") }
                        archiveImport?.let { Muted("יובאו ${it.added} כותרות מתוך ${it.read} כתבות") }
                    }
                }
            }
        }
    }
}

/** What the background refreshes cost: the last 24 hours in sum, then the latest runs one per line. */
@Composable
private fun LogPage(runs: List<BackgroundRun>, onClear: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (runs.isEmpty()) {
            Muted("עדיין לא רץ רענון ברקע.")
            return@Column
        }
        val day = runs.filter { it.startedAt > System.currentTimeMillis() - DAY_MS }
        val onBattery = day.filter { !it.charging }
        Text("24 השעות האחרונות", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted(
            "${day.size} ריצות · ${seconds(day.sumOf { it.durationMs })} ריצה · מעבד ${seconds(day.sumOf { it.cpuMs })} · " +
                Formatter.formatShortFileSize(context, day.sumOf { it.bytesReceived }) + " הורדו",
        )
        if (onBattery.size >= 2) {
            Muted("הסוללה בריצות האלה: מ-${onBattery.last().batteryPercent}% ל-${onBattery.first().batteryPercent}% (כל הטלפון)")
        }
        Spacer(Modifier.height(16.dp))
        Text("ריצות אחרונות", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        runs.take(12).forEach { run ->
            val time = DateFormat.getTimeFormat(context).format(Date(run.startedAt))
            Muted(
                "$time · ${seconds(run.durationMs)} · מעבד ${seconds(run.cpuMs)} · " +
                    Formatter.formatShortFileSize(context, run.bytesReceived) +
                    (if (run.storiesMs > 0) " · סיפורים ${seconds(run.storiesMs)}" else "") +
                    (if (run.notified > 0) " · ${run.notified} התראות" else "") + " · ${run.batteryPercent}%" +
                    (if (run.charging) " בטעינה" else "") + (run.error?.let { " · נכשל: $it" } ?: ""),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClear) { Text("ניקוי היומן") }
    }
}

@Composable
private fun AboutPage(sources: List<Site>) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    InfoRow("גרסה", version)
    InfoRow("אתרים", sources.joinToString(", ") { it.name })
    InfoRow("מקור החדשות", "שרת החדשות שלכם: הכותרות, הכתבות, הסיפורים והסיכומים מגיעים ממנו.")
}

/** "primary:Documents/News archive" → "Documents/News archive". */
private fun archiveName(treeUri: String): String =
    Uri.decode(treeUri.substringAfterLast("/tree/")).substringAfter(':').ifEmpty { "אחסון הטלפון" }

/** A wait between a site's requests, as the choice shows it: ללא, 0.25 ש׳, 2 ש׳. */
private fun waitName(ms: Int): String = when {
    ms == 0 -> "ללא"
    ms < 1000 -> "${ms / 1000.0}".trimEnd('0') + " ש׳"
    ms % 1000 == 0 -> "${ms / 1000} ש׳"
    else -> "${ms / 1000.0} ש׳"
}

private fun storyTextNote(text: StoryText): String = when (text) {
    StoryText.TITLE -> "הכותרת בלבד: אין צורך לחכות לכתבות, אבל כותרות קצרות מטעות לפעמים."
    StoryText.SUBTITLE -> "כותרת המשנה של הכתבה (או הפסקה הראשונה)."
    StoryText.TITLE_AND_SUBTITLE -> "הכותרת וכותרת המשנה כטקסט אחד. הכי מדויק בבדיקה."
    StoryText.TITLE_TIMES_SUBTITLE -> "הכותרת וכותרת המשנה בנפרד, ושתיהן צריכות להיות דומות. פי שניים חישוב."
}

private val STORY_TEXT_CHOICES = listOf(
    StoryText.TITLE to "כותרת",
    StoryText.SUBTITLE to "משנה",
    StoryText.TITLE_AND_SUBTITLE to "שתיהן",
    StoryText.TITLE_TIMES_SUBTITLE to "מכפלה",
)

/** The waits offered for a site on the server (its waitMs), in milliseconds. */
private val SITE_WAITS_MS = listOf(250, 0, 500, 1000, 2000)

/** The days the server's stories are grouped from (its grouping days), 0: all. */
private val STORY_DAYS_CHOICES = listOf(1, 2, 3, 7, 14, 0)

private fun intervalName(minutes: Int): String = when (minutes) {
    0 -> "כבוי"
    60 -> "שעה"
    else -> "$minutes דק׳"
}

private fun seconds(ms: Long) = "%.1f שנ׳".format(ms / 1000.0)

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A row that opens a page of its own. */
@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    inset: Dp = 0.dp,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = 16.dp + inset, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Muted(it) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * A choice: one row, its title, its explanation (two lines at most) and, at its end, the value chosen with an
 * up-and-down mark (↕: pick one of these, not another screen). A tap opens a bottom sheet with the whole explanation
 * and every choice on its own line with a radio button; picking one closes it. From the UI lab's settings round, a3
 * (docs/ux-history/settings/).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    inset: Dp = 0.dp,
    /** False: shown read-only (dimmed, no sheet). */
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).clickable(enabled = enabled) { open = true }.padding(start = 16.dp + inset, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(current, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Filled.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                }
                Spacer(Modifier.height(8.dp))
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value); open = false }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Muted(value)
    }
}


/**
 * How strictly stories group headlines, a slider from 10 to 90: higher keeps only clearly the same event together,
 * lower brings in related coverage too. The stories are found again once the thumb is let go, not while it moves.
 */
@Composable
private fun StrictnessRow(value: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    var moving by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("קיבוץ", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${moving.roundToInt()}", style = MaterialTheme.typography.titleMedium)
        }
        Muted("גבוה מקבץ רק אירועים זהים בבירור; נמוך מצרף גם סיקור קרוב. 30, 50 ו-70 הם המרחיב, המאוזן והמחמיר מקודם.")
        Slider(
            value = moving,
            onValueChange = { moving = it },
            onValueChangeFinished = { onChange(moving.roundToInt()) },
            enabled = enabled,
            valueRange = STRICTNESS_MIN.toFloat()..STRICTNESS_MAX.toFloat(),
            steps = (STRICTNESS_MAX - STRICTNESS_MIN) / STRICTNESS_STEP - 1,
        )
    }
}

/**
 * Which new articles the background refresh notifies: the sites and the categories picked, as pills ("הכל": none
 * picked, every one).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsPage(settings: AppSettings, sources: List<Site>, onChange: (AppSettings) -> Unit) {
    Muted(
        "התראות על כתבות חדשות מגיעות מהרענון ברקע (תדירות, בהגדרות). כאן בוחרים מאילו אתרים ונושאים; בלי בחירה, מכולם.",
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    SwitchRow(
        title = "התראות על כתבות חדשות",
        subtitle = when {
            !settings.notifications -> "כבוי: הרענון ברקע לא מציג התראות."
            settings.backgroundMinutes == 0 -> "הרענון ברקע כבוי (תדירות, בהגדרות), אז עוד לא יגיעו התראות."
            else -> "כל רענון ברקע מודיע על הכתבות החדשות מהאתרים והנושאים שנבחרו למטה."
        },
        checked = settings.notifications,
        onChange = { onChange(settings.copy(notifications = it)) },
    )
    SwitchRow(
        title = "התראה אחת לכל הכתבות",
        subtitle = "כל רענון מוסיף את הכתבות החדשות לאותה התראה, כותרת מתחת לכותרת, במקום התראה לכל כתבה. לחיצה עליה פותחת את הפיד.",
        checked = settings.combineNotifications,
        onChange = { onChange(settings.copy(combineNotifications = it)) },
    )
    Section("אתרים")
    PillPicker(
        options = sources.filter { it.id !in settings.hiddenSources }.map { it.id to it.name },
        chosen = settings.notifySources,
    ) { onChange(settings.copy(notifySources = it)) }
    Section("נושאים")
    PillPicker(
        options = (Categories.IDS + OTHER_CATEGORY).map { id -> id to (if (id == OTHER_CATEGORY) categoryName(null) else categoryName(id)) },
        chosen = settings.notifyCategories,
    ) { onChange(settings.copy(notifyCategories = it)) }
}

/** [options] (id to name) as pills wrapped in rows, those [chosen] filled; "הכל" first, filled when none is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillPicker(options: List<Pair<String, String>>, chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = chosen.isEmpty(), onClick = { onChange(emptySet()) }, label = { Text("הכל") })
            options.forEach { (id, name) ->
                FilterChip(
                    selected = id in chosen,
                    onClick = { onChange(if (id in chosen) chosen - id else chosen + id) },
                    label = { Text(name) },
                )
            }
        }
    }
}
