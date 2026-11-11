package news.app

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.pow

/**
 * The feed's fling ([AppSettings.snapToRows]): it goes on to the next row in its direction, none skipped, and slows
 * into it, from the finger's speed to a stop with that row's top at the list's top (under the status bar and the
 * progress lines, once the header is gone). A row taller than the screen is let stop anywhere in its middle, so its
 * lower part can be read.
 */
@Composable
internal fun rememberRowSnapFling(listState: LazyListState): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(listState, decay) { RowSnapFling(listState, decay) }
}

private class RowSnapFling(private val listState: LazyListState, private val decay: DecayAnimationSpec<Float>) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val info = listState.layoutInfo
        val end = flingEnd(info, initialVelocity, decay.calculateTargetValue(0f, initialVelocity))
        if (end !is FlingEnd.RowTop) return glide(initialVelocity)
        // Its distance now; a row above the screen isn't laid out yet: about a row's height up until it shows.
        val rows = info.visibleItemsInfo
        val measured = distanceTo(info, end.index)
        var remaining = measured ?: -(rows.sumOf { it.size } / rows.size.toFloat())
        // The curve 1 - (1 - t)^EASE starts at EASE times its average speed: as long as makes that the finger's.
        val seconds = (EASE * abs(remaining) / abs(initialVelocity).coerceAtLeast(1f)).coerceIn(MIN_SECONDS, MAX_SECONDS)
        val start = withFrameNanos { it }
        var left = 1f
        while (left > 0f) {
            val t = ((withFrameNanos { it } - start) / 1e9f / seconds).coerceAtMost(1f)
            val now = (1 - t).pow(EASE)
            // Measured each frame: the header taking some of the scroll, or a row's size coming in, changes the distance,
            // not the curve. Each frame takes the curve's part of what is left.
            remaining = distanceTo(listState.layoutInfo, end.index) ?: remaining
            // The row above turned out tall, its top more than a screen away: the fling goes on as a plain one.
            if (measured == null && abs(remaining) > info.viewportSize.height) return glide(EASE * remaining / seconds * left.pow((EASE - 1) / EASE))
            val step = remaining * (1 - now / left)
            val consumed = scrollBy(step)
            if (abs(step) > 1f && abs(consumed) < abs(step) / 2) break // the list's end
            remaining -= consumed
            left = now
        }
        return 0f
    }

    /** A plain fling's decay from [velocity]. */
    private suspend fun ScrollScope.glide(velocity: Float): Float {
        if (abs(velocity) < 1f) return 0f
        var last = 0f
        var left = velocity
        AnimationState(0f, velocity).animateDecay(decay) {
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            left = this.velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return left
    }
}

/** Where a fling ends: as it would, or at a row's top. */
internal sealed interface FlingEnd {
    data object Natural : FlingEnd
    data class RowTop(val index: Int) : FlingEnd
}

/**
 * Where a fling of [velocity] (forward, down the list, above 0) ends, by [info]'s rows, [natural] the distance it would
 * go by itself: at the top of the next row its way, or, slow (a finger let go), of the nearer one. Inside a row taller
 * than the list ([TALL_ROW_EDGE] of the list's height from either end), where it would.
 */
internal fun flingEnd(info: LazyListLayoutInfo, velocity: Float, natural: Float): FlingEnd {
    val rows = info.visibleItemsInfo
    if (rows.isEmpty()) return FlingEnd.Natural
    // A row's offset is from the list's top below its padding: scrolling by it puts the row's top there.
    val height = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
    val row = rows.lastOrNull { it.offset <= EXACT } ?: rows.first()
    val top = row.offset.toFloat()
    val bottom = top + row.size
    val next = FlingEnd.RowTop(row.index + 1)
    if (row.size > height) {
        val edge = height * TALL_ROW_EDGE
        when {
            natural >= bottom - edge -> return next
            natural > top + edge -> return FlingEnd.Natural
            natural >= top -> return FlingEnd.RowTop(row.index)
        }
    }
    return when {
        abs(velocity) < SLOW -> if (-top < bottom) FlingEnd.RowTop(row.index) else next
        velocity > 0 -> next
        top < -EXACT || row.index == 0 -> FlingEnd.RowTop(row.index)
        else -> FlingEnd.RowTop(row.index - 1)
    }
}

/** How far row [index]'s top is from the list's top, while it, or the row above it, is laid out. */
internal fun distanceTo(info: LazyListLayoutInfo, index: Int): Float? {
    val rows = info.visibleItemsInfo
    rows.firstOrNull { it.index == index }?.let { return it.offset.toFloat() }
    return rows.firstOrNull { it.index == index - 1 }?.let { (it.offset + it.size).toFloat() }
}

/** How near a tall row's ends, as a part of the list's height, a fling still ends at a row's top. */
private const val TALL_ROW_EDGE = 0.4f

/** Below this speed, in pixels a second, a fling is a finger let go: it settles on the nearer row. */
private const val SLOW = 400f

/** A row this many pixels from the list's top is at it. */
private const val EXACT = 2

/** The power of the slowing curve, and how short and long it may take, in seconds. */
private const val EASE = 2.5f
private const val MIN_SECONDS = 0.18f
private const val MAX_SECONDS = 0.6f
