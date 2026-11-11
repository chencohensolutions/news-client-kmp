package news.app

import news.core.model.ArticleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderQueueTest {

    private fun page(vararg urls: String) = urls.map { ArticleSummary("s", it, it) }

    // Five pages; the second is a story with two reports.
    private val pages = listOf(page("a"), page("b1", "b2"), page("c"), page("d"), page("e"))

    @Test
    fun aStoryIsReadOnceAnyOfItsReportsIs() {
        assertFalse(isRead(pages[1], setOf("a")))
        assertTrue(isRead(pages[1], setOf("b2")))
    }

    @Test
    fun upcomingUnreadSkipsReadPagesAndWrapsToTheTop() {
        val read = setOf("a", "b2", "d")
        // From page 2 ("c"): after it only "e" is unread, then from the top nothing else ("a" and the story are read).
        assertEquals(listOf(4), upcomingUnread(pages, read, current = 2))
        // From the last page: wraps to the top, skipping read ones.
        assertEquals(listOf(2), upcomingUnread(pages, read, current = 4))
        assertEquals(listOf(1, 2), upcomingUnread(pages, setOf("a"), current = 0, limit = 2))
    }

    @Test
    fun theQueueKeepsTheOpenedPageAndTheUnreadOnes() {
        val read = setOf("a", "b2", "d")
        // Opened at "c": "a", the story and "d" were read, so the next swipe from "c" is "e".
        assertEquals(listOf(page("c"), page("e")), unreadQueue(pages, read, opened = 2))
        // Opened at a story already read: it stays, in its place among the unread.
        assertEquals(listOf(page("b1", "b2"), page("c"), page("e")), unreadQueue(pages, read, opened = 1))
    }

    @Test
    fun nextUnreadIsTheFirstUpcomingOrNone() {
        assertEquals(3, nextUnread(pages, setOf("a", "b1", "c"), current = 2))
        assertEquals(0, nextUnread(pages, setOf("b1", "c", "d", "e"), current = 4))
        assertNull(nextUnread(pages, setOf("a", "b1", "c", "d", "e"), current = 2))
        // The current page doesn't count, read or not.
        assertNull(nextUnread(listOf(page("a")), emptySet(), current = 0))
    }
}
