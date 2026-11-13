package news.app

import news.app.server.Footnote
import news.app.server.StorySummary
import news.app.stories.Story
import kotlin.time.Instant

// The "מה חדש" round's state, shared by its options a, b and c: what the incremental server sends with each version of
// a story (its updates, each with a kind), which the app's ServerApi doesn't read yet. On promotion it moves to
// server/ServerApi.kt (UpdateKind, StoryUpdateItem) and NewsViewModel.kt (WhatsNewState).

/** What kind of news an update is, as the server's incremental writer tags it. */
enum class UpdateKind { DEVELOPMENT, CORRECTION, DETAIL, BACKGROUND, REACTION }

/**
 * One update of a story: a fact a version added, with its sources and when that version was written. A correction
 * also carries the value it fixes ([was] → [now]).
 */
data class StoryUpdateItem(
    val kind: UpdateKind,
    val text: String,
    val footnotes: List<Footnote>,
    val at: Instant,
    /** Which version of the story added it (1 is the first). */
    val version: Int,
    val was: String? = null,
    val now: String? = null,
)

/**
 * A story page with what changed in it: [summary] is its latest version, [updates] every update newest first, and
 * [lastSeen] when the reader last opened the story (null: never), so "new" means after it.
 */
data class WhatsNewState(
    val story: Story,
    val summary: StorySummary,
    val updates: List<StoryUpdateItem>,
    val firstWrittenAt: Instant,
    val lastSeen: Instant?,
) {
    /** The updates since [lastSeen] (all of them for a story never opened). */
    val unseen: List<StoryUpdateItem> get() = updates.filter { lastSeen == null || it.at > lastSeen }
}

/** The kind's Hebrew label. */
fun UpdateKind.label(): String = when (this) {
    UpdateKind.DEVELOPMENT -> "התפתחות"
    UpdateKind.CORRECTION -> "תיקון"
    UpdateKind.DETAIL -> "פרט חדש"
    UpdateKind.BACKGROUND -> "רקע"
    UpdateKind.REACTION -> "תגובה"
}

/** Developments and corrections: the news itself; the other kinds add to it. */
val UpdateKind.headline: Boolean get() = this == UpdateKind.DEVELOPMENT || this == UpdateKind.CORRECTION
