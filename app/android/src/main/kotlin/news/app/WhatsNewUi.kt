package news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import news.app.server.Footnote

// The what's new of a generated story ([WhatsNew]): "עודכן" and a "חדש:" line on its feed card, and the "מה חדש"
// block over its page's text. From the UI lab's whats-new round (docs/ux-history/whats-new/).

/** "עודכן": first on a card's "✦ כתבה מ־N אתרים" line when the story has news since it was last opened. */
@Composable
internal fun UpdatedBadge() {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        "עודכן",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = primary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(primary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A card's "חדש:" line under its title: the newest development or correction since the last opening, on a bar in its
 * kind's color, then its site, when, and how many more updates there are. A tap opens the page.
 */
@Composable
internal fun UpdateLine(line: WhatsNewLine, others: Int, names: Map<String, String>, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color = kindColor(line.kind)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                        append(if (line.kind == UpdateKind.CORRECTION) "תיקון: " else "חדש: ")
                    }
                    append(line.sentence.text)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.sentence.footnotes.firstNotNullOfOrNull { it.sourceId }?.let { SitePill(it, names[it] ?: it) }
                Text(ago(line.at).orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted)
                if (others > 0) {
                    Text("· " + if (others == 1) "ועוד עדכון אחד" else "ועוד $others עדכונים", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}

/**
 * The "מה חדש" block over a story page's text: the developments and corrections since the reader's last visit, a line
 * each (kind, when, text, sites; a tap opens its article), the other kinds behind "ועוד N", or one quiet line when
 * nothing came.
 */
@Composable
internal fun WhatsNewBlock(news: WhatsNew, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (news.isEmpty) {
        Text("אין חדש מאז ביקורך ${ago(news.since).orEmpty()}", style = MaterialTheme.typography.labelMedium, color = muted)
        return
    }
    var open by rememberSaveable(news.since.toEpochMilliseconds()) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("מה חדש", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("מאז ביקורך ${ago(news.since).orEmpty()}", style = MaterialTheme.typography.labelMedium, color = muted)
            }
            news.lines.forEachIndexed { i, line ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                BlockLine(line, names, onFootnote)
            }
            if (news.more.isNotEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    val kinds = news.more.mapNotNull { it.kind?.label }.distinct()
                    Text(
                        when {
                            open -> "פחות"
                            kinds.isEmpty() -> "ועוד ${news.more.size}"
                            else -> "ועוד ${news.more.size}: " + kinds.joinToString(", ")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (open) news.more.forEach { BlockLine(it, names, onFootnote) }
            }
        }
    }
}

/** A line of the block: its kind (or "עדכון") and when, its text, its sites; a tap opens its first article. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockLine(line: WhatsNewLine, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    val color = kindColor(line.kind)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { line.sentence.footnotes.firstOrNull()?.let(onFootnote) }.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(kindIcon(line.kind), null, Modifier.size(14.dp), tint = color)
                Text(line.kind?.label ?: "עדכון", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
            Text(ago(line.at).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(line.sentence.text, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            line.sentence.footnotes.forEach { note ->
                val id = note.sourceId ?: return@forEach
                SitePill(id, names[id] ?: id, onClick = { onFootnote(note) }, video = note.video)
            }
        }
    }
}

/** A kind's color: developments (and unsorted updates) in the primary color, corrections in the error color, the rest muted. */
@Composable
private fun kindColor(kind: UpdateKind?): Color = when (kind) {
    null, UpdateKind.DEVELOPMENT -> MaterialTheme.colorScheme.primary
    UpdateKind.CORRECTION -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun kindIcon(kind: UpdateKind?): ImageVector = when (kind) {
    null -> Icons.Filled.Update
    UpdateKind.DEVELOPMENT -> Icons.Filled.Bolt
    UpdateKind.CORRECTION -> Icons.Filled.SwapHoriz
    UpdateKind.DETAIL -> Icons.Filled.AddCircleOutline
    UpdateKind.BACKGROUND -> Icons.Filled.Info
    UpdateKind.REACTION -> Icons.Filled.Campaign
}
