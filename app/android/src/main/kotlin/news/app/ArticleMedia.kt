package news.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The picture above an article's text: [mainImage], unless the same photo is also among the photos in the text
 * ([blocks]), where it shows with its caption, so it isn't shown twice. Sites serve one photo at several sizes and
 * formats, so two URLs are the same photo when their file names match ([photoKey]).
 */
fun leadImage(mainImage: String?, blocks: List<ArticleBlock>): String? =
    mainImage.takeIf { mainPhotoInText(mainImage, blocks) == null }

/** Which of [blocks] is [mainImage]'s photo, when it is among the photos in the text; null when it isn't. */
fun mainPhotoInText(mainImage: String?, blocks: List<ArticleBlock>): Int? {
    if (mainImage == null) return null
    val key = photoKey(mainImage)
    return blocks.indexOfFirst { it is ArticleBlock.Image && (it.url == mainImage || key != null && photoKey(it.url) == key) }
        .takeIf { it >= 0 }
}

/**
 * A photo's file name without query, size suffix and extension: "Hormuz-1024x768.jpg?w=800" and "hormuz.webp" are
 * both "hormuz". Null when the name is too short to tell photos apart ("1.jpg"); those match only by the whole URL.
 */
internal fun photoKey(url: String): String? =
    url.substringBefore('#').substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
        .replace(SIZE_SUFFIX, "").lowercase().takeIf { it.length >= 6 }

private val SIZE_SUFFIX = Regex("-\\d+x\\d+$")

/**
 * An in-article photo across the full width, at its own proportions, with its caption under it at [captionInset].
 * [standIn]: a picture in memory to show until the photo's file is in, at that picture's proportions until the photo's
 * are known (the story picture that moves in, [sharedPicture], so it has a frame to land in); [pictureModifier] goes on
 * the picture.
 */
@Composable
fun ArticlePhoto(image: ArticleBlock.Image, captionInset: Dp, standIn: String? = null, pictureModifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth()) {
        if (standIn == null) {
            AsyncImage(
                model = image.url,
                contentDescription = image.caption,
                contentScale = ContentScale.FillWidth,
                modifier = pictureModifier.fillMaxWidth(),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(image.url).placeholderMemoryCacheKey(standIn).build(),
                contentDescription = image.caption,
                contentScale = ContentScale.Crop,
                onSuccess = { pictureRatios.record(image.url, it.painter.intrinsicSize) },
                modifier = pictureModifier.fillMaxWidth().aspectRatio(pictureRatios[image.url] ?: pictureRatios[standIn] ?: (16f / 9f)),
            )
        }
        image.caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = captionInset, end = captionInset, top = 4.dp),
            )
        }
    }
}

/**
 * The article's video across the full width: its poster with a play button, then, once tapped, the video playing in
 * place. A native stream plays in Media3's player, whose full-screen button moves the same playback to full screen;
 * the stream is [ArticleVideo.stream] as the server sent it ([streamFor]). A video that has only the site's own player
 * ([ArticleVideo.embed], no stream) runs that player in a web view, whose own full-screen button is honoured. It stops
 * once it is no longer [active].
 *
 * While it plays natively, playback fills the app's video cache; and, when an archive folder is set, the video is also
 * copied there as a single highest-res MP4, just like the article's pictures ([NewsApplication.archiveVideo]).
 *
 * [stickTo]: where the article's visible area starts, in the window. While the video plays and the article scrolls it
 * up past there, it stays there, over the text, so the rest can be read beside it ([AppSettings.stickyVideo]).
 */
@Composable
fun VideoCard(video: ArticleVideo, article: Article, fallbackPoster: String?, active: Boolean = true, stickTo: (() -> Float)? = null) {
    val app = LocalContext.current.applicationContext as NewsApplication
    val sticky = stickTo != null && remember { app.settingsStore.stickyVideo }
    // Where the card itself is in the window: its place in the article; the video moves down from it to stick.
    var cardTop by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    // Told to the reader while it plays on the page on screen: the status bar is solid then ([LocalVideoPlaying]).
    val playingNow = LocalVideoPlaying.current
    LaunchedEffect(playing, active) { if (active) playingNow?.value = playing }
    DisposableEffect(playingNow) { onDispose { if (active) playingNow?.value = false } }
    var resolving by remember(video) { mutableStateOf(false) }
    // The stream to play: the one the server sent; without one, the video plays in its embedded player.
    var resolved by remember(video) { mutableStateOf(streamFor(video)) }
    var unresolved by remember(video) { mutableStateOf(false) }
    // An archived video plays from its MP4 there (through the video cache), not from the site: looked for on first play.
    var archiveChecked by remember(video) { mutableStateOf(app.archive == null) }
    // Swiped away in the reader: the page stays composed next door, so the player is let go.
    LaunchedEffect(active) { if (!active) playing = false }

    // On first play, an archived copy plays in place of the stream.
    LaunchedEffect(playing) {
        if (playing && !archiveChecked) {
            withContext(Dispatchers.IO) { runCatching { app.archivedVideo(article) }.getOrNull() }?.let { resolved = ResolvedStream(it, isHls = false) }
            archiveChecked = true
        }
        if (playing && resolved == null && !unresolved && video.embed != null) unresolved = true
    }
    // A native stream that plays goes to the app cache (the player below) and to the archive when one is set.
    val stream = resolved?.takeIf { archiveChecked }
    LaunchedEffect(playing, stream) {
        if (playing && stream != null) {
            // The video cache drops the videos of the oldest articles first ([NewsApplication.trimMedia]).
            app.scope.launch { runCatching { app.mediaDates.noteVideo(stream.url, article.publishedAt?.toEpochMilliseconds()) } }
            app.archiveVideo(article, stream)
        }
    }

    val embed = video.embed
    // The card keeps its place in the article; the video inside it is drawn lower by as much as the card went up past
    // the top, and over what follows (zIndex), while it plays.
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).zIndex(1f).onGloballyPositioned { cardTop = it.positionInRoot().y }) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationY = if (sticky && playing) (stickTo!!() - cardTop).coerceAtLeast(0f) else 0f }
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                playing && stream != null -> InlineStreamPlayer(stream.url, app.videoDataSourceFactory())
                playing && (resolving || !archiveChecked) -> CircularProgressIndicator(color = Color.White)
                playing && embed != null -> InlineSitePlayer(embed)
                else -> Poster(video, fallbackPoster, onPlay = { playing = true })
            }
        }
    }
}

