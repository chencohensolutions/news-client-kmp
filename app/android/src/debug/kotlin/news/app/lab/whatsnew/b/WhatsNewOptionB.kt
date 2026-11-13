package news.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import news.app.server.Footnote
import kotlin.time.Instant

/**
 * Option b — the story page as a timeline of its updates, newest first: each version a dot on a line with its time
 * and its updates (kind, text, sites), the ones since the last visit marked "חדש"; the first report closes the line.
 * The full story waits collapsed below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreenB(state: WhatsNewState, names: Map<String, String>, onFootnote: (Footnote) -> Unit, onBack: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var full by rememberSaveable { mutableStateOf(false) }
    AppLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("כתבה מסוכמת", maxLines = 1)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.summary.title, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp), fontWeight = FontWeight.Bold)
                Text(
                    "${state.updates.size} עדכונים מאז ${timeOf(state.firstWrittenAt)} · ${state.story.reports.size} כתבות",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                Column {
                    val versions = state.updates.groupBy { it.version }.toSortedMap(compareByDescending { it })
                    versions.forEach { (_, items) ->
                        val at = items.first().at
                        TimelineNode(at, unseen = state.lastSeen == null || at > state.lastSeen, last = false) {
                            items.forEach { u -> TimelineUpdate(u, names, onFootnote) }
                        }
                    }
                    TimelineNode(state.firstWrittenAt, unseen = false, last = true, label = "הדיווח הראשון") {
                        state.summary.sentences.firstOrNull()?.let { Text(it.text, style = MaterialTheme.typography.bodyMedium, color = muted) }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().tappable { full = !full }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("הכתבה המלאה", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${state.summary.sentences.size} משפטים", style = MaterialTheme.typography.labelMedium, color = muted)
                            Icon(if (full) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = muted)
                        }
                        if (full) {
                            val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp)
                            state.summary.sentences.forEach { FactText(it, names, body) }
                        } else {
                            Text(
                                state.summary.sentences.joinToString(" ") { it.text },
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(Modifier.height(16.dp))
            }
        }
    }
}

/** A point on the timeline: a dot (filled when [unseen]) and the line down to the next, its time, then [content]. */
@Composable
private fun TimelineNode(at: Instant, unseen: Boolean, last: Boolean, label: String? = null, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val dot = if (unseen) MaterialTheme.colorScheme.primary else line
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(dot))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(line))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (last) 8.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unseen) NewBadge("חדש")
                Text(
                    listOfNotNull(label, ago(at), timeOf(at)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (unseen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun TimelineUpdate(u: StoryUpdateItem, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    Column(
        Modifier.fillMaxWidth().tappable { u.footnotes.firstOrNull()?.let(onFootnote) }.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UpdateKindLabel(u.kind, filled = true)
        UpdateText(u, MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
        SourceRow(u.footnotes, names, onFootnote)
    }
}
