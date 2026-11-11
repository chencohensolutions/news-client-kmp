package news.app.lab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import news.app.AppSettings
import news.app.ArticleDownloads
import news.app.ArticleScreen
import news.app.ArticleState
import news.app.HeadlinesScreen
import news.app.HeadlinesState
import news.app.FeedView
import news.app.HeadlinesView
import news.app.Site
import news.app.ReaderScreen
import news.app.ReaderState
import news.app.SettingsScreen
import news.app.SavedTopic
import news.app.StoriesState
import news.app.SummaryArticleScreen
import news.app.server.StorySummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.app.stories.newestFirst
import news.app.stories.topicMatches
import news.core.model.Article
import news.core.model.ArticleSummary
import kotlin.time.Instant

// The UI lab's library (UiLabActivity): every screen, each with the app's current version first and then the
// suggestions for it. A suggestion lives in its own folder, lab/<screen>/<option>/, and adds one line to its screen's
// list here, after `current` (the ui-lab skill). When one is chosen it moves to src/main and replaces the current one.

/** The screen as the app draws it now, always the first option. */
private fun current(content: @Composable () -> Unit) = LabOption("current", "Current", content = content)

internal val labScreens: List<LabScreen> = listOf(
    LabScreen("home", "Home: the three views as one screen", listOf(
        current { HeadlinesLab(HeadlinesView.STORIES, readyStories()) },
        LabOption("quiet", "Nothing new", "No new headlines since the last visit; no site failing") {
            HeadlinesLab(HeadlinesView.STORIES, readyStories(), newUrls = emptySet(), failures = emptyMap())
        },
        LabOption("filtered", "Filtered", "Only new, two sites and one category picked; the drawers work") {
            HeadlinesLab(HeadlinesView.STORIES, readyStories(), onlyNew = true, sites = setOf("harbor", "valley"), categories = setOf("politics"))
        },
        LabOption("downloads", "Downloading articles", "In advance: 120 of 251 saved, a bar under the title") {
            HeadlinesLab(HeadlinesView.STORIES, readyStories(), downloads = ArticleDownloads(done = 120, total = 251, running = true))
        },
    )),
    LabScreen("feed-nav", "Home: bottom bar + topic search", listOf(
        current { HeadlinesLab(HeadlinesView.ALL, readyStories(), topics = LAB_TOPICS.map { SavedTopic(it, on = false) }) },
        LabOption("topics", "Topics on", "מעבורת and חום on: their stories, whole, with the topic under the title") {
            HeadlinesLab(HeadlinesView.ALL, readyStories(), topics = LAB_TOPICS.map { SavedTopic(it, on = it == "מעבורת" || it == "חום") })
        },
    )),
    LabScreen("latest", "Headlines: latest", listOf(
        current { HeadlinesLab(HeadlinesView.LATEST) },
        LabOption("read", "Some read", "Opened headlines marked with ✓ נקרא") {
            HeadlinesLab(HeadlinesView.LATEST, read = PreviewData.headlines.take(3).map { it.url }.toSet())
        },
        LabOption("errors", "Two sites failing", "Two sites unreachable") {
            HeadlinesLab(HeadlinesView.LATEST, failures = mapOf("harbor" to "harbor: https://harbor.example/news returned 503", "valley" to "valley: https://valley.example/ returned 503"))
        },
        LabOption("empty", "Nothing loaded", "Offline on first launch") {
            HeadlinesLab(HeadlinesView.LATEST, items = emptyList())
        },
    )),
    LabScreen("categories", "Headlines: by category", listOf(
        current { HeadlinesLab(HeadlinesView.CATEGORIES) },
    )),
    LabScreen("stories", "Headlines: stories", listOf(
        current { HeadlinesLab(HeadlinesView.STORIES, readyStories()) },
        LabOption("read", "Some read", "A story with one report read, one with all") {
            HeadlinesLab(HeadlinesView.STORIES, readyStories(), read = (PreviewData.stories[0].reports.take(1) + PreviewData.stories[1].reports).map { it.url }.toSet())
        },
        LabOption("working", "Grouping") { HeadlinesLab(HeadlinesView.STORIES, StoriesState.Working) },
    )),
    LabScreen("article", "Article", listOf(
        current { ArticleLab(ArticleState.Loaded(PreviewData.articleSummary, PreviewData.article)) },
        LabOption("media", "Photos and video", "In-text photos with captions; a video in an embedded player") {
            ArticleLab(ArticleState.Loaded(PreviewData.mediaArticleSummary, PreviewData.mediaArticle))
        },
        LabOption("stream", "Video in the app's player", "A public MP4, played natively") {
            ArticleLab(ArticleState.Loaded(ArticleSummary(PreviewData.streamArticle.sourceId, PreviewData.streamArticle.url, PreviewData.streamArticle.title), PreviewData.streamArticle))
        },
        LabOption("loading", "Loading") { ArticleLab(ArticleState.Loading(PreviewData.articleSummary)) },
        LabOption("failed", "Failed") { ArticleLab(ArticleState.Failed(PreviewData.articleSummary, "הכתבה עוד לא בשרת")) },
    )),
    LabScreen("reader", "Reader", listOf(
        current { ReaderLab(PreviewData.stories.map { it.reports }, startPage = 1) },
        LabOption("headlines", "Single headlines", "From Latest or Categories: one article per page") {
            ReaderLab(PreviewData.headlines.map(::listOf), startPage = 0)
        },
        LabOption("video", "An article with a video", "The app's player; playing, it sticks below the status bar when scrolled") {
            val s = PreviewData.streamArticle
            ReaderLab(listOf(listOf(ArticleSummary(s.sourceId, s.url, s.title, imageUrl = s.imageUrl))), startPage = 0, article = s)
        },
    )),
    LabScreen("server", "Settings: the news server", serverLabOptions),
    LabScreen("threadmap", "Threads: the map (מפה / רצועות)", listOf(
        current { news.app.ThreadMapLab() },
    )),
    LabScreen("summaries", "Generated articles", listOf(
        current { HeadlinesLab(HeadlinesView.ALL, readyStories(), summaries = sampleSummaries()) },
        LabOption("article", "A generated article", "The page: numbered footnotes, what the sites disagree on, the sources") {
            val s = SummariesSample.stories[1]
            SummaryArticleScreen(s.story, s.toStorySummary(), PreviewData.sites.associate { it.id to it.name }, onFootnote = {}, onBack = {})
        },
    )),
    LabScreen("settings", "Settings", listOf(
        current { SettingsLab() },
        LabOption("topics", "Topic search", "Four saved topics, two on; the threshold slider") {
            SettingsLab(
                initial = AppSettings(topics = LAB_TOPICS.take(4).map { SavedTopic(it, on = it == "מעבורת" || it == "חום") }),
            )
        },
        LabOption("articles", "Bringing articles", "From the server, 120 of 251 done") { SettingsLab() },
    )),
)

