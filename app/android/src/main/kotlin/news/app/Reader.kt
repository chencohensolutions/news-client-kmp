package news.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import news.core.model.ArticleSummary

/**
 * Reading: swipe through [ReaderState.pages], the headlines in the order the list showed them (in Stories, the
 * unread ones: [unreadQueue]). The title bar names
 * the site, then the article's category and how many of the others are still unread ("פוליטיקה · עוד 7 סיפורים שלא
 * נקראו"); a thin line under it shows how far into the list this page is; share and open-in-browser are icons. A page
 * that is a story has a chip per report (site · time, a check once read) to switch between its sites' articles, in a
 * bar at the bottom that slides away while scrolling down and back on scrolling up. The
 * article on screen counts as read once the swipe settles on it ([onRead]); the pages either side load in advance
 * ([onLoad]); each article ends with a card naming the next unread one ([nextUnread]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    reader: ReaderState,
    articles: Map<String, ArticleState>,
    read: Set<String>,
    sources: List<Site>,
    onLoad: (ArticleSummary) -> Unit,
    onRetry: (ArticleSummary) -> Unit,
    onRead: (ArticleSummary) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val names = sources.associate { it.id to it.name }
    val languages = sources.associate { it.id to it.language }
    val pages = reader.pages
    val pager = rememberPagerState(initialPage = reader.startPage) { pages.size }
    // The report chosen on each page that has more than one; the others show their first (newest).
    val chosen = remember(reader) { mutableStateMapOf(reader.startPage to reader.startReport) }
    fun shown(page: Int) = pages[page][chosen[page] ?: 0]

    val current = shown(pager.currentPage)
    val settled = shown(pager.settledPage)
    LaunchedEffect(settled.url) { onRead(settled) }
    val scope = rememberCoroutineScope()
    // Out of the way while reading down, as in the feed: the header goes up out of sight by as much as the text moves,
    // giving up its room (it takes the scroll until it is gone), and a story's report bar at the bottom moves down
    // with the text, as far as its own height; scrolling up brings both back, and so does a new page.
    var headerHeight by remember { mutableIntStateOf(0) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    var barHeight by remember { mutableIntStateOf(0) }
    var barOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pager.currentPage) {
        headerOffset = 0f
        barOffset = 0f
    }
    // The article, blurred under the status bar once the header is gone (StatusBarGlass). Made again once the reader
    // has faded in: on the owner's phone the first article opened showed no blur until a swipe away and back, the one
    // made while the reader faded in not taking.
    val glass = remember { HazeState() }
    // A video playing on the page on screen ([VideoCard]): the status bar is solid then, not frosted.
    val videoPlaying = remember { mutableStateOf(false) }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val before = headerOffset
                headerOffset = (before - available.y).coerceIn(0f, headerHeight.toFloat())
                barOffset = (barOffset - available.y).coerceIn(0f, barHeight.toFloat())
                return Offset(0f, before - headerOffset)
            }
        }
    }

    AppLayout {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(hideOnScroll),
                topBar = {
                    CollapsingHeader(offset = { headerOffset }, onHeight = { headerHeight = it }) {
                        Column {
                            TopAppBar(
                                windowInsets = WindowInsets(0),
                                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(names[current.sourceId].orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { shareArticle(context, current) }) { Icon(Icons.Filled.Share, "שיתוף") }
                                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.url))) }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "פתיחה באתר")
                                    }
                                },
                            )
                            LinearProgressIndicator(
                                progress = { (pager.currentPage + 1f) / pages.size },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                drawStopIndicator = {},
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pager,
                        beyondViewportPageCount = 1,
                        key = { pages[it].first().url },
                        modifier = Modifier.padding(padding).fillMaxSize().hazeSource(glass),
                    ) { page ->
                        val summary = shown(page)
                        LaunchedEffect(summary.url) { onLoad(summary) }
                        val nextPage = nextUnread(pages, read, page)
                        val next = nextPage?.let { pages[it][chosen[it] ?: 0] }
                        CompositionLocalProvider(LocalVideoPlaying provides videoPlaying) {
                            ArticleContent(
                                state = articles[summary.url] ?: ArticleState.Loading(summary),
                                sourceName = names[summary.sourceId].orEmpty(),
                                language = languages[summary.sourceId],
                                onRetry = { onRetry(summary) },
                                active = page == pager.currentPage,
                                next = next,
                                nextSite = next?.let { names[it.sourceId] },
                                nextIsStory = nextPage != null && pages[nextPage].size > 1,
                                onNext = { nextPage?.let { scope.launch { pager.animateScrollToPage(it) } } },
                            )
                        }
                    }
                    // This box starts at the screen's top: the glass needs no move up.
                    // Only once the header has gone up off the status bar.
                    // Asked as a yes or no, so the header sliding away composes this again only when the answer changes.
                    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
                    // Not before the header is measured: at 0 the glass was made at once, while the reader faded in, and on
                    // the owner's phone the first article opened then showed no blur until a swipe away and back.
                    val glassShows by remember(statusBar) { derivedStateOf { headerHeight > 0 && headerHeight - headerOffset < statusBar } }
                    if (glassShows) StatusBarGlass(glass, pageTop = { 0.dp }, Modifier.align(Alignment.TopCenter).zIndex(3f), solid = videoPlaying.value)
                    val reports = pages[pager.currentPage]
                    if (reports.size > 1) {
                        // Over the text, across the full width, under the system's navigation bar.
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .onSizeChanged { barHeight = it.height }
                                .offset { IntOffset(0, barOffset.roundToInt()) },
                        ) {
                            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
                                ReportChips(reports, chosen[pager.currentPage] ?: 0, names, read) { chosen[pager.currentPage] = it }
                            }
                        }
                    }
                }
            }
            // With a video playing, a solid strip under the status bar from when the header starts to go, over the
            // header too (drawn above the page): the header's title would pass under the status bar, and the blur
            // doesn't take a playing video on the owner's phone.
            val coverStatusBar by remember { derivedStateOf { videoPlaying.value && headerHeight > 0 && headerOffset > 0f } }
            if (coverStatusBar) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

/** Whether a page has been read: a story counts once any of its reports was opened. */
internal fun isRead(page: List<ArticleSummary>, read: Set<String>): Boolean = page.any { it.url in read }