@Composable
private fun Poster(video: ArticleVideo, fallbackPoster: String?, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
        (video.poster ?: fallbackPoster)?.let {
            // A poster that fails to load (sites' metadata sometimes points nowhere) shows the article's picture.
            val fallback = fallbackPoster?.let { url -> rememberAsyncImagePainter(url) }
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, error = fallback, modifier = Modifier.fillMaxSize())
        }
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("▶", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
        }
        formatDuration(video.duration)?.let {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
    }
}

/**
 * A file or HLS playlist, played in place at the highest resolution it offers, through the app's video cache
 * ([dataSourceFactory], upstream a browser's User-Agent) so a replay is served from disk. The player's
 * full-screen button shows the same player in a full-screen view, so playback carries on where it was.
 */
@OptIn(UnstableApi::class)
@Composable
private fun InlineStreamPlayer(url: String, dataSourceFactory: androidx.media3.datasource.DataSource.Factory) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build().apply {
            // HLS has several renditions; take the top one.
            trackSelectionParameters = trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    // Whether the video is wider than it is tall (its pixels may be non-square): a landscape video turns to landscape
    // in full screen; a portrait one (a vertical clip) stays upright.
    var videoWide by remember(url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoWide = size.height > 0 && size.width * size.pixelWidthHeightRatio > size.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    var fullScreen by remember { mutableStateOf(false) }

    // Full screen on a wide video turns the phone to landscape, and back to how it was on leaving.
    val activity = context.findActivity()
    DisposableEffect(fullScreen && videoWide) {
        if (fullScreen && videoWide && activity != null) {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { activity.requestedOrientation = previous }
        } else {
            onDispose {}
        }
    }

    if (!fullScreen) {
        AndroidView(
            // A TextureView (layout/inline_player): blurred under the status bar like the rest of the page, no black flash.
            factory = {
                (LayoutInflater.from(it).inflate(R.layout.inline_player, null) as PlayerView)
                    .apply { setFullscreenButtonClickListener { enter -> if (enter) fullScreen = true } }
            },
            update = { it.player = player; it.setFullscreenButtonState(false) },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Immersive()
            AndroidView(
                // Setting the button's state reports it through this listener too, so only a request to leave counts.
                factory = { PlayerView(it).apply { setFullscreenButtonClickListener { stay -> if (!stay) fullScreen = false } } },
                update = { it.player = player; it.setFullscreenButtonState(true) },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/**
 * The site's own player page in place, which handles its own access; its cookies persist, so a sign-in is kept.
 * When the player asks for full screen, its view is shown in a full-screen dialog until it leaves full screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineSitePlayer(url: String) {
    var fullScreenView by remember { mutableStateOf<View?>(null) }
    var exitFullScreen by remember { mutableStateOf<(() -> Unit)?>(null) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        fullScreenView = view
                        exitFullScreen = { callback.onCustomViewHidden() }
                    }

                    override fun onHideCustomView() {
                        fullScreenView = null
                        exitFullScreen = null
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
    fullScreenView?.let { view ->
        Dialog(
            onDismissRequest = { exitFullScreen?.invoke() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Immersive()
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        (view.parent as? ViewGroup)?.removeView(view)
                        addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

/**
 * Inside a full-screen [Dialog] (a video in full screen): the dialog's window covers the whole screen, under the
 * camera cutout too, with the status bar and the navigation (gesture) bar hidden, as a video player's full screen is;
 * a swipe in from an edge shows them for a moment. They come back with the app's window when the dialog closes.
 */
@Composable
private fun Immersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@DisposableEffect onDispose {}
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {}
    }
}

/** Set by an article's video while it plays on the page on screen ([VideoCard]); the reader provides it. */
val LocalVideoPlaying = compositionLocalOf<MutableState<Boolean>?> { null }

/** The [Activity] behind a composable's context (unwrapping the theme/base wrappers), or null. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "PT0H1M45S" → "1:45"; null when it isn't an ISO-8601 duration. */
internal fun formatDuration(iso: String?): String? {
    val m = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)(?:\\.\\d+)?S)?$").find(iso ?: return null) ?: return null
    val (h, min, s) = m.destructured
    val hours = h.toIntOrNull() ?: 0
    val minutes = min.toIntOrNull() ?: 0
    val seconds = s.toIntOrNull() ?: 0
    if (hours == 0 && minutes == 0 && seconds == 0) return null
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
