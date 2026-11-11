package news.app

import android.content.Context

data class AppSettings(
    /** Source ids the server doesn't read (switched off in Settings › אתרים, on the server): not shown. */
    val hiddenSources: Set<String> = emptySet(),
    /** A headline is new ([newUrlsOf]) only if published in this many last hours, besides not viewed or read; 0: no limit. */
    val newWithinHours: Int = 24,
    /** A fling of the feed ends with a row's top at the top of the screen ([rememberRowSnapFling]). */
    val snapToRows: Boolean = true,
    /** A story's picture in the feed under its title, subtitle and category, right above its other reports. */
    val pictureBelowText: Boolean = true,
    /** Where a generated story's card shows its sources' labels. */
    val sourceLabels: SourceLabels = SourceLabels.BELOW,
    /** A generated story's sentences each on a new line (after its labels when they are inline). */
    val sentenceLines: Boolean = false,
    /** The sentence a related story shows under its title (סיפורים קשורים, the map's story). */
    val relatedLine: RelatedLine = RelatedLine.MOST_CITED,
    /** Labels under the feed's title for the sites and categories picked (with an ✕ each); hidden by default. */
    val showFilterLabels: Boolean = false,
    /** While reading, load this many of the next unread articles of the reading queue, with their pictures; 0 is off. */
    val readAhead: Int = 3,
    /** While scrolling the feed, download the pictures of this many rows below the screen; 0 is off. */
    val listAhead: Int = 20,
    /** The sites whose new articles are notified ([NewArticleNotifications]); empty: every site. */
    val notifySources: Set<String> = emptySet(),
    /** The categories whose new articles are notified ([OTHER_CATEGORY] for none); empty: every category. By default the news ones. */
    val notifyCategories: Set<String> = setOf("israel", "politics", "security", "world", "economy", "law", "tech", "health", OTHER_CATEGORY),
    /** Refresh on coming back to the app when the feed is more than this many minutes old; 0 is off. */
    val refreshOnReturnMinutes: Int = 2,
    /** Refresh in the background every this many minutes ([BackgroundRefresh]); 0 is off. */
    val backgroundMinutes: Int = 30,
    /** The archive folder's tree URI ([ArticleArchive]), or null when not archiving. */
    val archiveFolder: String? = null,
    /** The picture cache's size in MB, its oldest pictures going first past it; 0: as big as leaves 1 GB free. */
    val imageCacheMb: Int = 0,
    /** The streamed-video cache's size in MB, its oldest videos going first past it. */
    val videoCacheMb: Int = 512,
    /**
     * The article cache's size in MB ([FeedCache.trim]): articles and their pages, kept after they leave the feed, the
     * oldest going first past it; 0: no limit. Pictures and videos have their own ([imageCacheMb], [videoCacheMb]).
     */
    val articleCacheMb: Int = 1024,
    /** The saved topics of the topic search, in the order they were added; those [SavedTopic.on] filter the feed. */
    val topics: List<SavedTopic> = emptyList(),
    /**
     * How much of a topic's words a headline must mention to show, 10 to 100 percent ([news.app.stories.wordScores]).
     */
    val topicThreshold: Int = 55,
    /**
     * New articles in one notification that each background refresh updates, its headlines one under another, instead
     * of a notification per article ([NewArticleNotifications]).
     */
    val combineNotifications: Boolean = false,
    /** A video that plays stays at the top of the article while scrolling down, so the text can be read beside it. */
    val stickyVideo: Boolean = true,
)

/** A topic saved in the topic search: the words the owner typed, and whether it filters the feed now. */
data class SavedTopic(val text: String, val on: Boolean)

/** [topics] as one string, a line each ("1" or "0" for on, a tab, the text), in order. */
internal fun encodeTopics(topics: List<SavedTopic>): String =
    topics.joinToString("\n") { (if (it.on) "1" else "0") + "\t" + it.text.replace('\n', ' ').replace('\t', ' ') }

/** What [encodeTopics] wrote; a damaged line is left out. */
internal fun decodeTopics(saved: String?): List<SavedTopic> =
    saved.orEmpty().lines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab != 1 || line[0] !in "01" || line.length == 2) null else SavedTopic(line.substring(2), line[0] == '1')
    }