/**
 * The unread pages other than [current], in the order to read them: those after it, then those before it from the
 * top. At most [limit].
 */
internal fun upcomingUnread(pages: List<List<ArticleSummary>>, read: Set<String>, current: Int, limit: Int = Int.MAX_VALUE): List<Int> =
    ((current + 1 until pages.size) + (0 until current)).asSequence().filter { !isRead(pages[it], read) }.take(limit).toList()

/**
 * The pages to swipe through when the reader opens at [opened]: that page and every unread one, in order, so the
 * next swipe is the next unread story. Pages read while reading stay, so swiping back still finds them.
 */
internal fun unreadQueue(pages: List<List<ArticleSummary>>, read: Set<String>, opened: Int): List<List<ArticleSummary>> =
    pages.filterIndexed { i, page -> i == opened || !isRead(page, read) }

/** The page to read after [current]: the first unread one after it, else the first unread one from the top. */
internal fun nextUnread(pages: List<List<ArticleSummary>>, read: Set<String>, current: Int): Int? =
    upcomingUnread(pages, read, current, limit = 1).firstOrNull()

/** A story's reports as chips: site and time, a check once read; the chosen one is filled. */
@Composable
private fun ReportChips(
    reports: List<ArticleSummary>,
    selected: Int,
    names: Map<String, String>,
    read: Set<String>,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(reports) { i, report ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(listOfNotNull(names[report.sourceId], ago(report.publishedAt)).joinToString(" · ")) },
                // The check slides in once the report is read, rather than the chip jumping wider.
                leadingIcon = {
                    AnimatedVisibility(visible = report.url in read, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                    }
                },
            )
        }
    }
}
