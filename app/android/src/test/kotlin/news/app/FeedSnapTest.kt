package news.app

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedSnapTest {

    /** Rows of [sizes] one under another from row [first], [scrolled] pixels of it up past the list's top, in a list 1000 high. */
    private fun layout(vararg sizes: Int, first: Int = 0, scrolled: Int = 0): LazyListLayoutInfo {
        var top = -scrolled
        val rows = sizes.mapIndexed { i, size ->
            object : LazyListItemInfo {
                override val index = first + i
                override val key: Any = first + i
                override val offset = top
                override val size = size
            }.also { top += size }
        }
        return object : LazyListLayoutInfo {
            override val visibleItemsInfo: List<LazyListItemInfo> = rows
            override val viewportStartOffset = 0
            override val viewportEndOffset = 1000
            override val totalItemsCount = first + rows.size
            override val viewportSize = IntSize(500, 1000)
            override val orientation = Orientation.Vertical
            override val reverseLayout = false
            override val beforeContentPadding = 0
            override val afterContentPadding = 0
            override val mainAxisItemSpacing = 0
        }
    }

    private fun top(index: Int) = FlingEnd.RowTop(index)

    @Test
    fun aFlingStopsAtTheNextRowHoweverFast() {
        // Row 3 at the top: a fling down, short or long, ends at row 4, not past it.
        assertEquals(top(4), flingEnd(layout(300, 300, 300, 300, first = 3), 3000f, 200f))
        assertEquals(top(4), flingEnd(layout(300, 300, 300, 300, first = 3), 20000f, 5000f))
        // Up: to the row above.
        assertEquals(top(2), flingEnd(layout(300, 300, 300, 300, first = 3), -20000f, -5000f))
    }

    @Test
    fun aFlingUpFromInsideARowEndsAtItsTop() {
        assertEquals(top(3), flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 100), -3000f, -900f))
    }

    @Test
    fun theRowLastStoppedAtLetsTheNextFlingRunFree() {
        // Row 3 was stopped at: a fling up from inside it, or a finger let go near its top, no longer comes back to it.
        assertEquals(FlingEnd.Natural, flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 100), -3000f, -900f, snapped = 3))
        assertEquals(FlingEnd.Natural, flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 100), 0f, 0f, snapped = 3))
        // Another row's top still stops it.
        assertEquals(top(4), flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 100), 3000f, 900f, snapped = 3))
    }

    @Test
    fun aFingerLetGoSettlesOnTheNearerRow() {
        assertEquals(top(3), flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 100), 0f, 0f))
        assertEquals(top(4), flingEnd(layout(300, 300, 300, 300, first = 3, scrolled = 200), 0f, 0f))
    }

    @Test
    fun aFlingInsideATallRowEndsWhereItWould() {
        // A row 3000 high: its middle can be read, so a fling that ends there isn't moved.
        assertEquals(FlingEnd.Natural, flingEnd(layout(3000, 300, scrolled = 500), 3000f, 700f))
        // Near its bottom it goes on to the next row; near its top, back to its own.
        assertEquals(top(1), flingEnd(layout(3000, 300, scrolled = 1500), 3000f, 1300f))
        assertEquals(top(0), flingEnd(layout(3000, 300, scrolled = 500), -3000f, -300f))
    }

    @Test
    fun aRowsTopIsMeasuredFromItOrTheRowAbove() {
        val info = layout(300, 300, first = 3, scrolled = 100)
        assertEquals(200f, distanceTo(info, 4))
        assertEquals(500f, distanceTo(info, 5))
        assertEquals(null, distanceTo(info, 2))
    }
}
