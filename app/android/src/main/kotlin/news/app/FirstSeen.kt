package news.app

import java.io.File

/**
 * When the feed first had each headline, by URL, in a file in the app's storage ("<url>\t<millis>" per line): the age a
 * headline without a publish time counts by for being new ([newUrlsOf]). Only the latest [LIMIT] at least are kept.
 * Not thread-safe: the view model calls it from one coroutine at a time.
 */
class FirstSeen(private val file: File) {

    private val times = LinkedHashMap<String, Long>()
    private var loaded = false

    /** Each of [urls] the first time it is seen gets [now]; returns every one's time. */
    fun note(urls: Collection<String>, now: Long = System.currentTimeMillis()): Map<String, Long> {
        load()
        val added = urls.filter { it !in times }
        added.forEach { times[it] = now }
        if (times.size > LIMIT * 2) {
            times.keys.take(times.size - LIMIT).forEach(times::remove)
            write()
        } else if (added.isNotEmpty()) {
            file.parentFile?.mkdirs()
            file.appendText(added.joinToString("") { "$it\t$now\n" })
        }
        return urls.associateWith { times.getValue(it) }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        file.readLines().forEach { line ->
            val tab = line.lastIndexOf('\t')
            if (tab > 0) line.substring(tab + 1).toLongOrNull()?.let { times[line.substring(0, tab)] = it }
        }
    }

    private fun write() {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(times.entries.joinToString("") { "${it.key}\t${it.value}\n" })
        if (!tmp.renameTo(file)) tmp.delete()
    }

    companion object {
        const val FILE = "first-seen.txt"
        const val LIMIT = 20_000
    }
}
