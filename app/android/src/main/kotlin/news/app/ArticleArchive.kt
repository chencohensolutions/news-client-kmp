package news.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import news.core.model.Article
import news.core.model.ArticleBlock
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Every article the app fetches, kept for good in a folder the owner picks in shared storage (Storage Access
 * Framework), so it outlives the app: uninstalling removes the app's own storage but not this folder. After a
 * reinstall, picking the same folder again brings the archive back.
 *
 * Layout, by the article's publish date in Israel time (the day it was archived when it has none):
 *
 *     .nomedia                      keeps the pictures out of the gallery (covers every folder below)
 *     .index                        one line per save: "<id>\t<path of its json>\t<format>"; the last wins
 *     .pictures                     the pictures archived so far, one file name without extension per line
 *     .videos                       the article ids whose video is archived, one per line
 *     .picture-files                each archived picture's URL and its file's path, a tab between: a picture the
 *                                   archive has goes into the picture cache from here ([pictureFile])
 *     2026/2026-10/2026-10-30/<site>/
 *         <id>.json                 the Article
 *         <id>.jpg                  its main picture
 *         <id>-1.jpg, <id>-2.jpg …  the photos in its text, in reading order
 *         <id>.mp4                  its video, when streamed and played (one highest-res MP4)
 *
 * `<id>` is the hash of the article's URL ([FeedCache.fileName]). An article saved in an older [FORMAT] (lines without
 * one are format 1, before in-text photos) is saved again, over its old files, the next time the app fetches it.
 *
 * Every file operation in the folder goes through the document provider, and each write makes Android's media service
 * scan the file, `.nomedia` or not: thousands of them in a background refresh kept a CPU core busy for minutes. So the
 * dot files are kept in the app's own storage ([Log], plain files, no provider), and copied to the folder in one write
 * each by [flush] (after a background refresh, when the app goes to the background, at most every [FLUSH_MS] while
 * appending); the local copy is the truth, the folder's a full rewrite of it. A file is written once, under its final
 * name (overwritten in place when it exists), and found by building its address from its path rather than by listing
 * folders ([uriOf]), on the external storage provider whose document ids are paths; another provider is searched as
 * before.
 *
 * The archive downloads no pictures of its own. Saving an article notes where each of its pictures goes; a picture is
 * copied from the app's picture cache ([cachedPicture], by URL) when the app has downloaded it anyway: on screen in the
 * feed or the article, or ahead while scrolling or reading. Then ([pictureLoaded]), or at once when it is
 * already in the cache. Writes are blocking; call off the main thread.
 */
