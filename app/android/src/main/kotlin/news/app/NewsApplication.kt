package news.app

import news.app.server.AndroidDeviceKeys
import news.app.server.AndroidServerStore
import news.app.server.ApolloServerApi
import news.app.server.NewsServer
import news.app.server.serverHttpClient
import android.app.Application
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import news.core.model.Article
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * What the app and the background refresh share in one process: the news server, the repository with its cache, the
 * archive, and the image loader (with the browser User-Agent some media servers need).
 */
class NewsApplication : Application(), SingletonImageLoader.Factory {

    /** For work that must finish even when the screen that started it closes, such as archiving an article. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore by lazy { SettingsStore(this) }

    val feedCache by lazy { FeedCache(File(filesDir, "feed")) }

    /** The dates cached pictures and videos are trimmed by ([trimMedia]). */
    val mediaDates by lazy { MediaDates(File(filesDir, MediaDates.FILE)) }

    @Volatile
    var archive: ArticleArchive? = null
        private set

    val repository by lazy {
        // Articles reach the archive once a day from the cache ([DailyArchive]), not as they are fetched.
        NewsRepository(this, feedCache, server, onArticle = mediaDates::noteArticle).also { repo ->
            repo.fallback = { url -> archive?.takeIf { it.contains(url) }?.load(url) }
            repo.archived = { url -> archive?.loadCurrent(url) }
            repo.inArchive = { url -> archive?.contains(url) == true }
        }
    }

    /**
     * The news server (news-server-go), where the headlines, articles, stories and the rest come from ([NewsServer]).
     * Until the phone is enrolled, the app shows only the connect screen.
     */
    val server by lazy {
        NewsServer(
            store = AndroidServerStore(this),
            keys = AndroidDeviceKeys(),
            apiFor = { url, trust, identity -> ApolloServerApi(url, serverHttpClient(trust, identity)) },
            deviceName = deviceName(),
            scope = scope,
        ).also { it.keepWritingIn(File(filesDir, "server-writing.json")) }
    }

