package news.app

/**
 * Option c's state: the story page's state plus which of the latest version's sentences each update wrote (by index),
 * so the page can mark them. The server would send this beside the updates; on promotion it moves to NewsViewModel.kt.
 */
data class WhatsNewCState(val page: WhatsNewState, val sentenceUpdates: Map<Int, StoryUpdateItem>) {
    /** The sentences written by an update the reader hasn't seen. */
    val marked: Map<Int, StoryUpdateItem> get() = sentenceUpdates.filterValues { u -> page.lastSeen == null || u.at > page.lastSeen }

    /** The newest development or correction the reader hasn't seen: the feed card's "חדש:" line. */
    val latest: StoryUpdateItem? get() = page.unseen.firstOrNull { it.kind.headline }
}
