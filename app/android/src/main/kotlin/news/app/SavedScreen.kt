package news.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.time.Instant
import news.app.server.FollowedStory
import news.app.server.ThreadRef
import news.core.model.ArticleSummary

/**
 * Saved and followed, in two tabs. The saved articles, latest saved first: each its headline, site, when it was
 * published and when saved, and its picture. Tapping one opens the reader, to swipe through the saved ones; the
 * bookmark takes it out of the list. The stories followed: [FollowingList].
 */
@Composable
fun SavedScreen(
    saved: List<SavedArticle>,
    sources: List<Site>,
    read: Set<String>,
    onOpen: (ArticleSummary) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    /** 0: the saved articles; 1: the stories followed ([FollowingList]). */
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    following: List<FollowedStory>? = emptyList(),
    followUnseen: Int = 0,
    followShownSince: Instant = Instant.DISTANT_PAST,
    onFollowedStory: (FollowedStory, String) -> Unit = { _, _ -> },
    onThread: (ThreadRef) -> Unit = {},
    onUnfollow: (FollowedStory) -> Unit = {},
    onSawFollowing: () -> Unit = {},
) {
    val names = sources.associate { it.id to it.name }
    val languages = sources.associate { it.id to it.language }
    if (tab == 1) LaunchedEffect(following) { onSawFollowing() }
    AppLayout {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
                    Text(if (tab == 0) "שמורים" else "במעקב", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, onClick = { onTab(0) }, text = { Text(if (saved.isEmpty()) "שמורים" else "שמורים · ${saved.size}") })
                    Tab(tab == 1, onClick = { onTab(1) }, text = {
                        BadgedBox(badge = { if (followUnseen > 0 && tab != 1) Badge { Text("$followUnseen") } }) {
                            Text(if (following.isNullOrEmpty()) "במעקב" else "במעקב · ${following.size}")
                        }
                    })
                }
                if (tab == 1) {
                    FollowingList(following, followShownSince, onFollowedStory, onThread, onUnfollow)
                } else if (saved.isEmpty()) {
                    Text(
                        "עוד אין כתבות שמורות. בכתבה, הסימנייה למעלה שומרת אותה כאן.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(saved, key = { it.summary.url }) { item ->
                            SavedRow(item, names[item.summary.sourceId] ?: item.summary.sourceId, languages[item.summary.sourceId], item.summary.url in read, { onOpen(item.summary) }, { onRemove(item.summary.url) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRow(item: SavedArticle, site: String, language: String?, read: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val summary = item.summary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        summary.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ContentDirection(language) {
                Text(
                    summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                listOfNotNull(site, summary.publishedAt?.let(::ago), ago(item.savedAt)?.let { "נשמרה $it" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Bookmark, "הסרה מהשמורים", tint = MaterialTheme.colorScheme.primary) }
    }
}
