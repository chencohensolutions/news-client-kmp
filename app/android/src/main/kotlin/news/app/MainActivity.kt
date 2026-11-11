package news.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import news.core.model.ArticleSummary
import news.app.server.EnrollmentLink
import news.app.server.parseEnrollmentLink
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {

    /** The article a notification asked to open ([NewArticleNotifications]), until the reader shows it. */
    private var toOpen by mutableStateOf<ArticleSummary?>(null)

    /** A `news://enroll` link (the server's QR code scanned with the camera app), until the server page takes it. */
    private var toEnroll by mutableStateOf<EnrollmentLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            toOpen = articleIn(intent)
            toEnroll = enrollmentIn(intent)
        }
        setContent {
            NewsTheme {
                NewsApp(toOpen = toOpen, onOpened = { toOpen = null }, toEnroll = toEnroll, onEnrolling = { toEnroll = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        articleIn(intent)?.let { toOpen = it }
        enrollmentIn(intent)?.let { toEnroll = it }
    }

    private fun enrollmentIn(intent: Intent?): EnrollmentLink? = intent?.data?.toString()?.let(::parseEnrollmentLink)

    /**
     * The article named by a notification's open intent, if this is one. Its notification goes: a tap on it goes by
     * itself, the "open" button's doesn't.
     */
    private fun articleIn(intent: Intent?): ArticleSummary? {
        // The combined notification opens the feed; its headlines are seen, so the next one starts afresh.
        if (intent?.getBooleanExtra(NewArticleNotifications.EXTRA_COMBINED, false) == true) NewArticleNotifications.clearCombined(this)
        val url = intent?.getStringExtra(NewArticleNotifications.EXTRA_URL) ?: return null
        val source = intent.getStringExtra(NewArticleNotifications.EXTRA_SOURCE) ?: return null
        NewArticleNotifications.cancel(this, url)
        return ArticleSummary(source, url, intent.getStringExtra(NewArticleNotifications.EXTRA_TITLE).orEmpty())
    }
}

/**
 * Google's code scanner (Play services: no camera permission) for the server's enrollment QR code; [onLink] gets the
 * link it holds. Another code, or a scanner that can't start, says so and leaves the typed fields.
 */
private fun scanEnrollmentCode(context: Context, onLink: (EnrollmentLink) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { code ->
            code.rawValue?.let(::parseEnrollmentLink)?.let(onLink)
                ?: Toast.makeText(context, "זה לא קוד חיבור של שרת החדשות", Toast.LENGTH_LONG).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "סורק הקודים לא נפתח. הקלידו את הכתובת והקוד.", Toast.LENGTH_LONG).show()
        }
}

/**
 * The app's colors: the phone's own (Material You, Android 12+), else the stock ones, following the system's light or
 * dark setting. The UI lab uses it too.
 */
@Composable
fun NewsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NewsApp(
    vm: NewsViewModel = viewModel(),
    toOpen: ArticleSummary? = null,
    onOpened: () -> Unit = {},
    toEnroll: EnrollmentLink? = null,
    onEnrolling: () -> Unit = {},
) {
    val headlines by vm.headlines.collectAsStateWithLifecycle()
    val sites by vm.repository.sites.collectAsStateWithLifecycle()
    val reader by vm.reader.collectAsStateWithLifecycle()
    val articles by vm.articles.collectAsStateWithLifecycle()
    val showSettings by vm.showSettings.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val archiveImport by vm.archiveImport.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val justRead by vm.justRead.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val backgroundRuns by vm.backgroundRuns.collectAsStateWithLifecycle()
    val returnTo by vm.returnTo.collectAsStateWithLifecycle()
    val topicCounts by vm.topicCounts.collectAsStateWithLifecycle()
    val topicsWorking by vm.topicsWorking.collectAsStateWithLifecycle()
    val server by vm.server.collectAsStateWithLifecycle()
    val serverPage by vm.serverPage.collectAsStateWithLifecycle()
    val serverSources by vm.serverSources.collectAsStateWithLifecycle()
    val adminState by vm.admin.collectAsStateWithLifecycle()
    val serverGrouping by vm.serverGrouping.collectAsStateWithLifecycle()
    val fullArticles by vm.fullArticles.collectAsStateWithLifecycle()
    val storyLength by vm.storyLength.collectAsStateWithLifecycle()
    val rewrite by vm.rewrite.collectAsStateWithLifecycle()
    val statsOpen by vm.statsOpen.collectAsStateWithLifecycle()
    val serverStats by vm.serverStats.collectAsStateWithLifecycle()
    val statsLoading by vm.statsLoading.collectAsStateWithLifecycle()
    val summaryStory by vm.summaryStory.collectAsStateWithLifecycle()
    val threadLayers by vm.threadLayers.collectAsStateWithLifecycle()
    val threadPages by vm.threadPages.collectAsStateWithLifecycle()
    val storyArticles by vm.storyArticles.collectAsStateWithLifecycle()
    val allThreads by vm.allThreads.collectAsStateWithLifecycle()
    val threadsRefreshing by vm.threadsRefreshing.collectAsStateWithLifecycle()
    val threadsMap by vm.threadsMap.collectAsStateWithLifecycle()
    val threadMapView by vm.threadMapView.collectAsStateWithLifecycle()
    // The server's enrollment QR code, scanned with the camera app: the server page, enrolling with it.
    LaunchedEffect(toEnroll) {
        val link = toEnroll ?: return@LaunchedEffect
        vm.enrollWith(link)
        onEnrolling()
    }
    // Coming back to the app: a feed more than a minute old is fetched again.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.refreshIfStale()
        vm.reloadRead()
    }
    // An article from a notification: the reader opens at it, in the feed's order when it is in the feed, else alone.
    LaunchedEffect(toOpen) {
        val asked = toOpen ?: return@LaunchedEffect
        vm.open(headlines.allItems.firstOrNull { it.url == asked.url } ?: asked)
        onOpened()
    }
    // Refreshing in the background tells of new articles in notifications: Android 13 and later ask for that once.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
    LaunchedEffect(settings.backgroundMinutes, settings.notifications) {
        if (settings.backgroundMinutes > 0 && settings.notifications && !NewArticleNotifications.allowed(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // The feed leaves the screen while an article or the settings are open; this keeps its scroll position for when
    // it comes back.
    val saved = rememberSaveableStateHolder()
    val current = reader
    val serverActions = ServerActions(
        onAddress = vm::setServerAddress,
        onCode = vm::setServerCode,
        onScan = { scanEnrollmentCode(context, vm::enrollWith) },
        onPaste = {
            val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (!vm.enrollFromText(text)) Toast.makeText(context, "אין קישור חיבור בלוח. העתיקו את הקישור news://enroll…", Toast.LENGTH_LONG).show()
        },
        onConnect = vm::connectServer,
        onConfirmKey = vm::confirmServerKey,
        onCancel = vm::cancelServer,
        onRetry = vm::retryServer,
        onDisconnect = vm::disconnectServer,
        onStats = { vm.showStats(true) },
        onBack = { vm.showServerPage(false) },
    )
    // The news comes only from the server: until the phone is enrolled, connecting is all the app shows.
    if (server !is ServerState.Connected) {
        ServerScreen(server, serverActions, canGoBack = false)
        return
    }
    if (showSettings) {
        BackHandler(onBack = vm::closeSettings)
        SettingsScreen(
            settings = settings,
            sources = sites,
            stories = headlines.stories,
            downloads = downloads,
            storage = storage,
            backgroundRuns = backgroundRuns,
            onChange = vm::updateSettings,
            onChooseArchive = vm::chooseArchive,
            onStopArchive = vm::stopArchive,
            archiveImport = archiveImport,
            onImportArchive = vm::importArchive,
            onArchiveNow = vm::archiveNow,
            onClearCache = vm::clearCache,
            onClearBackgroundLog = vm::clearBackgroundLog,
            readCount = headlines.read.size,
            onClearReadHistory = vm::clearReadHistory,
            unreadCount = headlines.items.count { it.url !in headlines.read },
            onMarkAllRead = vm::markAllRead,
            onMarkAllViewed = vm::markAllViewed,
            topicCounts = topicCounts,
            server = server,
            serverActions = serverActions,
            serverPage = serverPage,
            serverSources = serverSources,
            stats = if (statsOpen) StatsPage(serverStats, statsLoading, onRefresh = vm::refreshStats, onBack = { vm.showStats(false) }) else null,
            onServerSource = vm::setServerSource,
            serverGrouping = serverGrouping,
            onServerGrouping = { strictness, text, days -> vm.setServerGrouping(strictness, text, days) },
            onServerGroupingMethod = { vm.setServerGrouping(byGemini = it) },
            fullArticles = fullArticles,
            storyLength = storyLength,
            rewrite = rewrite,
            onRewrite = { m, n, r -> vm.setRewrite(m, n, r) },
            onStoryLength = vm::setStoryLength,
            onFullArticles = vm::setFullArticles,
            onServerPage = vm::showServerPage,
            onSourceHidden = vm::setSourceHidden,
            adminState = adminState,
            adminActions = AdminActions(
                load = vm::loadUsers,
                invite = vm::createInvite,
                dismissInvite = vm::dismissInvite,
                setRole = vm::setUserRole,
                revoke = vm::revokeDevice,
                remove = vm::removeUser,
            ),
            onBack = vm::closeSettings,
        )
    } else {
        // Feed and reader: the reader fades in over the fading feed, and a story's picture moves to its place in the
        // article (sharedPicture). Keyed by whether an article is open, so paging in the reader doesn't animate.
        SharedTransitionLayout {
            AnimatedContent(
                targetState = current,
                contentKey = { it == null },
                // One screen fades over the other, which stays as it is underneath: two fading together would let the
                // window show through halfway, a grey flash. Opening, the article fades in over the feed; closing, it
                // fades out over the feed.
                transitionSpec = {
                    if (targetState != null) {
                        fadeIn(tween(300, delayMillis = 90)) togetherWith ExitTransition.KeepUntilTransitionsFinished
                    } else {
                        (EnterTransition.None togetherWith fadeOut(tween(250, delayMillis = 60))).apply { targetContentZIndex = -1f }
                    }
                },
                label = "reader",
            ) { open ->
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalAnimatedVisibilityScope provides this,
                ) {
                    val names = remember(sites) { sites.associate { it.id to it.name } }
                    val story = summaryStory
                    val summary = story?.reports?.firstNotNullOfOrNull { headlines.summaries[it.url] }
                    // A generated article's page, its threads and their stories at the end.
                    val articlePage = @Composable { s: news.app.stories.Story, sum: news.app.server.StorySummary, onBack: () -> Unit ->
                        SummaryArticleScreen(s, sum, names, onFootnote = vm::openFootnote, onBack = onBack, articles = storyArticles) {
                            ThreadRelated(
                                sum.id, sum.threads, threadPages,
                                onThread = { vm.openThread(it, sum.id) },
                                onStory = { t ->
                                    vm.openThreadStory(t, sum.threads.firstOrNull()) || true.also { sum.threads.firstOrNull()?.let { ref -> vm.openThread(ref, t.id) } }
                                },
                                line = settings.relatedLine,
                            )
                        }
                    }
                    val layer = threadLayers.lastOrNull()
                    if (open == null && layer != null) {
                        BackHandler { vm.closeThreadLayer() }
                        when (layer) {
                            is ThreadLayer.Page -> ThreadScreen(
                                layer.ref, threadPages[layer.ref.id], layer.currentId,
                                onStory = { vm.openThreadStory(it, layer.ref) },
                                onThread = { vm.openThread(it, null) },
                                onBack = vm::closeThreadLayer,
                            )
                            ThreadLayer.Map -> ThreadMapScreen(
                                threadsMap, threadMapView, vm::setThreadMapView,
                                onStory = vm::openMapStory,
                                onThread = { vm.openThread(it, null) },
                                onBack = vm::closeThreadLayer,
                                line = settings.relatedLine,
                            )
                            ThreadLayer.All -> ThreadsScreen(allThreads, threadsRefreshing && allThreads != null, vm::refreshThreads, onThread = { vm.openThread(it, null) }, onBack = vm::closeThreadLayer)
                            is ThreadLayer.Article -> (layer.story.reports.firstNotNullOfOrNull { headlines.summaries[it.url] } ?: layer.summary)
                                ?.let { articlePage(layer.story, it, vm::closeThreadLayer) }
                        }
                    } else if (open == null && story != null && summary != null) {
                        // A generated article as a page, over the feed or the paper.
                        BackHandler { vm.showSummary(null) }
                        articlePage(story, summary) { vm.showSummary(null) }
                    } else if (open == null) {
                        saved.SaveableStateProvider("headlines") {
                            HeadlinesScreen(
                                state = headlines,
                                feed = feed,
                                justRead = justRead,
                                downloads = downloads,
                                sources = sites,
                                onRefresh = vm::refresh,
                                onShowSites = vm::showSites,
                                onShowCategories = vm::showCategories,
                                onShowOnlyNew = vm::showOnlyNew,
                                onOpenSettings = vm::openSettings,
                                onOpen = vm::open,
                                returnTo = returnTo,
                                onReturned = vm::returned,
                                onScrolledPast = vm::scrolledPast,
                                listAhead = settings.listAhead,
                                showFilterLabels = settings.showFilterLabels,
                                snapToRows = settings.snapToRows,
                                pictureBelow = settings.pictureBelowText,
                                sourceLabels = settings.sourceLabels,
                                sentenceLines = settings.sentenceLines,
                                feedSentences = settings.feedSentences,
                                onPrefetch = vm::prefetchListImages,
                                onNearEnd = vm::showMoreOlder,
                                topics = settings.topics,
                                topicCounts = topicCounts,
                                topicThreshold = settings.topicThreshold,
                                topicsWorking = topicsWorking,
                                onAddTopic = vm::addTopic,
                                onToggleTopic = vm::toggleTopic,
                                onRemoveTopic = vm::removeTopic,
                                onFootnote = vm::openFootnote,
                                onSummary = vm::showSummary,
                                onThread = vm::openThread,
                                onRefreshOnlyNew = vm::refreshOnlyNew,
                                onMap = vm::openMap,
                                onThreads = vm::openThreads,
                                onShowOriginal = vm::showOriginal,
                                onFlip = vm::flipStory,
                                onOnlyGenerated = vm::showOnlyGenerated,
                                newWithinHours = settings.newWithinHours,
                            )
                        }
                    } else {
                        BackHandler(onBack = vm::close)
                        // A reader of its own per opened article: one opened while the last one is still fading out
                        // would otherwise take over that one's pager, on the page it was left at.
                        key(open) {
                            ReaderScreen(
                                reader = open,
                                articles = articles,
                                read = headlines.read,
                                sources = sites,
                                onLoad = vm::load,
                                onRetry = { vm.load(it, retry = true) },
                                onRead = vm::markRead,
                                onBack = vm::close,
                            )
                        }
                    }
                }
            }
        }
    }
}
