package news.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.app.server.ServerThread
import news.app.server.ThreadRef
import news.app.server.ThreadStory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The threads map (מפת פרשות, the title bar's button): how the server's threads meet over time, from the UI lab's
// thread-map round, c and b together (docs/ux-history/threadmap/). Two views of the same threads, one bottom panel and
// one time slider: "מפה" (c), a dot per thread, its area by its stories, lines where threads share stories (dashed:
// only related), a dot between two threads for each story they share; "רצועות" (b), a strip per thread with its
// stories across the period. Only threads joined to another show on the map.

/** The map's two views; the last one chosen is kept. */
enum class ThreadMapView { MAP, STRIPS }

private val mapPalette = listOf(
    Color(0xFF3F7BE0), Color(0xFFE0703F), Color(0xFF3FA66B), Color(0xFFB04FC9), Color(0xFFD1A21F), Color(0xFF2FA9B8),
    Color(0xFFD64F6B), Color(0xFF7A8F2E), Color(0xFF8C6BD9), Color(0xFFCF6E3A),
)

/** The period a strip spans ("רצועות"). */
private enum class StripPeriod(val label: String, val days: Int) { DAY("יום", 1), WEEK("שבוע", 7), MONTH("חודש", 30) }

private sealed interface MapPick {
    data class Thread(val id: String) : MapPick
    data class Story(val id: String) : MapPick
}

private fun dayOf(at: kotlin.time.Instant, zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(zone).toLocalDate()

private fun shortWhen(at: kotlin.time.Instant, zone: ZoneId): String {
    val t = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(zone)
    return "${t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("he"))} ${t.dayOfMonth}.${t.monthValue} ${"%02d:%02d".format(t.hour, t.minute)}"
}

/** The threads' places: springs along shared stories and related links, threads pushing apart; run once, settled. */
private fun mapLayout(ids: List<String>, links: Map<Pair<String, String>, Int>, radius: (String) -> Float): Map<String, Offset> {
    val n = ids.size
    if (n == 0) return emptyMap()
    val pos = Array(n) { i -> val a = 2 * PI * i / n; Offset((260 * cos(a)).toFloat(), (260 * sin(a)).toFloat()) }
    val index = ids.withIndex().associate { (i, id) -> id to i }
    repeat(500) { step ->
        val force = Array(n) { Offset.Zero }
        for (i in 0 until n) for (j in i + 1 until n) {
            val d = pos[i] - pos[j]
            val dist = max(hypot(d.x, d.y), 1f)
            val push = d / dist * (90_000f / (dist * dist))
            force[i] += push
            force[j] -= push
        }
        links.forEach { (k, shared) ->
            val a = index[k.first] ?: return@forEach
            val b = index[k.second] ?: return@forEach
            val d = pos[b] - pos[a]
            val dist = max(hypot(d.x, d.y), 1f)
            val rest = radius(k.first) + radius(k.second) + 110f
            val pull = d / dist * ((dist - rest) * 0.05f * (0.6f + min(shared, 3) * 0.3f))
            force[a] += pull
            force[b] -= pull
        }
        val cool = 1f - step / 500f
        for (i in 0 until n) {
            val f = force[i] - pos[i] * 0.01f
            val len = hypot(f.x, f.y)
            pos[i] += if (len > 30f) f / len * 30f * cool else f * cool
        }
    }
    return ids.withIndex().associate { (i, id) -> id to pos[i] }
}

/**
 * The threads map: [threads] (null while they load), "מפה" or "רצועות" ([view]), a slider back in time with ▶, and a
 * bottom panel for the thread or story tapped. [onStory] opens a story (its article page, or its thread's).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadMapScreen(
    threads: List<ServerThread>?,
    view: ThreadMapView,
    onView: (ThreadMapView) -> Unit,
    onStory: (ThreadStory, ThreadRef) -> Unit,
    onThread: (ThreadRef) -> Unit,
    onBack: () -> Unit,
    line: RelatedLine = RelatedLine.MOST_CITED,
) {
    AppLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                    title = { Text("מפת פרשות") },
                    // The two views, icons only: the map, and the strips.
                    actions = {
                        listOf(Triple(ThreadMapView.MAP, Icons.Filled.Hub, "מפה"), Triple(ThreadMapView.STRIPS, Icons.Filled.ViewStream, "רצועות")).forEach { (v, icon, label) ->
                            // The view on: the primary color in a tonal circle; the other, a plain icon.
                            val on = view == v
                            IconButton(
                                onClick = { onView(v) },
                                modifier = Modifier.background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape),
                            ) {
                                Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (threads == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (threads.none { it.stories.isNotEmpty() }) {
                    Text("עוד אין פרשות. השרת מקשר את הסיפורים לפרשות פעם ביום.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
                } else {
                    MapBody(threads, view, onStory, onThread, line)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MapBody(
    threads: List<ServerThread>,
    view: ThreadMapView,
    onStory: (ThreadStory, ThreadRef) -> Unit,
    onThread: (ThreadRef) -> Unit,
    line: RelatedLine,
) {
    val zone = ZoneId.systemDefault()
    val all = threads.flatMap { it.stories }
    val startOf = { s: ThreadStory -> s.startedAt }
    val firstDay = dayOf(all.minOf(startOf), zone)
    val lastDay = maxOf(dayOf(all.maxOf { it.lastReportAt ?: it.startedAt }, zone), LocalDate.now())
    val dayCount = (lastDay.toEpochDay() - firstDay.toEpochDay()).toInt()
    var day by remember { mutableFloatStateOf(dayCount.toFloat()) }
    var playing by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<MapPick?>(null) }
    var panelUp by remember { mutableStateOf(false) }
    val cutoff = firstDay.plusDays(day.toLong() + 1).atStartOfDay(zone).toInstant().toEpochMilli()
    // What existed by the slider's day: each thread with its stories then.
    val shownThreads = threads.map { t -> t to t.stories.filter { it.startedAt.toEpochMilliseconds() < cutoff } }.filter { it.second.isNotEmpty() }
    val byId = threads.associateBy { it.ref.id }
    val color = threads.sortedBy { it.ref.id }.withIndex().associate { (i, t) -> t.ref.id to mapPalette[i % mapPalette.size] }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        if (day >= dayCount) day = 0f
        while (day < dayCount) {
            delay(900)
            day += 1f
        }
        playing = false
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (view) {
            ThreadMapView.MAP -> ConstellationMap(shownThreads, byId, color, pick) { pick = it }
            ThreadMapView.STRIPS -> Strips(shownThreads, byId, color, zone, LocalDate.ofEpochDay(firstDay.toEpochDay() + day.toLong()), pick) { pick = it }
        }
    }
    // Back in time, and play forward.
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { playing = !playing }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "עצירה" else "הפעלה")
        }
        Slider(day, { day = it; playing = false }, valueRange = 0f..max(dayCount, 1).toFloat(), steps = max(dayCount - 1, 0), modifier = Modifier.weight(1f))
        val shown = firstDay.plusDays(day.toLong())
        Text(
            if (shown == lastDay) "היום" else "${shown.dayOfMonth}.${shown.monthValue}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
    }
    MapPanel(pick, panelUp, { panelUp = !panelUp }, shownThreads, byId, color, zone, onPick = { pick = it }, onStory = onStory, onThread = onThread, line = line)
}

/** "מפה": a dot per thread joined to another, its area by its stories; lines and shared-story dots between them. */
@Composable
private fun ConstellationMap(
    shown: List<Pair<ServerThread, List<ThreadStory>>>,
    byId: Map<String, ServerThread>,
    color: Map<String, Color>,
    pick: MapPick?,
    onPick: (MapPick?) -> Unit,
) {
    // Shared stories between two threads, and related links, among what existed by then.
    val shared = HashMap<Pair<String, String>, Int>()
    val bridges = ArrayList<Pair<ThreadStory, List<String>>>()
    val storyThreads = HashMap<String, MutableList<String>>()
    shown.forEach { (t, stories) -> stories.forEach { storyThreads.getOrPut(it.id) { ArrayList() } += t.ref.id } }
    shown.flatMap { it.second }.distinctBy { it.id }.forEach { s ->
        val ids = storyThreads[s.id].orEmpty().sorted()
        if (ids.size > 1) {
            bridges += s to ids
            for (a in ids.indices) for (b in a + 1 until ids.size) shared.merge(ids[a] to ids[b], 1, Int::plus)
        }
    }
    val present = shown.map { it.first.ref.id }.toSet()
    val related = HashSet<Pair<String, String>>()
    shown.forEach { (t, _) -> t.related.map { it.id }.filter { it in present }.forEach { o -> related += minOf(t.ref.id, o) to maxOf(t.ref.id, o) } }
    val joined = (shared.keys + related).flatMap { listOf(it.first, it.second) }.toSet()
    val counts = shown.associate { (t, s) -> t.ref.id to s.size }
    // A dot's area by its stories: its radius by their square root.
    fun radiusOf(count: Int) = 10f + 7f * sqrt(count.toFloat())
    // The layout of every thread with all its stories, so dots stay put as the slider moves.
    val places = remember(byId) {
        val allShared = HashMap<Pair<String, String>, Int>()
        val owner = HashMap<String, MutableList<String>>()
        byId.values.forEach { t -> t.stories.forEach { owner.getOrPut(it.id) { ArrayList() } += t.ref.id } }
        owner.values.forEach { ids -> val s = ids.sorted(); for (a in s.indices) for (b in a + 1 until s.size) allShared.merge(s[a] to s[b], 1, Int::plus) }
        byId.values.forEach { t -> t.related.forEach { r -> if (r.id in byId) allShared.putIfAbsent(minOf(t.ref.id, r.id) to maxOf(t.ref.id, r.id), 0) } }
        val ids = allShared.keys.flatMap { listOf(it.first, it.second) }.distinct().sorted()
        mapLayout(ids, allShared) { radiusOf(byId[it]?.stories?.size ?: 0) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    val cam = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val edgeColor = MaterialTheme.colorScheme.outline
    val pickedThread = (pick as? MapPick.Thread)?.id
    val pickedStory = (pick as? MapPick.Story)?.id

    fun bridgeAt(i: Int, ids: List<String>): Offset? {
        val centers = ids.mapNotNull { places[it] }
        if (centers.size < 2) return null
        val mid = centers.reduce { a, b -> a + b } / centers.size.toFloat()
        val along = centers.last() - centers.first()
        val len = max(hypot(along.x, along.y), 1f)
        return mid + Offset(-along.y / len, along.x / len) * ((i % 3 - 1) * 16f)
    }

    if (joined.isEmpty()) {
        Text(
            "עוד אין פרשות שנפגשות: כשסיפור שייך לשתי פרשות, או ששתיים קשורות, הן יופיעו כאן.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    Canvas(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val before = cam.value + (centroid - center) / scale
                    val next = (scale * zoom).coerceIn(0.4f, 4f)
                    scale = next
                    scope.launch { cam.snapTo(before - (centroid - center) / next - pan / next) }
                }
            }
            .pointerInput(shown, scale, joined) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val world = cam.value + (tap - center) / scale
                    val bridge = bridges.withIndex().mapNotNull { (i, b) -> bridgeAt(i, b.second)?.let { b.first to it } }
                        .minByOrNull { (_, p) -> (p - world).getDistance() }
                    if (bridge != null && (bridge.second - world).getDistance() * scale < 20.dp.toPx()) {
                        onPick(MapPick.Story(bridge.first.id))
                        return@detectTapGestures
                    }
                    val hit = joined.filter { places[it] != null }.minByOrNull { ((places.getValue(it)) - world).getDistance() - radiusOf(counts[it] ?: 0) }
                    if (hit != null && (places.getValue(hit) - world).getDistance() <= radiusOf(counts[hit] ?: 0) + 14f) {
                        onPick(MapPick.Thread(hit))
                        scope.launch { cam.animateTo(places.getValue(hit), tween(450)) }
                    } else {
                        onPick(null)
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        fun screen(p: Offset) = center + (p - cam.value) * scale
        // Lines: shared stories (thicker for more), or only related (dashed).
        (shared.keys + related).distinct().forEach { k ->
            val a = places[k.first] ?: return@forEach
            val b = places[k.second] ?: return@forEach
            val n = shared[k] ?: 0
            val lit = pickedThread != null && (k.first == pickedThread || k.second == pickedThread)
            drawLine(
                edgeColor.copy(alpha = if (lit) 0.95f else if (n > 0) 0.6f else 0.4f),
                screen(a), screen(b),
                strokeWidth = if (n > 0) (2f + 3f * min(n, 5)) * min(scale, 1.6f) else 2f,
                pathEffect = if (n > 0) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        // A dot per thread joined to another.
        joined.forEach { id ->
            val p = places[id] ?: return@forEach
            val c = color[id] ?: edgeColor
            val r = radiusOf(counts[id] ?: 0) * scale
            drawCircle(c.copy(alpha = if (id == pickedThread) 0.95f else 0.8f), r, screen(p))
            if (id == pickedThread) drawCircle(labelColor, r + 5f, screen(p), style = Stroke(width = 3f))
        }
        // The stories two threads share: a small dot between them.
        bridges.forEachIndexed { i, (s, ids) ->
            val p = bridgeAt(i, ids) ?: return@forEachIndexed
            val sp = screen(p)
            val r = 6f * min(max(scale, 0.8f), 1.8f)
            drawCircle(labelColor.copy(alpha = 0.85f), r, sp)
            if (s.id == pickedStory) drawCircle(labelColor, r + 6f, sp, style = Stroke(width = 3f))
        }
        // Names under the dots.
        joined.forEach { id ->
            val p = places[id] ?: return@forEach
            val t = byId[id] ?: return@forEach
            val r = radiusOf(counts[id] ?: 0) * scale
            val text = measurer.measure(
                t.ref.name,
                UiTextStyle(fontSize = (11 * min(max(scale, 0.9f), 1.4f)).sp, fontWeight = FontWeight.SemiBold, color = labelColor, textAlign = TextAlign.Center),
                constraints = Constraints(maxWidth = 120.dp.roundToPx()),
            )
            val sp = screen(p)
            drawText(text, topLeft = Offset(sp.x - text.size.width / 2f, sp.y + r + 4f))
        }
    }
}

/** "רצועות": a strip per thread over the period that ends at the slider's day, its stories as dots; shared ones ringed. */
@Composable
private fun Strips(
    shown: List<Pair<ServerThread, List<ThreadStory>>>,
    byId: Map<String, ServerThread>,
    color: Map<String, Color>,
    zone: ZoneId,
    until: LocalDate,
    pick: MapPick?,
    onPick: (MapPick?) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(StripPeriod.WEEK) }
    val end = until.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val start = end - period.days * 86_400_000L
    val owners = HashMap<String, MutableList<String>>()
    shown.forEach { (t, stories) -> stories.forEach { owners.getOrPut(it.id) { ArrayList() } += t.ref.id } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.outlineVariant
    val rows = shown.map { (t, s) -> t to s.filter { it.startedAt.toEpochMilliseconds() in start until end } }.filter { it.second.isNotEmpty() }
        .sortedByDescending { (_, s) -> s.maxOf { it.startedAt } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StripPeriod.entries.forEach { p -> FilterChip(period == p, { period = p }, label = { Text(p.label) }) }
        }
        rows.forEach { (t, own) ->
            val c = color[t.ref.id] ?: track
            val on = (pick as? MapPick.Thread)?.id == t.ref.id
            Column(
                Modifier.fillMaxWidth().background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onPick(MapPick.Thread(t.ref.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(c, CircleShape))
                    Text(t.ref.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (own.size == 1) "סיפור אחד" else "${own.size} סיפורים", style = MaterialTheme.typography.labelMedium, color = muted)
                }
                // The period across, the latest at the end (left), as Hebrew reads.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Canvas(Modifier.fillMaxWidth().height(26.dp).padding(vertical = 4.dp)) {
                        val y = size.height / 2
                        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                        for (d in 0..period.days) {
                            if (period == StripPeriod.MONTH && d % 7 != 0) continue
                            val x = size.width - d * size.width / period.days
                            drawLine(track, Offset(x, y - 8f), Offset(x, y + 8f), strokeWidth = 2f)
                        }
                        own.forEach { s ->
                            val x = size.width - (s.startedAt.toEpochMilliseconds() - start).toFloat() / (end - start) * size.width
                            val other = owners[s.id].orEmpty().firstOrNull { it != t.ref.id }
                            drawCircle(c, radius = 7f, center = Offset(x, y))
                            if (other != null) drawCircle(color[other] ?: c, radius = 13f, center = Offset(x, y), style = Stroke(width = 5f))
                        }
                    }
                }
                val meets = own.flatMap { owners[it.id].orEmpty() }.filter { it != t.ref.id }.distinct()
                if (meets.isNotEmpty()) {
                    Text("נפגשת עם: ${meets.mapNotNull { byId[it]?.ref?.name }.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                val related = t.related.map { it.id }.filter { it !in meets }.mapNotNull { byId[it]?.ref?.name }
                if (related.isNotEmpty()) Text("קשורה ל: ${related.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            HorizontalDivider()
        }
    }
}

/** The bottom panel: the thread or story tapped, its links as chips; pulled up (a tap on the handle), more. */
@Composable
private fun MapPanel(
    pick: MapPick?,
    up: Boolean,
    onToggle: () -> Unit,
    shown: List<Pair<ServerThread, List<ThreadStory>>>,
    byId: Map<String, ServerThread>,
    color: Map<String, Color>,
    zone: ZoneId,
    onPick: (MapPick) -> Unit,
    onStory: (ThreadStory, ThreadRef) -> Unit,
    onThread: (ThreadRef) -> Unit,
    line: RelatedLine,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val storiesOf = shown.associate { (t, s) -> t.ref.id to s }
    val threadsOf = HashMap<String, MutableList<String>>()
    shown.forEach { (t, s) -> s.forEach { threadsOf.getOrPut(it.id) { ArrayList() } += t.ref.id } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = if (up) 440.dp else 220.dp)) {
            Box(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(muted.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (pick) {
                    null -> Text("הקישו על פרשה או על סיפור.", style = MaterialTheme.typography.bodyMedium, color = muted)
                    is MapPick.Thread -> {
                        val t = byId[pick.id] ?: return@Column
                        val own = storiesOf[t.ref.id].orEmpty().sortedByDescending { it.startedAt }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(12.dp).background(color[t.ref.id] ?: muted, CircleShape))
                            Text(t.ref.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onThread(t.ref) }) { Text("לפרשה") }
                        }
                        if (own.isNotEmpty()) {
                            val from = dayOf(own.last().startedAt, zone)
                            val to = dayOf(own.first().startedAt, zone)
                            Text(
                                (if (own.size == 1) "סיפור אחד" else "${own.size} סיפורים") +
                                    if (from == to) " · ${from.dayOfMonth}.${from.monthValue}" else " · ${from.dayOfMonth}.${from.monthValue}–${to.dayOfMonth}.${to.monthValue}",
                                style = MaterialTheme.typography.labelMedium, color = muted,
                            )
                        }
                        if (t.summary.isNotBlank()) Text(t.summary, style = MaterialTheme.typography.bodyMedium, maxLines = if (up) Int.MAX_VALUE else 3)
                        val linked = (own.flatMap { threadsOf[it.id].orEmpty() } + t.related.map { it.id }).filter { it != t.ref.id && it in byId }.distinct()
                        if (linked.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                linked.forEach { id -> AssistChip(onClick = { onPick(MapPick.Thread(id)) }, label = { Text(byId[id]?.ref?.name.orEmpty()) }) }
                            }
                        }
                        if (up) {
                            own.forEach { s ->
                                Row(Modifier.fillMaxWidth().clickable { onPick(MapPick.Story(s.id)) }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(shortWhen(s.startedAt, zone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(96.dp))
                                    Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        } else if (own.isNotEmpty()) {
                            Text("הקישו על הידית לכל הסיפורים", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                    is MapPick.Story -> {
                        val owners = threadsOf[pick.id].orEmpty()
                        val s = owners.firstNotNullOfOrNull { id -> storiesOf[id]?.firstOrNull { it.id == pick.id } } ?: return@Column
                        Text(spanOf(s, LocalDate.now()).let { "${shortWhen(s.startedAt, zone).substringBeforeLast(' ')} · $it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        // One whole sentence: the one with the most sources, the first on a tie.
                        s.lead(line)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            owners.forEach { id -> AssistChip(onClick = { onPick(MapPick.Thread(id)) }, label = { Text(byId[id]?.ref?.name.orEmpty()) }) }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { byId[owners.first()]?.let { onStory(s, it.ref) } }) { Text("פתח כתבה") }
                        }
                    }
                }
            }
        }
    }
}