/** The choices offered for [AppSettings.imageCacheMb] (0: no limit but the phone's free space). */
val IMAGE_CACHE_SIZES = listOf(256, 512, 1024, 2048, 0)

/** The choices offered for [AppSettings.articleCacheMb] (0: no limit). */
val ARTICLE_CACHE_SIZES = listOf(256, 512, 1024, 2048, 0)

/** The choices offered for [AppSettings.videoCacheMb]. */
val VIDEO_CACHE_SIZES = listOf(256, 512, 1024, 2048)

/** The choices offered for [AppSettings.listAhead]. */
val LIST_AHEAD_COUNTS = listOf(0, 10, 20, 50)

/** The choices offered for [AppSettings.refreshOnReturnMinutes] (0: off). */
val REFRESH_ON_RETURN_CHOICES = listOf(1, 2, 5, 10, 30, 0)

/** The choices offered for [AppSettings.newWithinHours] (0: no limit). */
val NEW_WITHIN_HOURS_CHOICES = listOf(6, 12, 24, 48, 0)

/** The choices offered for [AppSettings.readAhead]. */
val READ_AHEAD_COUNTS = listOf(0, 3, 5, 10)

/** The choices offered for [AppSettings.backgroundMinutes]. */
val BACKGROUND_INTERVALS = listOf(0, 2, 5, 15, 30, 60)