/**
 * The headline list with made-up data: fetched four minutes ago, eight headlines new since the last visit, one site
 * failing. The view switch, site chips and category chips work; a pull fakes a refresh that brings one more story
 * (two made-up headlines) in after a moment, to show how arrivals animate; other taps do nothing.
 */
@Composable
private fun HeadlinesLab(
    view: HeadlinesView,
    stories: StoriesState = StoriesState.Idle,
    items: List<ArticleSummary> = PreviewData.headlines + PreviewData.storyHeadlines,
    failures: Map<String, String> = mapOf("valley" to "valley: https://valley.example/ returned 503"),
    read: Set<String> = PreviewData.read,
    newUrls: Set<String> = PreviewData.newUrls,
    downloads: ArticleDownloads = ArticleDownloads(),
    onlyNew: Boolean = false,
    sites: Set<String> = emptySet(),
    categories: Set<String> = emptySet(),
    topics: List<SavedTopic> = emptyList(),
    summaries: Map<String, StorySummary> = emptyMap(),
) {
    var state by remember {
        mutableStateOf(
            HeadlinesState(
                items = items.distinctBy { it.url }, failures = failures, view = view, stories = stories, read = read,
                refreshedAt = if (items.isEmpty()) null else PreviewData.refreshedAt, newUrls = newUrls, onlyNew = onlyNew, sites = sites,
                categories = categories, summaries = summaries,
            ),
        )
    }
    val scope = rememberCoroutineScope()
    // The topic search over made-up closeness ([LAB_TOPIC_SCORES]); adding, switching and deleting work.
    var saved by remember { mutableStateOf(topics) }
    val on = saved.filter { it.on }.map { it.text }
    val shown = state.copy(topicMatches = if (on.isEmpty()) null else topicMatches(LAB_TOPIC_SCORES, on, LAB_THRESHOLD))
    HeadlinesScreen(
        state = shown,
        feed = remember(shown) { FeedView.of(shown) },
        downloads = downloads,
        sources = labSources(),
        onRefresh = {
            if (!state.refreshing) scope.launch {
                state = state.copy(refreshing = true)
                delay(1500)
                val incoming = PreviewData.incomingStory
                val ready = state.stories as? StoriesState.Ready
                val arrived = state.items.any { it.url == incoming.lead.url }
                state = state.copy(
                    refreshing = false,
                    refreshedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                    items = if (arrived) state.items else (incoming.reports + state.items).distinctBy { it.url },
                    stories = if (arrived || ready == null) state.stories else ready.copy(stories = listOf(incoming) + ready.stories, forItems = incoming.reports + ready.forItems),
                    newUrls = state.newUrls + incoming.reports.map { it.url },
                )
            }
        },
        onShowSites = { state = state.copy(sites = it) },
        onShowCategories = { state = state.copy(categories = it) },
        onShowOnlyNew = { state = state.copy(onlyNew = it) },
        onOpenSettings = {},
        onOpen = {},
        topics = saved,
        topicCounts = saved.associate { t -> t.text to LAB_TOPIC_SCORES[t.text].orEmpty().count { it.value >= LAB_THRESHOLD } },
        topicThreshold = LAB_THRESHOLD,
        onAddTopic = { text -> if (saved.none { it.text == text }) saved = saved + SavedTopic(text.trim(), on = true) },
        onToggleTopic = { text -> saved = saved.map { if (it.text == text) it.copy(on = !it.on) else it } },
        onRemoveTopic = { text -> saved = saved.filterNot { it.text == text } },
    )
}

