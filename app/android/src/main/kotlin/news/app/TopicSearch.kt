package news.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import news.app.stories.TopicMatch

// The feed's bottom controls and the topic search: a floating island of the three filters (חדשים, אתרים, נושאים)
// with a round search button beside it, the topics on floating above them, and the search's sheet; and the topic
// threshold's row in settings. From the UI lab's feed-nav round, option a2 (docs/ux-history/feed-nav/).

/**
 * The bottom of the feed, floating over the list: the topics on as chips (✕ switches one off), then the island of the
 * three filters, חדשים badged with how many headlines are new ([newCount]), and the round search button, badged with
 * how many topics are on.
 */
@Composable
internal fun FeedBottomBar(
    onlyNew: Boolean,
    newCount: Int,
    sitesOn: Boolean,
    categoriesOn: Boolean,
    topicsOn: List<String>,
    /** Only the stories with a generated article (the default); the toggle, on, shows the original ones too; null hides it. */
    onlyGenerated: Boolean?,
    onOnlyGenerated: () -> Unit,
    onOnlyNew: () -> Unit,
    onSites: () -> Unit,
    onCategories: () -> Unit,
    onSearch: () -> Unit,
    onTopicOff: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(topicsOn.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                items(topicsOn) { topic -> FloatingTopic(topic) { onTopicOff(topic) } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Island(onlyNew, newCount, sitesOn, categoriesOn, onOnlyNew, onSites, onCategories, onlyGenerated, onOnlyGenerated)
            SearchButton(topicsOn.size, onSearch)
        }
    }
}

/** The floating capsule of the three filters; the one on has its icon in a pill and its label in bold. */
@Composable
private fun Island(
    onlyNew: Boolean,
    newCount: Int,
    sitesOn: Boolean,
    categoriesOn: Boolean,
    onOnlyNew: () -> Unit,
    onSites: () -> Unit,
    onCategories: () -> Unit,
    onlyGenerated: Boolean?,
    onOnlyGenerated: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IslandItem(null, if (onlyNew) Icons.Filled.NewReleases else Icons.Outlined.NewReleases, onlyNew, onOnlyNew, badge = newCount, description = "חדשים")
            IslandItem(null, if (sitesOn) Icons.Filled.Language else Icons.Outlined.Language, sitesOn, onSites, description = "אתרים")
            IslandItem(null, if (categoriesOn) Icons.Filled.Sell else Icons.Outlined.Sell, categoriesOn, onCategories, description = "נושאים")
            if (onlyGenerated != null) {
                // On: the original stories show too, beside the generated ones.
                IslandItem(null, if (!onlyGenerated) Icons.Filled.Newspaper else Icons.Outlined.Newspaper, !onlyGenerated, onOnlyGenerated, description = "גם כותרות המקור")
            }
        }
    }
}

/** One filter of the [Island]; a [badge] above zero is shown on its icon (99+ past 99). */
@Composable
private fun IslandItem(label: String?, icon: ImageVector, on: Boolean, onClick: () -> Unit, badge: Int = 0, description: String? = null) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BadgedBox(badge = { if (badge > 0) Badge { Text(if (badge > 99) "99+" else "$badge") } }) {
                Icon(icon, description, tint = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (label != null) Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The round search button beside the island: filled when topics are on, with how many. */
@Composable
private fun SearchButton(onCount: Int, onClick: () -> Unit) {
    val filtering = onCount > 0
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (filtering) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 8.dp,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            BadgedBox(badge = { if (filtering) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("$onCount") } }) {
                Icon(Icons.Filled.Search, "חיפוש לפי נושא")
            }
        }
    }
}

/** A topic that is on, floating above the island; the ✕ switches it off (it stays saved). */
@Composable
private fun FloatingTopic(topic: String, onOff: () -> Unit) {
    Surface(
        onClick = onOff,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(topic, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Close, "כיבוי", Modifier.size(16.dp))
        }
    }
}

/** Under a story's title: the topic that brought it and how close, on the threshold's scale. */
@Composable
internal fun TopicMatchLabel(match: TopicMatch) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Text(" ${match.topic} · ${match.score}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The topic search's bottom sheet: a field to type a topic and add it (it is switched on), then the saved topics as
 * chips with how many headlines each brings ([counts]), a tap switching one on or off and ✕ deleting it; and what the
 * topics on bring ([rows] rows of the feed) at the threshold in settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TopicSheet(
    topics: List<SavedTopic>,
    counts: Map<String, Int>,
    rows: Int,
    threshold: Int,
    working: Boolean,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    val on = topics.filter { it.on }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            Text("חיפוש לפי נושא", style = MaterialTheme.typography.titleLarge)
            Text(
                "הסיפורים שאחת הכתבות בהם מזכירה את המילים של הנושאים הפעילים, בכותרת, בכותרת המשנה או בתקציר.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("למשל: אוכל, בית המשפט העליון") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (text.isNotBlank()) IconButton(onClick = add) { Icon(Icons.Filled.Add, "הוספה") }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("הנושאים שלי", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (topics.isEmpty()) {
                Text("עוד אין נושאים. הקלידו נושא והוסיפו אותו.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { topic ->
                    FilterChip(
                        selected = topic.on,
                        onClick = { onToggle(topic.text) },
                        label = { Text(counts[topic.text]?.let { "${topic.text} · $it" } ?: topic.text) },
                        // No check mark when on: it would widen the chip and move the ones after it.
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close, "מחיקה",
                                Modifier.size(FilterChipDefaults.IconSize).clip(CircleShape).clickable { onRemove(topic.text) },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        working -> "מחפש…"
                        on.isEmpty() -> "אין נושא פעיל: הפיד מלא"
                        else -> "$rows סיפורים וכותרות בהתאמה של $threshold ומעלה · הסף בהגדרות"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (on.isNotEmpty()) TextButton(onClick = { on.forEach { onToggle(it.text) } }) { Text("כיבוי הכל") }
            }
        }
    }
}

/**
 * The settings row for the topic search: how close in meaning a headline must be to a topic to show, a slider from
 * 10 to 90 like the stories' strictness, with how many headlines each saved topic brings at the value set ([counts]).
 * Applied once the thumb is let go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicThresholdRow(value: Int, topics: List<SavedTopic>, counts: Map<String, Int>, onChange: (Int) -> Unit) {
    var moving by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("סף התאמה", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${moving.roundToInt()}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "כמה ממילות הנושא, באחוזים, כותרת צריכה להזכיר כדי להופיע. 100: את כולן.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = moving,
            onValueChange = { moving = it },
            onValueChangeFinished = { onChange(moving.roundToInt()) },
            valueRange = 10f..100f,
            steps = 17,
        )
        if (topics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                topics.forEach { topic ->
                    Text("${topic.text} · ${counts[topic.text] ?: 0}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