    /** The phone's name for the server's device list: its own name when set (Android 7.1+), else its model. */
    private fun deviceName(): String =
        runCatching { android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

    val articleFetcher by lazy { ArticleFetcher(this, repository) }

    override fun onCreate() {
        super.onCreate()
        val settings = settingsStore.load()
        repository.articleQuotaBytes = settings.articleCacheMb.toLong() shl 20
        useArchive(settings.archiveFolder?.let(Uri::parse), copyCached = false)
        DailyArchive.schedule(this, archive != null)
        // What the server can do now, and its certificate renewed in its last 30 days.
        server.start()
        scope.launch { runCatching { trimMedia() } }
    }

    /**
     * Archives to [folder] from now on (null: stop archiving), once a day ([DailyArchive]); with [copyCached] (a folder
     * just picked), a first run at once. The folder's permission is the caller's to keep.
     */
    fun useArchive(folder: Uri?, copyCached: Boolean = true) {
        val archive = folder?.let { ArticleArchive(this, it, ::cachedPicture) }?.takeIf { it.isAvailable }
        this.archive = archive
        if (archive != null) scope.launch { runCatching { archive.loadPictureFiles() } }
        if (copyCached) {
            DailyArchive.schedule(this, archive != null)
            if (archive != null) DailyArchive.runNow(this)
        }
    }

    /**
     * The day's archiving ([DailyArchive]): every cached article the archive doesn't have, or has in an older format,
     * with its pictures that are in the picture cache (and those of articles archived before whose pictures weren't
     * yet), then the videos played since ([archiveVideo]); the archive's index files are written once, at the end.
     * Reads the whole cache: off the main thread.
     */
    suspend fun archiveFromCache() = withContext(Dispatchers.IO) {
        val archive = archive ?: return@withContext
        try {
            feedCache.articles().forEach { article ->
                coroutineContext.ensureActive()
                runCatching { archive.save(article) }
            }
            val queued = videoQueue.all()
            videoQueue.clear()
            for (line in queued) {
                coroutineContext.ensureActive()
                val (url, stream, hls) = line.split('\t').takeIf { it.size == 3 } ?: continue
                val article = repository.savedArticle(url) ?: archive.load(url) ?: continue
                runCatching { exportVideo(archive, article, ResolvedStream(stream, hls.toBoolean())) }
            }
        } finally {
            archive.flush()
        }
    }

    /** The videos played since the last [archiveFromCache], to be archived by it: "<article url>\t<stream>\t<hls>". */
    private val videoQueue by lazy { ReadHistory(File(filesDir, "archive-videos.txt")) }

    /** [url]'s picture from the picture cache, or null when the app hasn't downloaded it. Blocking. */
    private fun cachedPicture(url: String): ByteArray? {
        val cache = SingletonImageLoader.get(this).diskCache ?: return null
        return cache.openSnapshot(url)?.use { snapshot -> runCatching { cache.fileSystem.read(snapshot.data) { readByteArray() } }.getOrNull() }
    }

    /**
     * The played-video cache (the analog of Coil's picture [DiskCache]): bytes a video plays go here, streamed or from
     * the archive, so a replay or the archive export reads them from disk. One cache for the process, kept within
     * [AppSettings.videoCacheMb] by [trimMedia] (the videos of the oldest articles first); its own least-recently-used
     * eviction only past a margin above that, in case the trim falls behind.
     */
    val videoCache: SimpleCache by lazy {
        val megabytes = settingsStore.load().videoCacheMb.coerceAtLeast(64).toLong()
        SimpleCache(File(cacheDir, "video_cache"), LeastRecentlyUsedCacheEvictor(withMargin(megabytes shl 20)), StandaloneDatabaseProvider(this))
    }

    /**
     * Reads media through [videoCache]: from the web with a browser's User-Agent (some CDNs refuse the default
     * one), or from the archive's files (content://).
     */
    fun videoDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(videoCache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(
                    this,
                    DefaultHttpDataSource.Factory().setUserAgent(MEDIA_USER_AGENT).setAllowCrossProtocolRedirects(true),
                ),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun videoMediaSourceFactory(): MediaSource.Factory = DefaultMediaSourceFactory(videoDataSourceFactory())

    /** [article]'s video as archived (its MP4 file), or null. Looks in the archive folder: call off the main thread. */
    fun archivedVideo(article: Article): String? = archive?.videoFile(article.url)?.toString()

    /**
     * Keeps the picture and video caches within their quotas ([AppSettings.imageCacheMb], [AppSettings.videoCacheMb]):
     * past one, the media of the oldest articles go first ([MediaDates]), however recently seen; those of an article
     * without a time, by when they were cached. Blocking; call off the main thread.
     */
    fun trimMedia() {
        val settings = settingsStore.load()
        SingletonImageLoader.get(this).diskCache?.let { cache ->
            val quota = imageCacheBytes(settings.imageCacheMb, imageCacheDir(this))
            if (cache.size > quota) {
                val gone = ArrayList<String>()
                for (url in mediaDates.picturesOldestFirst()) {
                    if (cache.size <= quota) break
                    cache.remove(url)
                    gone += url
                }
                mediaDates.removed(gone)
            }
        }
        val videoQuota = settings.videoCacheMb.coerceAtLeast(64).toLong() shl 20
        if (videoCache.cacheSpace > videoQuota) {
            for (key in videoCache.keys.sortedBy { mediaDates.videoDate(it) }) {
                if (videoCache.cacheSpace <= videoQuota) break
                videoCache.removeResource(key)
            }
        }
    }

    /**
     * Copies the archive's [file] of the picture at [url] into the picture cache, as a download would have, unless it
     * is there. Blocking.
     */
    private fun copyToPictureCache(url: String, file: Uri) {
        val cache = SingletonImageLoader.get(this).diskCache ?: return
        cache.openSnapshot(url)?.use { return }
        val editor = cache.openEditor(url) ?: return
        val copied = runCatching {
            contentResolver.openInputStream(file)?.use { input -> cache.fileSystem.write(editor.data) { input.copyTo(outputStream()) } } != null
        }.getOrDefault(false)
        if (copied) editor.commit() else editor.abort()
    }

    /**
     * [article]'s [stream] played: queued for the day's archiving ([archiveFromCache]), which copies it into the archive
     * as one highest-res MP4 ([exportVideo]), when a folder is set and it isn't there yet. For an HLS stream the highest
     * rendition is exported; a progressive file is muxed as is. Nothing is queued when not archiving.
     */
    fun archiveVideo(article: Article, stream: ResolvedStream) {
        val archive = archive ?: return
        scope.launch {
            runCatching { if (!archive.hasVideo(article.url)) videoQueue.add("${article.url}\t${stream.url}\t${stream.isHls}") }
        }
    }

    /** Copies [article]'s [stream] into [archive] as one highest-res MP4, unless it is there ([archiveVideo]). */
    private suspend fun exportVideo(archive: ArticleArchive, article: Article, stream: ResolvedStream) {
        if (archive.hasVideo(article.url)) return
        val source = if (stream.isHls) {
            val master = runCatching { java.net.URL(stream.url).openConnection().apply { setRequestProperty("User-Agent", MEDIA_USER_AGENT) }.getInputStream().use { it.readBytes().decodeToString() } }.getOrNull()
            master?.let { highestHlsVariant(it, stream.url) } ?: stream.url
        } else {
            stream.url
        }
        val temp = File(cacheDir, "archive-video-${FeedCache.fileName(article.url).removeSuffix(".json")}.mp4")
        try {
            if (exportMp4(this@NewsApplication, videoMediaSourceFactory(), source, temp)) archive.saveVideo(article, temp)
        } finally {
            temp.delete()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
            // A picture the archive has goes into the picture cache from there rather than from the site, on the way
            // to the screen.
            add(Interceptor { chain ->
                val url = chain.request.data as? String
                val file = url?.let { archive?.pictureFile(it) }
                if (url != null && file != null) withContext(Dispatchers.IO) { runCatching { copyToPictureCache(url, file) } }
                chain.proceed()
            })
        }
        .diskCache {
            val dir = imageCacheDir(context)
            // Kept within the quota by trimMedia, the pictures of the oldest articles first; Coil's own
            // least-recently-used eviction only past a margin above it, in case the trim falls behind.
            DiskCache.Builder().directory(dir.toOkioPath()).maxSizeBytes(withMargin(imageCacheBytes(settingsStore.load().imageCacheMb, dir))).build()
        }
        // Every picture the app downloads gets its date for the trim; it reaches the archive with the day's archiving,
        // from this cache ([archiveFromCache]).
        .eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                val url = request.data as? String ?: return
                scope.launch { runCatching { mediaDates.cached(url) } }
            }
        })
        .build()

