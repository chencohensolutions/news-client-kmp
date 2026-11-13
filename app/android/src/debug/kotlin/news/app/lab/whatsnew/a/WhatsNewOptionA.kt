package news.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import news.app.server.Footnote

/**
 * Option a — a "מה חדש" block at the top of the story page: one line per development or correction since the reader's
 * last visit, each with its kind, its site and when; a tap opens that article. Details, background and reactions wait
 * behind "ועוד N". The story's text below is plain, with no marks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreenA(state: WhatsNewState, names: Map<String, String>, onFootnote: (Footnote) -> Unit, onBack: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(state.summary.title, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp), fontWeight = FontWeight.Bold)
                Text(
                    "עודכן ${ago(state.summary.writtenAt)} · ${state.story.reports.size} כתבות · נפתח לראשונה ${ago(state.firstWrittenAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                WhatsNewBlock(state, names, onFootnote)
                PartTitle("הכתבה")
                val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp)
                state.summary.sentences.forEach { FactText(it, names, body) }
                Spacer16()
            }
        }
    }
}

@Composable
private fun WhatsNewBlock(state: WhatsNewState, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unseen = state.unseen
    if (unseen.isEmpty()) {
        Text("אין חדש מאז ביקורך ${ago(state.lastSeen)}", style = MaterialTheme.typography.labelMedium, color = muted)
        return
    }
    val main = unseen.filter { it.kind.headline }
    val more = unseen.filterNot { it.kind.headline }
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("מה חדש", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    state.lastSeen?.let { "מאז ביקורך ${ago(it)}" } ?: "מאז הדיווח הראשון",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
            }
            main.forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                UpdateLine(u, names, onFootnote)
            }
            if (more.isNotEmpty()) {
                Row(
                    Modifier.tappable { open = !open }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (open) "פחות" else "ועוד ${more.size}: " + more.map { it.kind.label() }.distinct().joinToString(", "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (open) more.forEach { UpdateLine(it, names, onFootnote) }
            }
        }
    }
}

/** An update: its kind and when on top, its text, then its sites; the line opens its first article. */
@Composable
private fun UpdateLine(u: StoryUpdateItem, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    Column(
        Modifier.fillMaxWidth().tappable { u.footnotes.firstOrNull()?.let(onFootnote) }.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateKindLabel(u.kind)
            Text(ago(u.at).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UpdateText(u, MaterialTheme.typography.bodyLarge)
        SourceRow(u.footnotes, names, onFootnote)
    }
}

@Composable
private fun Spacer16() = androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
