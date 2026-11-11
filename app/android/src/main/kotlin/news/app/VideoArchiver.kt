package news.app

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMuxer
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Writes the video at [sourceUrl] (a progressive file, or a single HLS rendition — pick the highest one first) to
 * [outFile] as one self-contained MP4, with Media3's [Transformer]: it remuxes when the codecs already fit MP4
 * (fast, no quality loss) and transcodes only when they don't. Reads through [mediaSourceFactory] (the app's video
 * cache, upstream the browser User-Agent), so bytes already played are reused. Returns whether it succeeded.
 *
 * Transformer posts its callbacks on the thread it is built on, so it runs on the main dispatcher.
 *
 * Written by Media3's own muxer, not the platform's (MediaMuxer): on some streams the platform's aborts the whole
 * process from native code (Samsung, Android 17: "FORTIFY: write: count … > SSIZE_MAX" in MPEG4Writer), which no
 * catch can stop; Media3's fails as an [ExportException], so the export just doesn't happen.
 */
suspend fun exportMp4(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
    sourceUrl: String,
    outFile: File,
): Boolean = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val assetLoader = ExoPlayerAssetLoader.Factory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            mediaSourceFactory,
        )
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoader)
            .setMuxerFactory(InAppMuxer.Factory.Builder().build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            .build()
        cont.invokeOnCancellation { runCatching { transformer.cancel() } }
        runCatching {
            transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(sourceUrl)).build(), outFile.absolutePath)
        }.onFailure { if (cont.isActive) cont.resume(false) }
    }
}

/**
 * The highest-resolution variant URL of an HLS master playlist [master] (its `#EXT-X-STREAM-INF` entries), resolved
 * to absolute against [masterUrl]. Picks the largest `RESOLUTION` area, then the largest `BANDWIDTH`. Null when the
 * playlist has no variants (it is itself a media playlist, e.g. a progressive-style single rendition).
 */
internal fun highestHlsVariant(master: String, masterUrl: String): String? {
    val lines = master.lines()
    var best: Triple<Long, Long, String>? = null // area, bandwidth, uri
    for (i in lines.indices) {
        val line = lines[i].trim()
        if (!line.startsWith("#EXT-X-STREAM-INF")) continue
        val uri = lines.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("#") } ?: continue
        val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
        val area = resolution?.let { it.groupValues[1].toLong() * it.groupValues[2].toLong() } ?: 0L
        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val current = best
        if (current == null || area > current.first || (area == current.first && bandwidth > current.second)) {
            best = Triple(area, bandwidth, uri)
        }
    }
    val uri = best?.third ?: return null
    return runCatching { java.net.URI(masterUrl).resolve(uri).toString() }.getOrDefault(uri)
}
