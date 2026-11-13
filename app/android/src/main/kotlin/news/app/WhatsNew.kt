package news.app

import news.app.server.Sentence
import news.app.server.StorySummary
import kotlin.time.Instant

// What's new in a generated story since the reader last opened it (from the UI lab's whats-new round, options a and
// c): the news server's updates, each sentence a version added with its kind. Developments and corrections are the
// news; details, background and reactions wait behind "ועוד N".

/** What a sentence an update added is, as the server's incremental writer tags it. */
enum class UpdateKind {
    DEVELOPMENT, CORRECTION, DETAIL, BACKGROUND, REACTION;

    /** The news itself, rather than something added to it. */
    val headline: Boolean get() = this == DEVELOPMENT || this == CORRECTION

    val label: String get() = when (this) {
        DEVELOPMENT -> "התפתחות"
        CORRECTION -> "תיקון"
        DETAIL -> "פרט חדש"
        BACKGROUND -> "רקע"
        REACTION -> "תגובה"
    }

    companion object {
        /** The server's word for it; null for one the app doesn't know. */
        fun of(kind: String?): UpdateKind? = entries.firstOrNull { it.name.equals(kind?.trim(), ignoreCase = true) }
    }
}

/** A sentence an update added, when, and its kind (null: the server didn't sort that version's sentences). */
data class WhatsNewLine(val sentence: Sentence, val at: Instant, val kind: UpdateKind?)

/**
 * The updates since [since], newest first: [lines] the developments and corrections, [more] the rest. A version whose
 * sentences the server didn't sort (no kinds: a rewrite, or a server before kinds) gives its first sentence to [lines]
 * and the others to [more].
 */
data class WhatsNew(val since: Instant, val lines: List<WhatsNewLine>, val more: List<WhatsNewLine>) {
    val isEmpty: Boolean get() = lines.isEmpty() && more.isEmpty()
    val count: Int get() = lines.size + more.size
}

/**
 * What [summary] added since the reader opened it at [since]; null for a story never opened (a first visit: all of it
 * is new). The first version is the story itself, never an update.
 */
fun whatsNew(summary: StorySummary, since: Instant?): WhatsNew? {
    since ?: return null
    val lines = ArrayList<WhatsNewLine>()
    val more = ArrayList<WhatsNewLine>()
    summary.updates.sortedBy { it.at }.drop(1).filter { it.at > since }.sortedByDescending { it.at }.forEach { u ->
        u.sentences.forEachIndexed { i, sentence ->
            if (u.kinds.isEmpty()) {
                (if (i == 0) lines else more) += WhatsNewLine(sentence, u.at, null)
            } else {
                val kind = UpdateKind.of(u.kinds.getOrNull(i))
                (if (kind?.headline == true) lines else more) += WhatsNewLine(sentence, u.at, kind)
            }
        }
    }
    return WhatsNew(since, lines, more)
}

/** A feed card's "חדש:" line: the newest news since the last opening ([line]: null when the server sent no updates). */
data class CardUpdate(val line: WhatsNewLine?, val others: Int)

/**
 * A card's update, when the story has a version newer than the reader's last opening ([seen]); null for a story never
 * opened (it is "חדש" as a whole) or with nothing newer.
 */
fun cardUpdate(summary: StorySummary, seen: Instant?): CardUpdate? {
    seen ?: return null
    if (summary.writtenAt <= seen) return null
    val news = whatsNew(summary, seen) ?: return null
    if (news.isEmpty) return if (summary.updates.isEmpty() && summary.versions > 1) CardUpdate(null, 0) else null
    return CardUpdate(news.lines.firstOrNull() ?: news.more.first(), news.count - 1)
}