class ArticleArchive(
    private val context: Context,
    val folder: Uri,
    private val cachedPicture: (String) -> ByteArray? = { null },
    /** Where the dot files are kept in the app's storage: one folder per archive folder. */
    localRoot: File = File(context.filesDir, "archive"),
) {

    private val root: DocumentFile? = DocumentFile.fromTreeUri(context, folder)
    private val resolver get() = context.contentResolver

    private val localDir = File(localRoot, sha(folder.toString()))

    private val treeId: String? = runCatching { DocumentsContract.getTreeDocumentId(folder) }.getOrNull()

    /** The external storage provider's document ids are the files' paths, so a file's address can be built ([uriOf]). */
    private val pathBased = treeId != null && folder.authority == EXTERNAL_STORAGE

    private class Entry(val path: String, val format: Int)

    /** Where an article's picture goes: its day's folder and its file name without extension. */
    private class Picture(val path: List<String>, val name: String)

    /**
     * One of the dot files, [name], kept in the app's storage and copied to the folder by [flush]. Read from the local
     * copy; when there is none (a new install, another folder), from the folder's copy once, which is kept locally.
     */
    private inner class Log(val name: String) {
        private val local = File(localDir, name.removePrefix("."))

        @Volatile
        var dirty = false

        /** Its lines; null when neither copy exists. */
        fun read(): List<String>? {
            val text = if (local.exists()) {
                runCatching { local.readText() }.getOrNull()
            } else {
                readFile(name)?.also { text -> synchronized(this) { local.parentFile?.mkdirs(); local.writeText(text) } }
            }
            return text?.lineSequence()?.filter { it.isNotBlank() }?.toList()
        }

        fun append(text: String) {
            synchronized(this) {
                local.parentFile?.mkdirs()
                local.appendText(text)
                dirty = true
            }
            flushSoon()
        }

        fun replace(text: String) {
            synchronized(this) {
                local.parentFile?.mkdirs()
                local.writeText(text)
                dirty = true
            }
        }

        /** Copies it to the folder in one write, when changed since the last copy. */
        fun flush() {
            if (!dirty) return
            val bytes = synchronized(this) { dirty = false; runCatching { local.readBytes() }.getOrNull() } ?: return
            if (!writeFile("", name, bytes, count = false)) dirty = true
        }
    }

    private val indexLog = Log(INDEX)
    private val picturesLog = Log(PICTURES)
    private val videosLog = Log(VIDEOS)
    private val pictureFilesLog = Log(PICTURE_FILES)
    private val logs = listOf(indexLog, picturesLog, videosLog, pictureFilesLog)

    @Volatile
    private var lastFlush = System.currentTimeMillis()

    /** The total size of the files written to the folder, kept in the app's storage ([bytes]); absent until counted. */
    private val bytesFile = File(localDir, "bytes")

    /** Pictures of saved articles not archived yet, by URL, until the app downloads them. In memory: an article
     * fetched again (opened, or the next refresh) notes its pictures again. */
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, Picture>()

    /** The pictures already archived, by file name without extension, from `.pictures`. */
    private val archivedPictures: MutableSet<String> by lazy { java.util.Collections.synchronizedSet(picturesLog.read().orEmpty().toHashSet()) }

    /** The videos already archived, by article id, from `.videos`. */
    private val archivedVideos: MutableSet<String> by lazy { java.util.Collections.synchronizedSet(videosLog.read().orEmpty().toHashSet()) }

    /** Article id to its json's path and format, from `.index`, or from the folders when there is no index yet. */
    private val index: MutableMap<String, Entry> by lazy { java.util.Collections.synchronizedMap(readIndex() ?: rebuildIndex()) }

    /** The folders known to exist, so [ensureDirs] creates only new ones: those of [index]'s paths, and those made since. */
    private val knownDirs: MutableSet<String> by lazy {
        java.util.Collections.synchronizedSet(
            index.values.flatMapTo(HashSet()) { e -> e.path.split('/').dropLast(1).runningReduce { a, b -> "$a/$b" } },
        )
    }

    @Volatile
    private var noMediaChecked = false

    /** The archived pictures' files by URL ([pictureFile]): their paths in the folder (or, from before, document URIs). */
    private val pictureFiles = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var pictureFilesLoaded = false

    /** Reads `.picture-files`, so [pictureFile] answers. Blocking; call off the main thread, once. */
    fun loadPictureFiles() {
        pictureFilesLog.read().orEmpty().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) pictureFiles[line.substring(0, tab)] = line.substring(tab + 1)
        }
        pictureFilesLoaded = true
    }

    /**
     * The archive's file of the picture at [url], or null when it isn't archived (or `.picture-files` isn't read yet,
     * or the provider's addresses can't be built: this never reads the folder, so it can be asked on the main thread).
     */
    fun pictureFile(url: String): Uri? {
        if (!pictureFilesLoaded) return null
        val where = pictureFiles[url] ?: return null
        return if (where.startsWith("content:")) Uri.parse(where) else builtUri(where)
    }

    private fun notePictureFile(url: String, path: String) {
        if (pictureFiles.put(url, path) == null) pictureFilesLog.append("$url\t$path\n")
    }

    /** Whether the folder is still there and writable (it may have been deleted, or access revoked). */
    val isAvailable: Boolean get() = root?.let { it.exists() && it.canWrite() } == true

    val articleCount: Int get() = index.size

    fun contains(url: String): Boolean = id(url) in index

    /** Whether the article whose id ([FeedCache.fileName] without ".json") is [id] is archived in the current [FORMAT]. */
    fun hasId(id: String): Boolean = (index[id]?.format ?: 0) >= FORMAT

    /** [url]'s article when archived in the current [FORMAT] (an older one is fetched from the site again), else null. */
    fun loadCurrent(url: String): Article? = if ((index[id(url)]?.format ?: 0) >= FORMAT) load(url) else null

    /** [url]'s archived article; null when it isn't archived, or its file can't be read (a write cut short). */
    fun load(url: String): Article? {
        val path = index[id(url)]?.path ?: return null
        return readFile(path)?.let { text -> runCatching { json.decodeFromString(Article.serializer(), text) }.getOrNull() }
    }

    /**
     * Saves [article] in its day's folder, unless already archived in the current [FORMAT], and notes its main picture
     * and in-text photos to be archived once the app has them ([pictureLoaded]); those it has already are copied now.
     */
    @Synchronized
    fun save(article: Article) {
        val id = id(article.url)
        val path = existingPath(id) ?: run {
            ensureNoMedia()
            val day = (article.publishedAt?.toEpochMilliseconds()?.let(Instant::ofEpochMilli) ?: Instant.now()).atZone(ISRAEL)
            val path = listOf(day.format(YEAR), day.format(MONTH), day.format(DAY), article.sourceId)
            if (!ensureDirs(path)) return
            // The index line only once the file is whole: a write cut short leaves no line, and is written again.
            if (!writeFile(path.joinToString("/"), "$id.json", json.encodeToString(Article.serializer(), article).toByteArray())) return
            val jsonPath = (path + "$id.json").joinToString("/")
            index[id] = Entry(jsonPath, FORMAT)
            indexLog.append("$id\t$jsonPath\t$FORMAT\n")
            path
        }
        val pictures = picturesOf(article, id)
        // Each lookup of an article saves it here again: only pictures not archived and not noted already.
        pictures.filter { (url, name) -> name !in archivedPictures && !waiting.containsKey(url) }.forEach { (url, name) ->
            waiting[url] = Picture(path, name)
            pictureLoaded(url)
        }
    }

    /** [article]'s pictures' URLs and their file names without extension: its main picture, then its in-text photos. */
    private fun picturesOf(article: Article, id: String): List<Pair<String, String>> =
        listOfNotNull(article.imageUrl?.let { it to id }) +
            article.blocks.filterIsInstance<ArticleBlock.Image>().mapIndexed { i, photo -> photo.url to "$id-${i + 1}" }

    /** The folders of [id]'s json when it is archived in the current [FORMAT]; null when it is to be saved. */
    private fun existingPath(id: String): List<String>? =
        index[id]?.takeIf { it.format >= FORMAT }?.path?.split('/')?.dropLast(1)

    /**
     * The app has [url] in its picture cache: archives it when it is a picture of a saved article not archived yet.
     * Copies the cached file; never downloads.
     */
    fun pictureLoaded(url: String) {
        val picture = waiting[url] ?: return
        val bytes = cachedPicture(url) ?: return
        if (waiting.remove(url) == null || picture.name in archivedPictures) return
        val dir = picture.path.joinToString("/")
        val name = "${picture.name}.${extension(bytes)}"
        if (!writeFile(dir, name, bytes)) return
        notePictureFile(url, "$dir/$name")
        archivedPictures += picture.name
        picturesLog.append("${picture.name}\n")
    }

    /** Whether [url]'s video is already archived. */
    fun hasVideo(url: String): Boolean = id(url) in archivedVideos

    /** The archived MP4 of [url]'s video, or null. May look in its folder: call off the main thread. */
    fun videoFile(url: String): Uri? {
        val id = id(url)
        if (id !in archivedVideos) return null
        val dir = index[id]?.path?.substringBeforeLast('/') ?: return null
        return uriOf("$dir/$id.mp4")?.takeIf(::exists)
    }

    /**
     * Copies a ready MP4 of [article]'s video ([mp4], a temp file the caller made from the stream) into the article's
     * day folder as `<id>.mp4`, once. The article is saved first so the folder exists. Blocking; call off the main
     * thread. Returns whether the video is now archived.
     */
    @Synchronized
    fun saveVideo(article: Article, mp4: File): Boolean {
        val id = id(article.url)
        if (id in archivedVideos) return true
        save(article)
        val dir = (existingPath(id) ?: index[id]?.path?.split('/')?.dropLast(1))?.joinToString("/") ?: return false
        if (!writeFileStreaming(dir, "$id.mp4", mp4)) return false
        archivedVideos += id
        videosLog.append("$id\n")
        return true
    }

    /**
     * Copies the dot files that changed to the folder, one write each. After a background refresh, when the app goes
     * to the background, and from [Log.append] at most every [FLUSH_MS]. Blocking; call off the main thread.
     */
    fun flush() {
        lastFlush = System.currentTimeMillis()
        logs.forEach { runCatching { it.flush() } }
    }

    private fun flushSoon() {
        if (System.currentTimeMillis() - lastFlush >= FLUSH_MS) flush()
    }

    /**
     * Every archived article, read one at a time: one listing per folder, not a lookup per file. Reads them all
     * through the document provider, which takes minutes for thousands: call off the main thread. Notes the files of
     * their archived pictures as it goes ([pictureFile]), those archived before `.picture-files` too.
     */
    fun articles(): Sequence<Article> = sequence {
        fun folders(dir: DocumentFile) = dir.listFiles().filter { it.isDirectory }
        val root = root ?: return@sequence
        for (year in folders(root)) for (month in folders(year)) for (day in folders(month)) for (site in folders(day)) {
            val dir = listOf(year.name, month.name, day.name, site.name).joinToString("/")
            val files = site.listFiles()
            val others = files.mapNotNull { it.name }.filter { !it.endsWith(".json") }.associateBy { it.substringBeforeLast('.') }
            for (file in files) {
                val name = file.name ?: continue
                if (!name.endsWith(".json")) continue
                val article = runCatching {
                    resolver.openInputStream(file.uri)?.use { json.decodeFromString(Article.serializer(), it.readBytes().decodeToString()) }
                }.getOrNull() ?: continue
                picturesOf(article, name.removeSuffix(".json")).forEach { (url, picture) -> others[picture]?.let { notePictureFile(url, "$dir/$it") } }
                yield(article)
            }
        }
    }

    /**
     * Total size of the archive's files: kept as files are written; counted from the folders (every one of them, so
     * call off the main thread) only when it isn't known yet, or with [recount].
     */
    fun bytes(recount: Boolean = false): Long {
        if (!recount) storedBytes()?.let { return it }
        val total = root?.let(::sizeOf) ?: 0
        storeBytes(total)
        return total
    }

    private fun storedBytes(): Long? = runCatching { bytesFile.takeIf { it.exists() }?.readText()?.trim()?.toLong() }.getOrNull()

    @Synchronized
    private fun storeBytes(total: Long) {
        runCatching { bytesFile.parentFile?.mkdirs(); bytesFile.writeText(total.toString()) }
    }

    /** Adds [size] to the stored total, once there is one. */
    private fun addBytes(size: Long) {
        synchronized(this) { storedBytes()?.let { storeBytes(it + size) } }
    }

    private fun sizeOf(dir: DocumentFile): Long = dir.listFiles().sumOf { if (it.isDirectory) sizeOf(it) else it.length() }

    /** The picture's type, from its first bytes. */
    private fun extension(bytes: ByteArray): String {
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            starts(0x89, 0x50, 0x4E, 0x47) -> "png"
            starts(0x47, 0x49, 0x46) -> "gif"
            starts(0x52, 0x49, 0x46, 0x46) && bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> "jpg"
        }
    }

    private fun readIndex(): MutableMap<String, Entry>? = indexLog.read()?.mapNotNull { line ->
        line.split('\t').takeIf { it.size >= 2 }?.let { it[0] to Entry(it[1], it.getOrNull(2)?.toIntOrNull() ?: 1) }
    }?.toMap(HashMap())

    /** Walks the year, month, day and site folders for article files, and writes the index it finds. */
    private fun rebuildIndex(): MutableMap<String, Entry> {
        val found = HashMap<String, Entry>()
        fun folders(dir: DocumentFile) = dir.listFiles().filter { it.isDirectory }
        root?.let(::folders)?.forEach { year ->
            folders(year).forEach { month ->
                folders(month).forEach { day ->
                    folders(day).forEach { site ->
                        site.listFiles().mapNotNull { it.name }.filter { it.endsWith(".json") }.forEach { name ->
                            // The format isn't known from the files: 1, so the next fetch saves it again.
                            found[name.removeSuffix(".json")] = Entry(listOf(year.name, month.name, day.name, site.name, name).joinToString("/"), 1)
                        }
                    }
                }
            }
        }
        if (found.isNotEmpty()) {
            indexLog.replace(found.entries.joinToString("") { (k, v) -> "$k\t${v.path}\t${v.format}\n" })
            indexLog.flush()
        }
        return found
    }

    /**
     * [path]'s address in the folder: built from the path when the provider's ids are paths, with no call to it;
     * otherwise found by listing each folder on the way (null when missing). The folder itself for an empty path.
     */
    private fun uriOf(path: String): Uri? = when {
        path.isEmpty() -> root?.uri
        pathBased -> builtUri(path)
        else -> path.split('/').fold(root) { parent, name -> parent?.findFile(name) }?.uri
    }

    private fun builtUri(path: String): Uri? {
        val tree = treeId?.takeIf { pathBased } ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(folder, if (tree.endsWith(":")) tree + path else "$tree/$path")
    }

    /** Whether the document at [uri] exists: one query to the provider, no listing and no scan. */
    private fun exists(uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    /** [path]'s text, or null when it is missing or can't be read. */
    private fun readFile(path: String): String? {
        val uri = uriOf(path) ?: return null
        return runCatching { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
    }

    /** Makes the folders of [path] (year, month, day, site), each only when not known to exist. */
    private fun ensureDirs(path: List<String>): Boolean {
        var parent = ""
        for (name in path) {
            val dir = if (parent.isEmpty()) name else "$parent/$name"
            if (dir !in knownDirs) {
                val there = uriOf(dir)?.let(::exists) == true
                if (!there) {
                    val parentUri = uriOf(parent) ?: return false
                    runCatching { DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name) }.getOrNull()
                        ?: return false
                }
                knownDirs += dir
            }
            parent = dir
        }
        return true
    }

    private fun ensureNoMedia() {
        if (noMediaChecked) return
        noMediaChecked = true
        if (uriOf(NO_MEDIA)?.let(::exists) != true) uriOf("")?.let { createIn(it, NO_MEDIA) }
    }

    private fun createIn(dir: Uri, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(resolver, dir, OCTET_STREAM, name) }.getOrNull()

    /**
     * The address to write [name] in folder [dir] at: the file's own when it exists (written over in place), else a
     * new file under that name. Created as octet-stream so the provider keeps the name as given instead of adding an
     * extension; never a temporary name and a rename, each a provider call and a media scan.
     */
    private fun target(dir: String, name: String): Uri? {
        val path = if (dir.isEmpty()) name else "$dir/$name"
        uriOf(path)?.takeIf(::exists)?.let { return it }
        return createIn(uriOf(dir) ?: return null, name)
    }

    /**
     * Writes [bytes] as [name] in folder [dir] in one go ("wt": truncated first when it exists). A write cut short
     * leaves a partial file, which reads as missing and is written again: the index lines come only after. [count]:
     * whether it adds to the archive's size total ([bytes]).
     */
    private fun writeFile(dir: String, name: String, bytes: ByteArray, count: Boolean = true): Boolean {
        val uri = target(dir, name) ?: return false
        val ok = runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
        if (ok && count) addBytes(bytes.size.toLong())
        return ok
    }

    /** Like [writeFile] but streams [src] (a video, too big to hold in memory) into the file. */
    private fun writeFileStreaming(dir: String, name: String, src: File): Boolean {
        val uri = target(dir, name) ?: return false
        val ok = runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out -> src.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
        if (ok) addBytes(src.length())
        return ok
    }

    private fun id(url: String) = FeedCache.fileName(url).removeSuffix(".json")

    private companion object {
        const val NO_MEDIA = ".nomedia"
        const val INDEX = ".index"
        const val PICTURES = ".pictures"
        const val VIDEOS = ".videos"
        const val PICTURE_FILES = ".picture-files"
        const val OCTET_STREAM = "application/octet-stream"
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

        /** The longest the dot files wait in the app's storage while appended to, before [flush] copies them. */
        const val FLUSH_MS = 5 * 60_000L

        /** 1: text and main picture. 2: also the in-text photos ([Article.blocks]) and the video. */
        const val FORMAT = 2
        val ISRAEL: ZoneId = ZoneId.of("Asia/Jerusalem")
        val YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; prettyPrint = true }

        fun sha(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}
