package news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import news.app.server.Footnote
import news.app.server.Sentence

// Pieces the "מה חדש" options share: an update's kind as a label, a correction's "2 → 3", a fact with its sites.
// On promotion they move with the picked option.

internal val UpdateKind.icon: ImageVector get() = when (this) {
    UpdateKind.DEVELOPMENT -> Icons.Filled.Bolt
    UpdateKind.CORRECTION -> Icons.Filled.SwapHoriz
    UpdateKind.DETAIL -> Icons.Filled.AddCircleOutline
    UpdateKind.BACKGROUND -> Icons.Filled.Info
    UpdateKind.REACTION -> Icons.Filled.Campaign
}

/** The kind's color: developments in the primary color, corrections in the error color, the rest muted. */
@Composable
internal fun UpdateKind.color(): Color = when (this) {
    UpdateKind.DEVELOPMENT -> MaterialTheme.colorScheme.primary
    UpdateKind.CORRECTION -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "⚡ התפתחות": the kind's icon and name in its color. */
@Composable
internal fun UpdateKindLabel(kind: UpdateKind, filled: Boolean = false) {
    val color = kind.color()
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (filled) color.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = if (filled) 6.dp else 0.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(kind.icon, null, Modifier.size(14.dp), tint = color)
        Text(kind.label(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** An update's text; a correction as "תיקון: 2 → 3" first, the old value struck through. */
@Composable
internal fun UpdateText(update: StoryUpdateItem, style: TextStyle = MaterialTheme.typography.bodyMedium, maxLines: Int = Int.MAX_VALUE) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    Text(
        buildAnnotatedString {
            if (update.kind == UpdateKind.CORRECTION && update.was != null && update.now != null) {
                withStyle(SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough)) { append(update.was) }
                append(" ← ")
                withStyle(SpanStyle(color = error, fontWeight = FontWeight.Bold)) { append(update.now) }
                append(" · ")
            }
            append(update.text)
        },
        style = style,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/** The sites an update or sentence comes from, as the app's site labels; a tap opens that article. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceRow(footnotes: List<Footnote>, names: Map<String, String>, onFootnote: (Footnote) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        footnotes.forEach { note ->
            val id = note.sourceId ?: return@forEach
            SitePill(id, names[id] ?: id, onClick = { onFootnote(note) })
        }
    }
}

/** A sentence of the story, its sites' names after it in their colors, small. */
@Composable
internal fun FactText(sentence: Sentence, names: Map<String, String>, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(sentence.text)
            sentence.footnotes.forEach { note ->
                val id = note.sourceId ?: return@forEach
                append(" ")
                withStyle(SpanStyle(color = siteTint(id), fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)) {
                    append((names[id] ?: id).replace(" ", "\u00A0"))
                }
            }
        },
        style = style,
        modifier = modifier,
    )
}

/** A heading for a part of the page: small, bold, muted. */
@Composable
internal fun PartTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A row that opens [onClick], for the update lines. */
internal fun Modifier.tappable(onClick: () -> Unit) = this.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)

@Composable
internal fun Spacing(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
