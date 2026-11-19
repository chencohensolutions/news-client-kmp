package news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import news.app.server.FollowUpdate
import news.app.server.FollowedStory
import news.app.server.ThreadRef

/** How many of [following]'s updates came after [seenAt]: the badge on the Saved button. */
fun unseenUpdates(following: List<FollowedStory>, seenAt: Instant): Int = following.sumOf { f -> f.updates.count { it.at > seenAt } }

/** How many updates a followed story shows before "עוד". */
private const val SHOWN_UPDATES = 3

/**
 * The stories followed, latest followed first. Each: its title, since when, its threads, and its short updates, newest
 * first: a few lines of what changed, or of a story that joined its threads; the ones after [shownSince] marked new.
 * Tapping an update opens its story; a thread opens its page.
 */
@Composable
fun FollowingList(
    following: List<FollowedStory>?,
    shownSince: Instant,
    onStory: (FollowedStory, String) -> Unit,
    onThread: (ThreadRef) -> Unit,
    onUnfollow: (FollowedStory) -> Unit,
) {
    when {
        following == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        following.isEmpty() -> Text(
            "עוד אין סיפורים במעקב. בכתבה מסוכמת, \"מעקב\" למעלה מוסיף אותה לכאן, עם עדכונים קצרים כשהיא מתפתחת.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(20.dp),
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(following, key = { it.id }) { f ->
                FollowedCard(f, shownSince, { onStory(f, it) }, onThread, { onUnfollow(f) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FollowedCard(
    followed: FollowedStory,
    shownSince: Instant,
    onStory: (String) -> Unit,
    onThread: (ThreadRef) -> Unit,
    onUnfollow: () -> Unit,
) {
    var all by rememberSaveable(followed.id) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable { onStory(followed.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(followed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        ago(followed.followedAt)?.let { "במעקב מ$it" },
                        followed.updates.size.takeIf { it > 0 }?.let { if (it == 1) "עדכון אחד" else "$it עדכונים" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
            }
            TextButton(onClick = onUnfollow) { Text("הפסקת מעקב") }
        }
        ThreadLines(followed.threads, onThread, Modifier.padding(horizontal = 16.dp))
        if (followed.updates.isEmpty()) {
            Text(
                "עוד אין עדכונים. כשהסיפור יתעדכן, או כשסיפור חדש יצטרף לפרשה שלו, העדכון יופיע כאן בקצרה.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val shown = if (all) followed.updates else followed.updates.take(SHOWN_UPDATES)
        shown.forEach { u -> UpdateRow(u, followed, isNew = u.at > shownSince) { onStory(u.storyId) } }
        if (!all && followed.updates.size > SHOWN_UPDATES) {
            TextButton(onClick = { all = true }, modifier = Modifier.padding(start = 8.dp)) {
                Text("עוד ${followed.updates.size - SHOWN_UPDATES} עדכונים")
            }
        }
    }
}

/** One update: when, the story it is about when it isn't the followed one, and its few sentences. */
@Composable
private fun UpdateRow(update: FollowUpdate, followed: FollowedStory, isNew: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.padding(top = 6.dp).size(8.dp).background(
                if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(
                    ago(update.at),
                    when {
                        update.newStory -> "סיפור חדש בפרשה: ${update.storyTitle}"
                        update.storyId != followed.id -> update.storyTitle
                        else -> null
                    },
                    if (isNew) "חדש" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (isNew) MaterialTheme.colorScheme.primary else muted,
                fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
            )
            Text(update.sentences.joinToString(" ") { it.text }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
