package news.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Opening an article from a story: its picture moves from the list to its place in the article while the rest of the
 * article fades in ([NewsApp]). These carry the transition's scopes down to the two pictures.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks the picture of the article at [articleUrl], so it moves between the story list and the reader. Keyed by the
 * article, not the picture: the reader may show the article page's picture, a different file from the list's.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPicture(articleUrl: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) { this@sharedPicture.sharedElement(rememberSharedContentState("picture:$articleUrl"), visibility) }
}
