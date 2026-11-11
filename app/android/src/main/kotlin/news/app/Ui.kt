package news.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.time.Instant
import news.core.model.Categories

// What the app's screens share: its direction, its short Hebrew times, the names and colors of sites and categories.

/** The app's own text is Hebrew, so its screens lay out right to left whatever the phone's language. */
@Composable
fun AppLayout(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)

/** Short Hebrew relative time: "עכשיו", "לפני 4 דק׳", "לפני שעה", "לפני 3 שע׳", "אתמול", "לפני 3 ימים". Null without a time. */
fun ago(instant: Instant?, now: Long = System.currentTimeMillis()): String? {
    val minutes = ((now - (instant ?: return null).toEpochMilliseconds()) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "עכשיו"
        minutes < 60 -> "לפני $minutes דק׳"
        minutes < 120 -> "לפני שעה"
        minutes < 24 * 60 -> "לפני ${minutes / 60} שע׳"
        minutes < 48 * 60 -> "אתמול"
        else -> "לפני ${minutes / (24 * 60)} ימים"
    }
}

/** The line under the title: "עודכן לפני 4 דק׳ · 7 חדשות", or what stands in for it while refreshing or before the first fetch. */
fun checkInLine(refreshedAt: Instant?, newCount: Int, refreshing: Boolean, now: Long = System.currentTimeMillis()): String = when {
    refreshing -> "מרענן…"
    refreshedAt == null -> "עדיין לא עודכן"
    else -> listOfNotNull("עודכן ${ago(refreshedAt, now)}", if (newCount > 0) "$newCount חדשות" else null).joinToString(" · ")
}

/** Display names for [Categories.IDS]; null is everything a site did not categorize. */
fun categoryName(id: String?): String = when (id) {
    Categories.ISRAEL -> "בארץ"
    Categories.POLITICS -> "פוליטיקה"
    Categories.SECURITY -> "ביטחון"
    Categories.WORLD -> "עולם"
    Categories.ECONOMY -> "כלכלה"
    Categories.LAW -> "משפט ופלילים"
    Categories.OPINION -> "דעות"
    Categories.SPORT -> "ספורט"
    Categories.CULTURE -> "תרבות ובידור"
    Categories.TECH -> "טכנולוגיה"
    Categories.HEALTH -> "בריאות"
    Categories.LIFESTYLE -> "צרכנות ופנאי"
    else -> "אחר"
}

/** A site's name for a row of them: the name the server gives it. */
fun shortSiteName(id: String, names: Map<String, String>): String = names[id] ?: id

/**
 * A hue per site, so its label is recognizable before it is read: worked out from its id, the same on every phone.
 * The server sends no colors.
 */
fun siteTint(id: String): Color = Color.hsl((id.hashCode() and 0x7fffffff) % 360f, 0.6f, 0.4f)

/**
 * The site's name on a tint of its own (lightened in dark theme). [highlight] fills it with the tint instead, white
 * letters: the site of the article it stands by.
 */
@Composable
fun SitePill(id: String, name: String, onClick: (() -> Unit)? = null, highlight: Boolean = false, video: Boolean = false) {
    val dark = isSystemInDarkTheme()
    val tint = siteTint(id).let { if (dark && !highlight) lerp(it, Color.White, 0.45f) else it }
    val color = if (highlight) Color.White else tint
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlight) tint else tint.copy(alpha = 0.13f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        // [video]: the article has a video, a film icon inside the label.
        if (video) Icon(Icons.Filled.Movie, contentDescription = "וידאו", tint = if (highlight) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}

/** "חדש", "4 חדשות": filled with the primary color. */
@Composable
fun NewBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "✓ נקרא" in the primary color: marks a headline the reader has opened, which otherwise looks like the rest. */
@Composable
fun ReadMark(label: Boolean = true, animate: Boolean = false) {
    // [animate]: just read, so it pops in (after a beat, to be seen on coming back to the list); else it is simply there.
    val shown = remember { MutableTransitionState(!animate) }
    shown.targetState = true
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(350, delayMillis = 300)) + scaleIn(tween(450, delayMillis = 300, easing = EaseOutBack), initialScale = 0.3f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, if (label) null else "נקרא", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            if (label) Text(" נקרא", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A category as a quiet outlined label, beside a site's tinted one. */
@Composable
fun CategoryLabel(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** An article with a video: a film icon outlined like [CategoryLabel] beside it, and of its height. */
@Composable
fun VideoLabel() {
    Box(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(Icons.Filled.Movie, contentDescription = "וידאו", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** A paragraph in the list, with an optional button under it. */
@Composable
fun Message(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.padding(24.dp)) {
        Text(text)
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) { Text(label) }
        }
    }
}

/**
 * Frosted glass over the status bar's strip: once a header has gone up ([CollapsingHeader]), the page under the
 * status bar ([state]'s source) shows blurred and lightened, its icons readable. Put it over the page, which starts
 * [pageTop] below the screen's top: it moves up by that much to sit on the status bar, and a header that is showing
 * lies over it. Blurs from Android 12; before that, a lighter shade only. [pageTop] is read as it is laid out, so a
 * header sliding away moves the glass without composing it again.
 */
@Composable
fun StatusBarGlass(state: HazeState, pageTop: Density.() -> Dp, modifier: Modifier = Modifier, solid: Boolean = false) {
    val surface = MaterialTheme.colorScheme.surface
    val strip = modifier
        .offset { IntOffset(0, -pageTop().roundToPx()) }
        .fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBars)
    // [solid]: the page's color, no blur (a video playing on the page, which the blur doesn't take on the owner's phone).
    Box(
        if (solid) {
            strip.background(surface)
        } else {
            strip.hazeEffect(state) {
                backgroundColor = surface
                blurRadius = 20.dp
                tints = listOf(HazeTint(surface.copy(alpha = 0.35f)))
            }
        },
    )
}
