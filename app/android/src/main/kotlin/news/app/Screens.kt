package news.app

import androidx.compose.material.icons.filled.AutoAwesome
import news.app.server.Footnote
import news.app.server.StorySummary
import news.app.stories.Story
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Timeline
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleSummary
import news.core.model.Categories
import news.app.stories.TopicMatch

/**
 * The headline list, the app's one screen ("הכל"): the stories, and every headline in none as a card of its own,
 * newest first. A story is its sites, its lead's picture across the width at its own proportions, the lead's title in
 * large type, then every other report with site and time. Under the title a line says when the feed was fetched and
 * how many headlines are new ([HeadlinesState.newUrls]); a story with a new report is badged. While the feed's articles
 * come from the server ([ArticleDownloads]), a thin bar along the bottom of the title bar fills with them, over a caption with
 * the count. At the bottom floats an island of filters ([FeedBottomBar]): a toggle to show only what is new, and
 * bottom sheets of pills ([ChoiceSheet]) to pick the sites shown (a warning on one that failed to load) or the
 * categories; what is picked shows under the title as labels with an ✕ ([FilterLabels]) when settings say so. The
 * filters choose what shows, not what stories are grouped from: a story keeps all its sites, led by a report the
 * filters let through.
 *
 * Beside the island, the topic search ([TopicSheet]): with topics on, only the stories with a report close in meaning
 * to one ([HeadlinesState.topicMatches]), each whole, with the topic under its title; the topics on float above the
 * island.
 *
 * Arrivals: items are keyed and animate into place, so a refresh that brings new headlines doesn't jump the list; at
 * the very top the list follows the new items up, anywhere else it stays put.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadlinesScreen(
    state: HeadlinesState,
    feed: FeedView,
    justRead: Set<String> = emptySet(),
    downloads: ArticleDownloads,
    sources: List<Site>,
    onRefresh: () -> Unit,
    onShowSites: (Set<String>) -> Unit,
    onShowCategories: (Set<String>) -> Unit,
    onShowOnlyNew: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (ArticleSummary) -> Unit,
    returnTo: String? = null,
    onReturned: () -> Unit = {},
    onScrolledPast: (Collection<String>) -> Unit = {},
    listAhead: Int = 0,
    onPrefetch: (List<String>) -> Unit = {},
    onNearEnd: () -> Unit = {},
    showFilterLabels: Boolean = false,
    snapToRows: Boolean = false,
    pictureBelow: Boolean = false,
    /** Where generated cards show their sources' labels. */
    sourceLabels: SourceLabels = SourceLabels.BELOW,
    sentenceLines: Boolean = false,
    /** How many sentences a generated card shows ([AppSettings.feedSentences]); 0: all. */
    feedSentences: Int = 0,
    onThread: (news.app.server.ThreadRef, String) -> Unit = { _, _ -> },
    /** The bottom bar's ⏱: every thread. */
    onThreads: () -> Unit = {},
    topics: List<SavedTopic> = emptyList(),
    topicCounts: Map<String, Int> = emptyMap(),
    topicThreshold: Int = 55,
    topicsWorking: Boolean = false,
    onAddTopic: (String) -> Unit = {},
    onToggleTopic: (String) -> Unit = {},
    onRemoveTopic: (String) -> Unit = {},
    /** A footnote of a generated article: opens its article. */
    onFootnote: (Footnote) -> Unit = {},
    /** A story's "לכתבה המלאה": its generated article as a page. */
    onSummary: (Story) -> Unit = {},
    /** The title bar's map of the threads (מפת פרשות). */
    onMap: () -> Unit = {},
    onSaved: () -> Unit = {},
    /** Updates on the stories followed not seen yet: a badge on the Saved button. */
    followUnseen: Int = 0,
    /** Only new, back at the top: show what is still new now. */
    onRefreshOnlyNew: () -> Unit = {},
    /** The feed's switch: generated articles or the stories' original headlines. */
    onShowOriginal: (Boolean) -> Unit = {},
    /** One story's ⇄: its card to the other view, by its lead's URL. */
    onFlip: (String) -> Unit = {},
    /** "רק כתבות מסוכמות": only the stories with a generated article. */
    onOnlyGenerated: (Boolean) -> Unit = {},
    /** "חדש עד" ([AppSettings.newWithinHours]): which generated stories the חדשים badge counts. */
    newWithinHours: Int = 0,
    /** [AppSettings.newestVersionOnly]: each generated story once, at its newest version ([newestVersionsOnly]). */
    newestVersionOnly: Boolean = true,
    /** A generated story's "גרסה N": its versions. */
    onVersions: (StorySummary) -> Unit = {},
) {
    // With the server's generated articles, the חדשים badge counts the generated stories still to read, not headlines.
    val generated = state.summaries.isNotEmpty()
    // The stories: one card a generated story, at its newest version, unless the setting is off.
    val stories = remember(feed.all, state.summaries, newestVersionOnly) {
        if (generated && newestVersionOnly) newestVersionsOnly(feed.all, state.summaries) else feed.all
    }
    // Like the headlines' count: a story scrolled past (no report still new to the owner) no longer counts either.
    val unreadStories = remember(stories, state.summaries, state.storiesOpened, newWithinHours, feed.markedNew) {
        if (!generated) emptySet()
        else unreadGenerated(stories, state.summaries, state.storiesOpened, newWithinHours)
            .filter { s -> s.reports.any { it.url in feed.markedNew } }.mapTo(HashSet()) { it.lead.url }
    }
    // What the list shows: only the stories with a generated article (the default) or all; the new ones first, then a
    // quiet "עד כאן החדשים" after the last new one, so scrolling down shows where the new ones end.
    val shown = if (generated && state.onlyGenerated) stories.filter { s -> s.reports.any { it.url in state.summaries } } else stories
    val isNew = { s: Story -> if (generated && state.onlyGenerated) s.lead.url in unreadStories else s.reports.any { it.url in feed.markedNew } }
    val lastNew = shown.indexOfLast(isNew)
    val newEnd = !state.onlyNew && lastNew >= 0 && lastNew < shown.lastIndex
    val names = remember(sources) { sources.associate { it.id to it.name } }
    val languages = remember(sources) { sources.associate { it.id to it.language } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The one site shown, when only one is: its name titles the list, and its failure shows above it. The one
    // category likewise titles it.
    val site = state.sites.singleOrNull()
    val category = state.categories.singleOrNull()
    val failure = site?.let { state.visibleFailures[it] }
    val topicsOn = remember(topics) { topics.filter { it.on }.map { it.text } }
    val searching = state.topicMatches != null
    // The key of the first item. When it changes and the list was at the very top, the list follows the new items up,
    // so they come in before the reader's eyes; anywhere else it stays put.
    val firstKey = feed.all.firstOrNull()?.lead?.url
    LaunchedEffect(firstKey) {
        // Read before the first suspension: this is the layout from before the change. Not animated: while stories are
        // prepared the first item changes every second or so, and a list mid-scroll takes a tap only to stop the
        // scroll, so the first tap on a story did nothing. The items' own placement animations still show the arrival.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) listState.requestScrollToItem(0)
    }
    // Other filters start from the top: keeping the old position would leave the list mid-way through new neighbours.
    // Only a change: coming back from an article composes the list again, and it keeps its place then.
    val filters = listOf(state.sites, state.categories, state.onlyNew, topicsOn)
    var filtersShown by rememberSaveable { mutableStateOf(filters.toString()) }
    LaunchedEffect(filters) {
        if (filters.toString() != filtersShown) {
            filtersShown = filters.toString()
            listState.scrollToItem(0)
        }
    }
    // The bottom sheet of pills to pick from: which one is open, if any.
    var sheetFor by remember { mutableStateOf<SheetFor?>(null) }
    // A row that leaves the screen over its top was scrolled past: its headlines (a story's, all of them) are no longer
    // new. Not one that leaves at the bottom, or goes with the list (another view, the reader): those weren't read past.
    val currentFeed by rememberUpdatedState(feed)
    LaunchedEffect(listState) {
        var before = emptyMap<Any, Int>()
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.associate { it.key to it.offset } }.collect { now ->
            val past = before.filter { (key, offset) -> key !in now && offset < 0 }.keys.flatMap { key -> rowUrls(key, currentFeed) }
            if (past.isNotEmpty()) onScrolledPast(past)
            before = now
        }
    }
    // The last rows can't leave over the top: at the end of the list, scrolled to, a row whose bottom is on screen (above
    // the bottom bar) is viewed too. Only after a scroll, so a short list isn't viewed just by being shown.
    LaunchedEffect(listState) {
        var scrolled = false
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (moving, more) ->
            if (moving) scrolled = true
            if (scrolled && !more) {
                val info = listState.layoutInfo
                val shown = info.visibleItemsInfo.filter { it.offset + it.size <= info.viewportEndOffset - info.afterContentPadding }
                    .flatMap { rowUrls(it.key, currentFeed) }
                if (shown.isNotEmpty()) onScrolledPast(shown)
            }
        }
    }
    // The pictures of the next [listAhead] rows below the screen download as it scrolls, so they are there when the
    // rows come up. Each picture is asked for once.
    // Keyed on the stories alone: scrolling past a row changes the feed's badges, not its rows.
    val rowPictures by rememberUpdatedState(remember(feed.all) { rowPictures(feed) })
    val asked = remember { HashSet<String>() }
    LaunchedEffect(listState, listAhead) {
        if (listAhead <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.key }.collect { last ->
            val rows = rowPictures
            val at = rows.indexOfFirst { it.first == last }
            val next = rows.drop(at + 1).take(listAhead).mapNotNull { it.second }.filter(asked::add)
            if (next.isNotEmpty()) onPrefetch(next)
        }
    }
    // Near the end of the list, older headlines join it ([HeadlinesState.older]); asked again when they come in, in
    // case the filters let none of them show.
    val olderCount = state.older.size
    LaunchedEffect(listState, olderCount) {
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: -1) >= it.totalItemsCount - NEAR_END } }
            .collect { near -> if (near && listState.layoutInfo.totalItemsCount > 0) onNearEnd() }
    }
    // Only new: scrolled down and back up to the top, the list keeps only what is still new (the viewed ones leave).
    LaunchedEffect(listState, state.onlyNew) {
        if (!state.onlyNew) return@LaunchedEffect
        var away = false
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (index > 1) away = true
            if (away && index == 0 && offset == 0) {
                away = false
                onRefreshOnlyNew()
            }
        }
    }
    // Back from the reader: the list stays where it was when the article read last is on screen; when the reader
    // moved on to one that isn't, the list goes to it.
    LaunchedEffect(returnTo) {
        val url = returnTo ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        val index = listIndexOf(url, shown, if (failure == null) 0 else 1, if (newEnd) lastNew else null)
        if (index != null && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        onReturned()
    }
    // Scrolling down, the header and the views' bar move out of the way, and scrolling up brings them back, by as much
    // as the list moves, as a story's report bar does in the reader; another view brings both back. The header gives
    // up its room as it goes (it takes the scroll until it is gone, then the list scrolls), so the list's top, and
    // the topics' sticky headers, stay in sight; the bar lies over the list and only slides.
    var headerHeight by remember { mutableIntStateOf(0) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    var barHeight by remember { mutableIntStateOf(0) }
    var barOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.view) {
        headerOffset = 0f
        barOffset = 0f
    }
    val hideBar = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val before = headerOffset
                headerOffset = (before - available.y).coerceIn(0f, headerHeight.toFloat())
                barOffset = (barOffset - available.y).coerceIn(0f, barHeight.toFloat())
                return Offset(0f, before - headerOffset)
            }
        }
    }
    // The list, blurred under the status bar once the header is gone (StatusBarGlass).
    val glass = remember { HazeState() }
    // The status bar's strip below what shows of the header, once it slides away: see-through, the list running
    // under it (and under the progress lines) to be blurred there (StatusBarGlass), as in the reader.
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    // In pixels. It changes with every frame of a scroll, so it is read as the screen is laid out or drawn (topSpace,
    // the list's padding), not here: read here, it would compose the whole screen again each frame.
    val headerShown = { (headerHeight - headerOffset).coerceAtLeast(0f) }
    // Not before the header is measured (0 at first, which would read as gone).
    val stripShows by remember(statusBar) { derivedStateOf { headerHeight > 0 && headerShown() < statusBar } }
    AppLayout {
        Scaffold(
            modifier = Modifier.nestedScroll(hideBar),
            topBar = {
                Column {
                    CollapsingHeader(offset = { headerOffset }, onHeight = { headerHeight = it }) {
                        Column {
                            TopAppBar(
                                // The status bar's strip is the header's own (CollapsingHeader), going with the rest.
                                windowInsets = WindowInsets(0),
                                title = {
                                    Column {
                                        Text(
                                            if (searching) "לפי נושא"
                                            else listOfNotNull(site?.let { names[it] ?: it }, category?.let(::filterCategoryName)).joinToString(" · ").ifEmpty { "חדשות" },
                                        )
                                        // Only the news server is read, and it doesn't answer: said in place of the check-in.
                                        val serverDown = !searching && state.serverDown && !state.refreshing
                                        Text(
                                            when {
                                                serverDown -> "שרת החדשות לא עונה · " + checkInLine(state.refreshedAt, feed.newCount, false)
                                                !searching -> checkInLine(state.refreshedAt, feed.newCount, state.refreshing || state.starting)
                                                topicsWorking -> "מחפש…"
                                                else -> "${feed.all.size} סיפורים וכותרות · ${topicsOn.joinToString(", ")}"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (serverDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                actions = {
                                    // With the server: its threads, as a list and as a map, next to the settings.
                                    if (generated || state.serverOn) {
                                        IconButton(onClick = onMap) { Icon(Icons.Filled.Hub, "מפת פרשות") }
                                        IconButton(onClick = onThreads) { Icon(Icons.Filled.Timeline, "פרשות") }
                                    }
                                    IconButton(onClick = onSaved) {
                                        BadgedBox(badge = { if (followUnseen > 0) Badge { Text("$followUnseen") } }) { Icon(Icons.Filled.Bookmarks, "שמורים ומעקב") }
                                    }
                                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "הגדרות") }
                                },
                            )
                            if (showFilterLabels) FilterLabels(
                                // One site or one category is the title already: labels only for several. Only-new has none:
                                // its button in the bottom bar shows it is on.
                                if (site != null) emptyList() else state.sites.map { it to shortSiteName(it, names) },
                                if (category != null) emptyList() else state.categories.map { it to filterCategoryName(it) },
                                onClearSite = { onShowSites(state.sites - it) },
                                onClearCategory = { onShowCategories(state.categories - it) },
                            )
                            when (sheetFor) {
                                SheetFor.SITES -> ChoiceSheet(
                                    title = "אתרים",
                                    options = sources.filter { it.id !in state.hiddenSources }.map { source ->
                                        Choice(source.id, source.name, state.allItems.count { it.sourceId == source.id }, failed = source.id in state.visibleFailures)
                                    },
                                    chosen = state.sites,
                                    onChange = onShowSites,
                                    tint = ::siteTint,
                                ) { sheetFor = null }
                                SheetFor.CATEGORIES -> ChoiceSheet(
                                    title = "נושאים",
                                    options = (Categories.IDS + OTHER_CATEGORY).map { id ->
                                        Choice(id, filterCategoryName(id), state.visibleItems.count { (it.category ?: OTHER_CATEGORY) == id })
                                    },
                                    chosen = state.categories,
                                    onChange = onShowCategories,
                                ) { sheetFor = null }
                                SheetFor.TOPICS -> TopicSheet(
                                    topics = topics,
                                    counts = topicCounts,
                                    rows = feed.all.size,
                                    threshold = topicThreshold,
                                    working = topicsWorking,
                                    onAdd = onAddTopic,
                                    onToggle = onToggleTopic,
                                    onRemove = onRemoveTopic,
                                ) { sheetFor = null }
                                null -> {}
                            }
                        }
                    }
                    // The downloads and the story finding stay in sight when the header slides away: under the status bar
                    // then, which the header no longer covers.
                    Column(Modifier.fillMaxWidth().topSpace { (statusBar - headerShown()).roundToInt() }.background(MaterialTheme.colorScheme.surface)) {
                        // Fetching: the saved feed at launch, a refresh, the server's stories. The download line says more when it shows.
                        val fetching = state.starting || state.refreshing || (state.stories as? StoriesState.Ready)?.refreshing == true
                        AnimatedVisibility(visible = fetching && !downloads.showing, enter = fadeIn(), exit = fadeOut()) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = MaterialTheme.colorScheme.primary)
                        }
                        AnimatedVisibility(visible = downloads.showing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column(Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { downloads.fraction },
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    drawStopIndicator = {},
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.CloudDownload,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        " שומר כתבות לקריאה בלי רשת · ${downloads.fetched} מתוך ${downloads.toFetch}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${(downloads.fraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            // The spinner shows only while pulling: the line under the title already says "מרענן…" while it runs.
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefresh,
                // Not the bottom: the bottom bar lies over the list, which runs under it to the screen's edge.
                modifier = Modifier.topSpace { headerShown().roundToInt() }.fillMaxSize(),
            ) {
                // Back to the top, once the list is past the first story (a story is one tall row). A long way down
                // it jumps most of the way first, so the ride up is short.
                val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                AnimatedVisibility(
                    visible = showTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    // Above the bottom bar, moving with it.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).offset { IntOffset(0, -(barHeight - barOffset).roundToInt()) }
                        .zIndex(1f),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            // A scroll in code doesn't pass the scroll connection: the header and the bottom bar come
                            // back here, to the top of the screen with the list.
                            headerOffset = 0f
                            barOffset = 0f
                            scope.launch {
                                if (listState.firstVisibleItemIndex > TOP_JUMP) listState.scrollToItem(TOP_JUMP)
                                listState.animateScrollToItem(0)
                            }
                        },
                    ) { Icon(Icons.Filled.ArrowUpward, "לראש הרשימה") }
                }
                // The bar gone down with the scroll: the new ones still below stay counted, a small pill where it was; a tap goes
                // to the first of them.
                val newLeft = if (generated && state.onlyGenerated) unreadStories.size else feed.newCount
                AnimatedVisibility(
                    visible = newLeft > 0 && barHeight > 0 && barOffset > barHeight * 0.6f,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp).zIndex(2f),
                ) {
                    Surface(
                        // To the first story still new: the header and the bottom bar come back with it, as with ↑.
                        onClick = {
                            val first = shown.indexOfFirst(isNew)
                            headerOffset = 0f
                            barOffset = 0f
                            if (first >= 0) scope.launch {
                                // The rows above the stories: a site's failure, a message.
                                val above = (if (site != null && failure != null) 1 else 0) +
                                    (if ((state.items.isEmpty() && !state.refreshing && !state.starting) || (searching && feed.all.isEmpty() && !topicsWorking)) 1 else 0)
                                val target = above + first
                                // A long way off, a jump most of the way first, so the ride is short.
                                val from = listState.firstVisibleItemIndex
                                if (from - target > TOP_JUMP) listState.scrollToItem(target + TOP_JUMP)
                                else if (target - from > TOP_JUMP) listState.scrollToItem(target - TOP_JUMP)
                                listState.animateScrollToItem(target)
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            "$newLeft חדשים",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                FeedBottomBar(
                    // Only generated stories: the badge counts those not viewed; else every new headline, as before.
                    onlyNew = state.onlyNew, newCount = if (generated && state.onlyGenerated) unreadStories.size else feed.newCount, sitesOn = state.sites.isNotEmpty(), categoriesOn = state.categories.isNotEmpty(),
                    topicsOn = topicsOn,
                    // With the server on, from launch: not only once its generated articles are in.
                    onlyGenerated = if (generated || state.serverOn) state.onlyGenerated else null,
                    onOnlyGenerated = { onOnlyGenerated(!state.onlyGenerated) },
                    onOnlyNew = { onShowOnlyNew(!state.onlyNew) },
                    onSites = { sheetFor = SheetFor.SITES }, onCategories = { sheetFor = SheetFor.CATEGORIES },
                    onSearch = { sheetFor = SheetFor.TOPICS }, onTopicOff = onToggleTopic,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f)
                        .onSizeChanged { barHeight = it.height }
                        .offset { IntOffset(0, barOffset.roundToInt()) },
                )
                // Only once the header has gone up off the status bar; over it, the header's own color.
                if (stripShows) StatusBarGlass(glass, pageTop = { headerShown().toDp() }, Modifier.align(Alignment.TopCenter).zIndex(3f))
                // Its start below the status bar's strip and the progress lines, which it scrolls under (the header's
                // height can be a frame ahead of the bar's: never below 0), and room at the end for the bar, as much of
                // it as shows. Read as the list is laid out: LazyColumn reads its padding only when it measures.
                val listPadding = remember(padding, density) {
                    object : PaddingValues {
                        override fun calculateTopPadding() =
                            with(density) { (padding.calculateTopPadding() - headerShown().toDp()).coerceAtLeast(0.dp) }
                        override fun calculateBottomPadding() = with(density) { 16.dp + (barHeight - barOffset).coerceAtLeast(0f).toDp() }
                        override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
                        override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = listPadding,
                    flingBehavior = if (snapToRows) rememberRowSnapFling(listState) else ScrollableDefaults.flingBehavior(),
                    modifier = Modifier.hazeSource(glass),
                ) {
                    if (site != null && failure != null) item(key = "failure") { FailureBanner(names[site] ?: site, failure) }
                    if (state.items.isEmpty() && !state.refreshing && !state.starting) {
                        item { Message("אין כותרות עדיין. משכו למטה כדי לרענן.") }
                    } else if (searching && feed.all.isEmpty() && !topicsWorking) {
                        item(key = "no-match") {
                            Message("אין כותרות שמזכירות את ${topicsOn.joinToString(" או את ")}. אפשר להוריד את סף ההתאמה בהגדרות.")
                        }
                    }
                    val storyRow: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Story) -> Unit = { story ->
                        StoryItem(
                            story.reports, names, state.read, feed.markedNew, justRead, onOpen, Modifier.animateItem(),
                            // One category picked: every story is in it, so its label says nothing.
                            showCategory = state.categories.size != 1,
                            match = feed.topicMatches?.let { m -> story.reports.mapNotNull { m[it.url] }.maxByOrNull { it.score } },
                            pictureBelow = pictureBelow,
                            sourceLabels = sourceLabels,
                            sentenceLines = sentenceLines,
                            feedSentences = feedSentences,
                            onThread = onThread,
                            summary = story.reports.firstNotNullOfOrNull { state.summaries[it.url] },
                            onFootnote = onFootnote,
                            onSummary = { onSummary(story) },
                            original = state.showOriginal != (story.lead.url in state.flipped),
                            onFlip = { onFlip(story.lead.url) },
                            generatedUnread = story.lead.url in unreadStories,
                            onVersions = { story.reports.firstNotNullOfOrNull { state.summaries[it.url] }?.let(onVersions) },
                            update = story.reports.firstNotNullOfOrNull { state.summaries[it.url] }?.let { s ->
                                cardUpdate(s, s.id.takeIf { it.isNotEmpty() }?.let { state.storySeen[it] })
                            },
                        )
                    }
                    items(if (newEnd) shown.take(lastNew + 1) else shown, key = { "story-" + it.lead.url }) { storyRow(it) }
                    if (newEnd) {
                        item(key = "new-end") { NewEnd(Modifier.animateItem()) }
                        items(shown.drop(lastNew + 1), key = { "story-" + it.lead.url }) { storyRow(it) }
                    }
                }
            }
        }
    }
}

/**
 * Pictures' width-to-height ratios, by URL, once loaded: their boxes keep that size when they come back, and a story's
 * picture lends its own to the article's while that one loads.
 */
internal val pictureRatios = mutableStateMapOf<String, Float>()

/** Notes [url]'s proportions from its loaded [size], kept between 1:2 and 3:1. */
internal fun MutableMap<String, Float>.record(url: String, size: Size) {
    if (!size.isSpecified || size.width <= 0f || size.height <= 0f) return
    // Only a change is written: every picture on screen reads this map, and a write draws them all again.
    val ratio = (size.width / size.height).coerceIn(0.5f, 3f)
    if (this[url] != ratio) this[url] = ratio
}

/** Space of [top] pixels above the content, read as it is laid out rather than composed. */
private fun Modifier.topSpace(top: Density.() -> Int): Modifier = layout { measurable, constraints ->
    val space = top().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -space))
    layout(placeable.width, placeable.height + space) { placeable.place(0, space) }
}

/** How many rows before the end of the list older headlines are asked for. */
private const val NEAR_END = 15

/** The scroll-to-top button jumps to this row before riding the rest of the way up. */
private const val TOP_JUMP = 12

/** Whether the header shows download progress: while downloading, or waiting for Wi-Fi with some still to go. */
private val ArticleDownloads.showing: Boolean get() = total > 0 && done < total && running

// Of this run's articles only: counted against the whole feed, a few new ones would sit at 90% and up.
private val ArticleDownloads.fraction: Float get() = if (toFetch <= 0) 0f else fetched.toFloat() / toFetch

/**
 * A header (the feed's, the reader's) from the screen's top edge (its status bar strip included), with [offset] pixels of it pushed up
 * out of sight and its room given up with them: once it is all gone the page runs up under the status bar, which is
 * then see-through. [onHeight] gets its full height.
 */
@Composable
internal fun CollapsingHeader(offset: () -> Float, onHeight: (Int) -> Unit, content: @Composable () -> Unit) {
    Layout(
        content = { Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) { content() } },
        modifier = Modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val full = placeables.maxOfOrNull { it.height } ?: 0
        onHeight(full)
        val shown = (full - offset().roundToInt()).coerceIn(0, full)
        layout(constraints.maxWidth, shown) { placeables.forEach { it.place(0, shown - full) } }
    }
}

/** Which bottom sheet is open: the sites' or the categories' pills, or the topic search. */
private enum class SheetFor { SITES, CATEGORIES, TOPICS }

/** One pill of a [ChoiceSheet]: its [id], [name], how many headlines it has, and whether it [failed] to load. */
private data class Choice(val id: String, val name: String, val count: Int, val failed: Boolean = false)

/**
 * A bottom sheet of [options] to show, as pills wrapped in rows: none picked shows them all ("הכל" first, picked),
 * and picking one shows only those picked. Each pill has its count, or a warning for a site that failed to load.
 * With a [tint] (the sites), each pill is in its own color, as on the stories: filled when picked, faint when not.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Choice>,
    chosen: Set<String>,
    onChange: (Set<String>) -> Unit,
    tint: ((String) -> Color)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            // Dense: the pills without Material's 48 dp touch height around each, 6 dp apart either way.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tint != null) {
                        ColoredChoice("הכל", MaterialTheme.colorScheme.primary, picked = chosen.isEmpty(), failed = false) { onChange(emptySet()) }
                    } else {
                        FilterChip(selected = chosen.isEmpty(), onClick = { onChange(emptySet()) }, label = { Text("הכל") })
                    }
                    options.forEach { option ->
                        val onClick = { onChange(if (option.id in chosen) chosen - option.id else chosen + option.id) }
                        val label = if (option.failed) option.name else "${option.name} · ${option.count}"
                        if (tint != null) {
                            ColoredChoice(label, tint(option.id), picked = option.id in chosen, option.failed, onClick)
                        } else {
                            FilterChip(
                                selected = option.id in chosen,
                                onClick = onClick,
                                label = { Text(label) },
                                leadingIcon = if (option.failed) {
                                    { Icon(Icons.Filled.Warning, "לא נטען", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A site in the [ChoiceSheet], in its [color]: filled with it, white letters, when [picked]; on a faint tint of it
 * when not (lightened in dark theme), as [SitePill] is, and about its size.
 */
@Composable
private fun ColoredChoice(label: String, color: Color, picked: Boolean, failed: Boolean, onClick: () -> Unit) {
    val faint = if (isSystemInDarkTheme()) lerp(color, Color.White, 0.45f) else color
    val content = if (picked) Color.White else faint
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (picked) color else faint.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (failed) Icon(Icons.Filled.Warning, "לא נטען", tint = content, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

/** The sites and categories picked, as labels with an ✕ under the title. */
@Composable
private fun FilterLabels(
    sites: List<Pair<String, String>>,
    categories: List<Pair<String, String>>,
    onClearSite: (String) -> Unit,
    onClearCategory: (String) -> Unit,
) {
    val labels = sites.map { (id, name) -> name to { onClearSite(id) } } +
        categories.map { (id, name) -> name to { onClearCategory(id) } }
    if (labels.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { (label, onClear) ->
            InputChip(selected = true, onClick = onClear, label = { Text(label) }, trailingIcon = { Icon(Icons.Filled.Close, "הסרה", modifier = Modifier.size(16.dp)) })
        }
    }
}

/** Every category among a story's [reports], the lead's first, without the ones in [unrelated]. */
internal fun storyCategories(reports: List<ArticleSummary>, unrelated: Collection<String>): List<String> =
    reports.filter { it.url !in unrelated }.mapNotNull { it.category }.distinct()

/** A category's name in the header's filters: [OTHER_CATEGORY] is "אחר". */
private fun filterCategoryName(id: String): String = if (id == OTHER_CATEGORY) categoryName(null) else categoryName(id)

/** One line for the site whose feed is showing when it didn't load; a tap shows the error itself. */
@Composable
private fun FailureBanner(site: String, message: String) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = !open },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Column {
                Text("$site לא נטען הפעם; אלה הכותרות מהרענון הקודם", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                if (open) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/**
 * One event in the stories list: its sites, the lead's picture across the width at its own proportions, the lead's
 * title in large type and its subtitle (or the list's summary) under it, then every other report with its site and
 * time. A band separates it from the next. With a [match], the topic search's topic under the title. With
 * [pictureBelow], the picture comes after the lead's category and time instead, right above the other reports.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryItem(
    reports: List<ArticleSummary>,
    names: Map<String, String>,
    read: Set<String>,
    newUrls: Set<String>,
    justRead: Set<String>,
    onOpen: (ArticleSummary) -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = true,
    match: TopicMatch? = null,
    pictureBelow: Boolean = false,
    sourceLabels: SourceLabels = SourceLabels.BELOW,
    sentenceLines: Boolean = false,
    /** How many sentences a generated card shows ([AppSettings.feedSentences]); 0: all. */
    feedSentences: Int = 0,
    onThread: (news.app.server.ThreadRef, String) -> Unit = { _, _ -> },
    /** The story's generated article (the news server's): shown in place of the lead's title and subtitle. */
    summary: StorySummary? = null,
    onFootnote: (Footnote) -> Unit = {},
    onSummary: () -> Unit = {},
    /** With a [summary]: show the lead's own title and subtitle (the original) instead of the generated article. */
    original: Boolean = false,
    onFlip: () -> Unit = {},
    /** With a [summary]: still to read as a generated story ([unreadGenerated]); its "חדש" goes by this. */
    generatedUnread: Boolean = false,
    /** With a [summary] written more than once: its versions. */
    onVersions: (() -> Unit)? = null,
    /** With a [summary]: what's new in it since the last opening ([cardUpdate]); null for nothing. */
    update: CardUpdate? = null,
) {
    val lead = reports.first()
    val leadRead = lead.url in read
    // A report is new to the owner when it arrived since the last visit and they haven't read it. A story they had
    // already read says it has a new report, rather than looking new as a whole.
    val newReports = reports.mapNotNullTo(HashSet()) { r -> r.url.takeIf { it in newUrls && it !in read } }
    val storyRead = reports.any { it.url in read }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        // A generated story shows its sites in the facts' footnotes: no row of site labels on top, only "חדש".
        val generatedView = summary != null && !original
        // A generated story's card opens its page; another, the reader at its lead.
        Column(Modifier.fillMaxWidth().clickable { if (generatedView) onSummary() else onOpen(lead) }.padding(top = 14.dp)) {
            // The headline (the written one, or the lead's); one line of details (category, time, "✦ כתבה מ־N
            // אתרים", the version, ⇄); the sites; the threads; the picture; then the article.
            Text(
                if (generatedView && summary != null) summary.title else lead.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // What's new since the story was last opened: its newest development or correction, under the title.
            if (generatedView) update?.line?.let {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) { UpdateLine(it, update.others, names, onSummary) }
            }
            Spacer(Modifier.height(6.dp))
            // The story's categories: every one among its reports (the lead's first), leaving out reports the story
            // set aside as about something else.
            val details = @Composable {
                if (showCategory) storyCategories(reports, summary?.unrelated.orEmpty()).forEach { CategoryLabel(categoryName(it)) }
                if (lead.video) VideoLabel()
                ago(lead.publishedAt)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
                // Brought by the topic search: which topic, how close (the best of its reports).
                match?.let { TopicMatchLabel(it) }
                if (leadRead) ReadMark(animate = lead.url in justRead)
            }
            val badge = when {
                // An updated story says עודכן instead.
                summary != null -> if (generatedUnread && !(generatedView && update != null)) (if (storyRead) "דיווח חדש" else "חדש") else null
                newReports.isNotEmpty() -> if (storyRead) "דיווח חדש" else "חדש"
                else -> null
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (summary != null) {
                    GeneratedHeader(summary, citedSites(news.app.stories.Story(reports), summary).size, lead, names, original, onFlip, onSummary,
                        badge = badge, onVersions = onVersions, details = details, flip = false, short = true, updated = generatedView && update != null)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        badge?.let { NewBadge(it) }
                        details()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Many sites wrap onto more lines rather than run off the edge.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (generatedView && summary != null) {
                    // The sites its text cites, once each: a tap opens that site's newest article in it.
                    storySites(summary).forEach { note ->
                        SitePill(note.sourceId.orEmpty(), note.sourceId?.let { names[it] ?: it } ?: "מקור", onClick = { onFootnote(note) }, video = note.video)
                    }
                } else {
                    // The sites that report it, a headline on its own included: its one site. The lead's, whose
                    // picture and title follow, filled. Their full names: this line wraps, unlike the
                    // narrow column of the reports below, which keeps the short ones.
                    reports.map { it.sourceId }.distinct().forEach { id ->
                        SitePill(id, names[id] ?: id, highlight = id == lead.sourceId)
                    }
                }
            }
            summary?.takeIf { generatedView && it.threads.isNotEmpty() }?.let { written ->
                ThreadLines(written.threads, { onThread(it, written.id) }, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
            // The picture, right above the article.
            val picture = @Composable {
                lead.imageUrl?.let { url ->
                    Spacer(Modifier.height(10.dp))
                    // The picture's box has its height before the image is drawn (its proportions, once seen, else 16:9),
                    // so a story scrolled back into view doesn't grow and push the list.
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { pictureRatios.record(url, it.painter.intrinsicSize) },
                        // Edge to edge: no margin, so no rounded corners either. Moves into the article when it opens.
                        modifier = Modifier.sharedPicture(lead.url).fillMaxWidth().aspectRatio(pictureRatios[url] ?: (16f / 9f)),
                    )
                }
            }
            if (!pictureBelow) picture()
            Spacer(Modifier.height(10.dp))
            if (summary != null) {
                GeneratedPart(summary, citedSites(news.app.stories.Story(reports), summary).size, lead, names, original, onFlip, onSummary, onFootnote, sourceLabels, sentenceLines, onThread,
                    maxSentences = feedSentences,
                    onVersions = onVersions,
                    threads = false,
                    sourcesRow = false,
                    title = false,
                    header = false)
            }
            (lead.subtitle ?: lead.summary)?.takeIf { summary == null }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (pictureBelow) picture()
            Spacer(Modifier.height(6.dp))
        }
        // Reports the generated article found to be about another event: apart, under their own muted label.
        val unrelated = summary?.unrelated.orEmpty().toSet()
        // A generated story lists no original headlines: its facts' footnotes open the sources (⇄ shows the original).
        val listed = if (generatedView) emptyList() else reports.drop(1)
        val (others, offTopic) = listed.partition { it.url !in unrelated }
        others.forEach { report ->
            val reportRead = report.url in read
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(report) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The pill and the read mark lower, onto the title's first line: the letters sit low in their line.
                Column(Modifier.width(80.dp).padding(top = 4.dp), horizontalAlignment = Alignment.Start) {
                    SitePill(report.sourceId, shortSiteName(report.sourceId, names))
                    Text(
                        ago(report.publishedAt) ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // A report with a video starts its title with a film icon, a thin space before the first word.
                Text(
                    buildAnnotatedString {
                        if (report.video) {
                            appendInlineContent(VIDEO_ICON)
                            append("\u2009")
                        }
                        append(report.title)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    inlineContent = if (report.video) videoIcon(muted) else emptyMap(),
                    modifier = Modifier.weight(1f),
                )
                if (reportRead) Box(Modifier.padding(top = 5.dp)) { ReadMark(label = false, animate = report.url in justRead) }
            }
        }
        if (offTopic.isNotEmpty()) {
            Text(
                "לא קשור לסיפור",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
            offTopic.forEach { report ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(report) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.width(80.dp).padding(top = 2.dp)) { SitePill(report.sourceId, shortSiteName(report.sourceId, names)) }
                    Text(report.title, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 6.dp, color = MaterialTheme.colorScheme.surfaceContainer)
    }
}

private const val VIDEO_ICON = "video"

/** The film icon at the start of a report's title ([VIDEO_ICON]), centered on the line. */
private fun videoIcon(tint: Color) = mapOf(
    VIDEO_ICON to InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Filled.Movie, contentDescription = "וידאו", tint = tint, modifier = Modifier.fillMaxSize())
    },
)

/** One article on its own (the reader shows them in a pager; this is the standalone form the UI lab draws). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    state: ArticleState,
    sourceName: String,
    language: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    AppLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(sourceName) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                    actions = {
                        IconButton(onClick = { shareArticle(context, state.summary) }) { Icon(Icons.Filled.Share, "שיתוף") }
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.summary.url))) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "פתיחה באתר")
                        }
                    },
                )
            },
        ) { padding ->
            ArticleContent(state, sourceName, language, onRetry, Modifier.padding(padding))
        }
    }
}

/** The system share sheet with the headline and its link. */
internal fun shareArticle(context: Context, summary: ArticleSummary) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, summary.title + "\n" + summary.url)
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * An article as it loads, fails or shows. [active]: it is the page on screen; a video elsewhere stops. [next] is the
 * headline a swipe brings, named in a card at the end that a tap turns to ([onNext]); without one the end of the
 * list is announced.
 */
@Composable
internal fun ArticleContent(
    state: ArticleState,
    sourceName: String,
    language: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    next: ArticleSummary? = null,
    nextSite: String? = null,
    nextIsStory: Boolean = false,
    onNext: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            // While it loads: what the list knew, title and picture, with a spinner below, so the picture moving in
            // from a story has its place before the article is in. One call for both, so the picture stays the same
            // composable when the article arrives, mid-move included.
            is ArticleState.Loading, is ArticleState.Loaded -> {
                val loaded = state as? ArticleState.Loaded
                ArticleBody(
                    loaded ?: ArticleState.Loaded(state.summary, state.summary.preview()), sourceName, language,
                    active = active && loaded != null, next = next, nextSite = nextSite, nextIsStory = nextIsStory, onNext = onNext,
                    loading = loaded == null,
                )
            }
            // Not loaded (a site that blocks, no connection): what the list knew, title, subtitle and picture, with
            // a word on why and the ways on.
            is ArticleState.Failed -> ArticleBody(
                ArticleState.Loaded(state.summary, state.summary.preview()), sourceName, language,
                active = false, next = next, nextSite = nextSite, nextIsStory = nextIsStory, onNext = onNext,
                failure = state.message, onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun ArticleBody(
    state: ArticleState.Loaded,
    sourceName: String,
    language: String?,
    active: Boolean,
    next: ArticleSummary?,
    nextSite: String?,
    nextIsStory: Boolean,
    onNext: () -> Unit,
    loading: Boolean = false,
    failure: String? = null,
    onRetry: () -> Unit = {},
) {
    val article = state.article
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Pictures and video run edge to edge; text keeps this inset on both sides.
    val inset = 16.dp
    val text = Modifier.padding(horizontal = inset)
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp)
    ContentDirection(language) {
        // Where the article's visible area starts, in the window: a playing video sticks there ([VideoCard]); below
        // the status bar, when the page runs up under it (the reader's header gone).
        var viewportTop by remember { mutableFloatStateOf(0f) }
        val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
        Column(Modifier.onGloballyPositioned { viewportTop = it.positionInRoot().y }.verticalScroll(rememberScrollState()).padding(vertical = inset)) {
            Text(
                article.title,
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp),
                fontWeight = FontWeight.Bold,
                modifier = text,
            )
            article.subtitle?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp), color = muted, modifier = text)
            }
            Spacer(Modifier.height(10.dp))
            Row(text, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.summary.category?.let { CategoryLabel(categoryName(it)) }
                SitePill(article.sourceId, sourceName)
                article.author?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
                ago(article.publishedAt)?.let { Text("· $it", style = MaterialTheme.typography.labelMedium, color = muted) }
            }
            val mainImage = article.imageUrl ?: state.summary.imageUrl
            val leadImage = leadImage(mainImage, article.blocks)
            val video = article.video
            if (video != null) {
                Spacer(Modifier.height(12.dp))
                VideoCard(video, article, fallbackPoster = mainImage, active = active, stickTo = { maxOf(viewportTop, statusBar) })
            } else if (leadImage != null) {
                Spacer(Modifier.height(12.dp))
                // At its own proportions, as in the stories list. Until its file is in, the list's picture stands in
                // (from memory): it is what moves in from a story, so the move never shows an empty frame.
                val listPicture = state.summary.imageUrl
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(leadImage)
                        .apply { if (listPicture != null) placeholderMemoryCacheKey(listPicture) }.build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { pictureRatios.record(leadImage, it.painter.intrinsicSize) },
                    modifier = Modifier.sharedPicture(state.summary.url).fillMaxWidth()
                        .aspectRatio(pictureRatios[leadImage] ?: listPicture?.let { pictureRatios[it] } ?: (16f / 9f)),
                )
            }
            if (loading) {
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            if (failure != null) {
                val context = LocalContext.current
                Spacer(Modifier.height(20.dp))
                Row(text, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, null, tint = muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("הכתבה המלאה לא נטענה", style = MaterialTheme.typography.titleSmall, color = muted)
                }
                Text(failure, style = MaterialTheme.typography.bodySmall, color = muted, modifier = text)
                Spacer(Modifier.height(12.dp))
                Row(text) {
                    Button(onClick = onRetry) { Text("ניסיון נוסף") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.summary.url))) }) {
                        Text("פתיחה באתר")
                    }
                }
                return@Column
            }
            if (article.blocks.isNotEmpty()) {
                // The main picture shown in the text instead of above it: that photo is the one a story's picture moves to.
                val mainInText = if (video == null) mainPhotoInText(mainImage, article.blocks) else null
                article.blocks.forEachIndexed { i, block ->
                    Spacer(Modifier.height(14.dp))
                    when (block) {
                        is ArticleBlock.Text -> Text(block.text, style = body, modifier = text)
                        is ArticleBlock.Image -> if (i == mainInText) {
                            ArticlePhoto(block, inset, standIn = state.summary.imageUrl, pictureModifier = Modifier.sharedPicture(state.summary.url))
                        } else {
                            ArticlePhoto(block, captionInset = inset)
                        }
                    }
                }
            } else {
                article.body.forEach { paragraph ->
                    Spacer(Modifier.height(14.dp))
                    Text(paragraph, style = body, modifier = text)
                }
            }
            if (article.body.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("לא נמצא טקסט לכתבה. פתחו אותה באתר.", style = MaterialTheme.typography.bodyMedium, modifier = text)
            }
            Spacer(Modifier.height(24.dp))
            NextCard(next, nextSite, nextIsStory, onNext)
        }
    }
}