    companion object {
        fun imageCacheDir(context: PlatformContext): File = File(context.cacheDir, "image_cache")

        /**
         * The picture cache's room ([AppSettings.imageCacheMb], 1 GB by default: the pictures of the whole cached feed;
         * Coil's own default, a percentage capped at 250 MB, filled with the background downloads, so pictures scrolled
         * past were evicted and downloaded again). Past it the oldest go. Unlimited (0) is what the phone can spare
         * leaving [KEEP_FREE] free, counting what the cache holds now. Read when the image loader starts.
         */
        internal fun imageCacheBytes(megabytes: Int, dir: File): Long {
            if (megabytes > 0) return megabytes.toLong() shl 20
            val held = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            val parent = dir.parentFile ?: dir
            return (parent.usableSpace + held - KEEP_FREE).coerceAtLeast(MIN_UNLIMITED)
        }

        /** A cache's own eviction limit: a quarter above the quota [trimMedia] keeps it within. */
        internal fun withMargin(quota: Long): Long = quota + quota / 4

        /** What an unlimited picture cache leaves free on the phone. */
        private const val KEEP_FREE = 1L shl 30

        /** An unlimited cache on a phone with less to spare still gets this much. */
        private const val MIN_UNLIMITED = 256L shl 20

        /** Some media servers answer 403 to OkHttp's default User-Agent, so images use a browser's. */
        private val imageHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", MEDIA_USER_AGENT).build())
                }
                .build()
        }
    }
}
