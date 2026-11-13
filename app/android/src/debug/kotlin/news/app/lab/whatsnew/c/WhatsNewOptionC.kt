package news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import news.app.server.Footnote

/**
 * Option c — the feed card of an updated story says so: an "עודכן" badge and a "חדש:" line with its newest
 * development, its site and when. On the page, the sentences written since the last visit stand on a side bar in their
 * kind's color with a small label over them ("חדש · לפני 20 דק׳", "תיקון · 2 ← 3"), in place of word highlights.
 */
@Composable
fun WhatsNewCardC(state: WhatsNewCState, names: Map<String, String>, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sites = state.page.story.reports.map { it.sourceId }.distinct().size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.latest != null) UpdatedBadge()
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("כתבה מ־$sites אתרים", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(state.page.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            state.latest?.let { u ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(u.kind.color()))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = u.kind.color(), fontWeight = FontWeight.Bold)) {
                                    append(if (u.kind == UpdateKind.CORRECTION) "תיקון: " else "חדש: ")
                                }
                                append(u.text)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            u.footnotes.firstOrNull()?.sourceId?.let { SitePill(it, names[it] ?: it) }
                            Text(ago(u.at).orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted)
                            val more = state.page.unseen.size - 1
                            if (more > 0) Text("· ועוד $more עדכונים", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
            } ?: Text(state.page.summary.sentences.first().text, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 2)
        }
    }
}

/** "עודכן": a small outlined badge in the primary color, beside "חדש" which is filled. */
@Composable
private fun UpdatedBadge() {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        "עודכן",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = primary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(primary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreenC(state: WhatsNewCState, names: Map<String, String>, onFootnote: (Footnote) -> Unit, onBack: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val marked = state.marked
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
                Text(state.page.summary.title, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp), fontWeight = FontWeight.Bold)
                Text(
                    buildAnnotatedString {
                        append("עודכן ${ago(state.page.summary.writtenAt)}")
                        if (marked.isNotEmpty()) {
                            append(" · ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                append(if (marked.size == 1) "משפט אחד חדש מאז ביקורך" else "${marked.size} משפטים חדשים מאז ביקורך")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp)
                state.page.summary.sentences.forEachIndexed { i, s ->
                    val u = marked[i]
                    if (u == null) FactText(s, names, body)
                    else MarkedSentence(u) { FactText(s, names, body) }
                }
                Box(Modifier.height(16.dp))
            }
        }
    }
}

/** A sentence an unseen update wrote: a bar at its start in the kind's color, and the kind and time over it. */
@Composable
private fun MarkedSentence(u: StoryUpdateItem, content: @Composable () -> Unit) {
    val color = u.kind.color()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(if (u.kind == UpdateKind.DEVELOPMENT) "חדש" else u.kind.label())
                    }
                    if (u.kind == UpdateKind.CORRECTION && u.was != null && u.now != null) append(" · ${u.was} ← ${u.now}")
                    append(" · ${ago(u.at)}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            content()
        }
    }
}