/** What a swipe brings: the next headline (or story), or word that the list is over. */
@Composable
private fun NextCard(next: ArticleSummary?, nextSite: String?, nextIsStory: Boolean, onNext: () -> Unit) {
    if (next == null) {
        Text(
            "זה הכול ברשימה הזאת",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        return
    }
    Card(
        onClick = onNext,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                listOfNotNull(if (nextIsStory) "הסיפור הבא" else "הבא", nextSite, next.category?.let(::categoryName)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(next.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lays out [content] right-to-left for RTL languages, so Hebrew articles read naturally in any UI locale. */
@Composable
internal fun ContentDirection(language: String?, content: @Composable () -> Unit) {
    val rtl = language?.substringBefore('-') in RTL_LANGUAGES
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
}

private val RTL_LANGUAGES = setOf("he", "iw", "ar", "fa", "ur", "yi")

/** The article as far as its headline knows it, shown while the article loads. */
private fun ArticleSummary.preview() = Article(
    sourceId = sourceId, url = url, title = title, subtitle = subtitle ?: summary, publishedAt = publishedAt, imageUrl = imageUrl,
)

/**
 * Where [url]'s row sits in the list: its story's, or its own when it is in none; [before] rows (a failure banner, the
 * model's offer) come first. Null when it isn't on the list.
 */
private fun listIndexOf(url: String, shown: List<Story>, before: Int, lastNew: Int?): Int? =
    shown.indexOfFirst { s -> s.reports.any { it.url == url } }.takeIf { it >= 0 }
        ?.let { before + it + if (lastNew != null && it > lastNew) 1 else 0 }

/** After the last new story: a quiet line saying where the new ones end. */
@Composable
private fun NewEnd(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("עד כאן החדשים", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** The headlines a list row stands for, by its key: a story's reports, one headline's URL, or none (a header, a banner). */
private fun rowUrls(key: Any, feed: FeedView): List<String> {
    val name = key as? String ?: return emptyList()
    if (name.startsWith("story-")) {
        val lead = name.removePrefix("story-")
        val story = feed.all.firstOrNull { it.lead.url == lead } ?: feed.stories.firstOrNull { it.lead.url == lead }
        return story?.reports?.map { it.url }.orEmpty()
    }
    return if (name.startsWith("http")) listOf(name) else emptyList()
}

/** Each row of the list, in order, by its key ([LazyColumn]'s), with its picture. */
private fun rowPictures(feed: FeedView): List<Pair<String, String?>> = feed.all.map { "story-" + it.lead.url to it.lead.imageUrl }