/** [AppSettings] in the app's private SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            hiddenSources = prefs.getStringSet(HIDDEN_SOURCES, null)?.toSet() ?: defaults.hiddenSources,
            showFilterLabels = prefs.getBoolean(SHOW_FILTER_LABELS, defaults.showFilterLabels),
            snapToRows = prefs.getBoolean(SNAP_TO_ROWS, defaults.snapToRows),
            newWithinHours = prefs.getInt(NEW_WITHIN_HOURS, defaults.newWithinHours),
            pictureBelowText = prefs.getBoolean(PICTURE_BELOW_TEXT, defaults.pictureBelowText),
            sentenceLines = prefs.getBoolean(SENTENCE_LINES, defaults.sentenceLines),
            relatedLine = prefs.getString(RELATED_LINE, null)?.let { n -> RelatedLine.entries.firstOrNull { it.name == n } } ?: defaults.relatedLine,
            sourceLabels = prefs.getString(SOURCE_LABELS, null)?.let { n -> SourceLabels.entries.firstOrNull { it.name == n } } ?: defaults.sourceLabels,
            refreshOnReturnMinutes = prefs.getInt(REFRESH_ON_RETURN_MINUTES, defaults.refreshOnReturnMinutes),
            notifySources = prefs.getStringSet(NOTIFY_SOURCES, null)?.toSet() ?: defaults.notifySources,
            notifyCategories = prefs.getStringSet(NOTIFY_CATEGORIES, null)?.toSet() ?: defaults.notifyCategories,
            readAhead = prefs.getInt(READ_AHEAD, defaults.readAhead),
            listAhead = prefs.getInt(LIST_AHEAD, defaults.listAhead),
            backgroundMinutes = prefs.getInt(BACKGROUND_MINUTES, defaults.backgroundMinutes),
            archiveFolder = prefs.getString(ARCHIVE_FOLDER, defaults.archiveFolder),
            imageCacheMb = prefs.getInt(IMAGE_CACHE_MB, defaults.imageCacheMb),
            videoCacheMb = prefs.getInt(VIDEO_CACHE_MB, defaults.videoCacheMb),
            articleCacheMb = prefs.getInt(ARTICLE_CACHE_MB, defaults.articleCacheMb),
            topics = decodeTopics(prefs.getString(TOPICS, null)),
            topicThreshold = prefs.getInt(TOPIC_THRESHOLD, defaults.topicThreshold),
            combineNotifications = prefs.getBoolean(COMBINE_NOTIFICATIONS, defaults.combineNotifications),
            stickyVideo = stickyVideo,
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putStringSet(HIDDEN_SOURCES, settings.hiddenSources)
            .putBoolean(SHOW_FILTER_LABELS, settings.showFilterLabels)
            .putBoolean(SNAP_TO_ROWS, settings.snapToRows)
            .putInt(NEW_WITHIN_HOURS, settings.newWithinHours)
            .putBoolean(PICTURE_BELOW_TEXT, settings.pictureBelowText)
            .putString(SOURCE_LABELS, settings.sourceLabels.name)
            .putBoolean(SENTENCE_LINES, settings.sentenceLines)
            .putString(RELATED_LINE, settings.relatedLine.name)
            .putInt(REFRESH_ON_RETURN_MINUTES, settings.refreshOnReturnMinutes)
            .putStringSet(NOTIFY_SOURCES, settings.notifySources)
            .putStringSet(NOTIFY_CATEGORIES, settings.notifyCategories)
            .putInt(READ_AHEAD, settings.readAhead)
            .putInt(LIST_AHEAD, settings.listAhead)
            .putInt(BACKGROUND_MINUTES, settings.backgroundMinutes)
            .putString(ARCHIVE_FOLDER, settings.archiveFolder)
            .putInt(IMAGE_CACHE_MB, settings.imageCacheMb)
            .putInt(VIDEO_CACHE_MB, settings.videoCacheMb)
            .putInt(ARTICLE_CACHE_MB, settings.articleCacheMb)
            .putString(TOPICS, encodeTopics(settings.topics))
            .putInt(TOPIC_THRESHOLD, settings.topicThreshold)
            .putBoolean(COMBINE_NOTIFICATIONS, settings.combineNotifications)
            .putBoolean(STICKY_VIDEO, settings.stickyVideo)
            .apply()
    }

    /** [AppSettings.stickyVideo], read on its own where a video is drawn. */
    val stickyVideo: Boolean get() = prefs.getBoolean(STICKY_VIDEO, AppSettings().stickyVideo)

    /** The sites and categories the feed was last filtered by (empty: all), kept across restarts of the app. */
    fun loadFilters(): Pair<Set<String>, Set<String>> =
        prefs.getStringSet(SHOWN_SITES, null).orEmpty().toSet() to prefs.getStringSet(SHOWN_CATEGORIES, null).orEmpty().toSet()

    fun saveFilters(sites: Set<String>, categories: Set<String>) {
        prefs.edit().putStringSet(SHOWN_SITES, sites).putStringSet(SHOWN_CATEGORIES, categories).apply()
    }

    /**
     * Only the stories with a generated article (the default), or the original ones too (the bottom bar's toggle),
     * remembered like the site and category filters. A new key: before, the default was all of them.
     */
    var onlyGenerated: Boolean
        get() = prefs.getBoolean("only_generated_v2", true)
        set(value) = prefs.edit().putBoolean("only_generated_v2", value).apply()

    /** The threads map's view last chosen ("מפה" or "רצועות"). */
    var threadMapView: ThreadMapView
        get() = enumOrNull<ThreadMapView>(prefs.getString("thread_map_view", null)) ?: ThreadMapView.MAP
        set(value) = prefs.edit().putString("thread_map_view", value.name).apply()

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

    private companion object {
        const val HIDDEN_SOURCES = "hidden_sources"
        const val SHOWN_SITES = "shown_sites"
        const val SHOWN_CATEGORIES = "shown_categories"
        const val SHOW_FILTER_LABELS = "show_filter_labels"
        const val SNAP_TO_ROWS = "snap_to_rows"
        const val NEW_WITHIN_HOURS = "new_within_hours"
        const val PICTURE_BELOW_TEXT = "picture_below_text"
        const val SOURCE_LABELS = "source_labels"
        const val SENTENCE_LINES = "sentence_lines"
        const val RELATED_LINE = "related_line"
        const val REFRESH_ON_RETURN_MINUTES = "refresh_on_return_minutes"
        const val NOTIFY_SOURCES = "notify_sources"
        const val NOTIFY_CATEGORIES = "notify_categories"
        const val READ_AHEAD = "read_ahead"
        const val LIST_AHEAD = "list_ahead"
        const val BACKGROUND_MINUTES = "background_minutes"
        const val ARCHIVE_FOLDER = "archive_folder"
        const val IMAGE_CACHE_MB = "image_cache_mb"
        const val VIDEO_CACHE_MB = "video_cache_mb"
        const val ARTICLE_CACHE_MB = "article_cache_mb"
        const val TOPICS = "topics"
        const val TOPIC_THRESHOLD = "topic_threshold"
        const val COMBINE_NOTIFICATIONS = "combine_notifications"
        const val STICKY_VIDEO = "sticky_video"

    }
}