private val LAB_TOPICS = listOf("מעבורת", "חום", "ספרייה", "רכבת", "אוכל")

private const val LAB_THRESHOLD = 55

/** The sample topics scored against the sample headlines by their words, as the app does ([news.app.stories.wordScores]). */
private val LAB_TOPIC_SCORES: Map<String, Map<String, Int>> =
    LAB_TOPICS.associateWith { news.app.stories.wordScores(it, PreviewData.headlines + PreviewData.storyHeadlines) }

private fun readyStories() = StoriesState.Ready(newestFirst(PreviewData.stories, PreviewData.storyHeadlines), PreviewData.storyHeadlines, 0.88f)

/** The reader over made-up pages; every article is the sample one under that headline. Swipes, tabs and read marks work. */
@Composable
private fun ReaderLab(pages: List<List<ArticleSummary>>, startPage: Int, article: Article = PreviewData.article) {
    var read by remember { mutableStateOf(emptySet<String>()) }
    val articles = remember(pages) {
        pages.flatten().associate { s ->
            s.url to ArticleState.Loaded(s, article.copy(sourceId = s.sourceId, url = s.url, title = s.title, imageUrl = s.imageUrl, publishedAt = s.publishedAt))
        }
    }
    ReaderScreen(
        reader = ReaderState(pages, startPage, 0),
        articles = articles,
        read = read,
        sources = labSources(),
        onLoad = {},
        onRetry = {},
        onRead = { read = read + it.url },
        onBack = {},
    )
}

@Composable
private fun ArticleLab(state: ArticleState) {
    val source = labSources().firstOrNull { it.id == state.summary.sourceId }
    ArticleScreen(state, source?.name.orEmpty(), source?.language, onBack = {}, onRetry = {})
}

/** Settings with made-up data; the switches and choices work while it is open. */
@Composable
private fun SettingsLab(
    stories: StoriesState = StoriesState.Idle,
    initial: AppSettings = AppSettings(),
    downloads: ArticleDownloads = ArticleDownloads(done = 120, total = 251, running = true),
) {
    var settings by remember { mutableStateOf(initial) }
    SettingsScreen(
        settings, labSources(), stories, downloads, PreviewData.storage, PreviewData.backgroundRuns,
        onChange = { settings = it }, onChooseArchive = {}, onStopArchive = {},
        onClearCache = {}, onClearBackgroundLog = {}, readCount = 42, onClearReadHistory = {},
        topicCounts = settings.topics.associate { t -> t.text to LAB_TOPIC_SCORES[t.text].orEmpty().count { it.value >= settings.topicThreshold } },
        onBack = {},
    )
}

/** The made-up sites of the samples ([PreviewData.sites]). */
internal fun labSources(): List<Site> = PreviewData.sites
