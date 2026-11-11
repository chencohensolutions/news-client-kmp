package news.app

import java.io.File

/**
 * The articles the user has opened, by URL, in a file in the app's own storage: one URL per line, oldest first.
 * The latest [limit] at least are kept: past twice that, the older ones are dropped. Not thread-safe: the view
 * model calls it from one coroutine at a time.
 */
class ReadHistory(private val file: File, private val limit: Int = LIMIT) {

    private val urls = LinkedHashSet<String>()
    private var loaded = false

    fun all(): Set<String> {
        load()
        return urls.toSet()
    }

    /** Adds [url] as the latest read; false if it was read already. */
    fun add(url: String): Boolean {
        load()
        if (!urls.add(url)) return false
        if (urls.size > limit * 2) {
            urls.take(urls.size - limit).forEach(urls::remove)
            write()
        } else {
            file.parentFile?.mkdirs()
            file.appendText(url + "\n")
        }
        return true
    }

    /** Adds [all] as read, in one write; those read already stay where they were. */
    fun addAll(all: Collection<String>) {
        load()
        val added = all.filter(urls::add)
        if (added.isEmpty()) return
        if (urls.size > limit * 2) {
            urls.take(urls.size - limit).forEach(urls::remove)
            write()
        } else {
            file.parentFile?.mkdirs()
            file.appendText(added.joinToString("") { it + "\n" })
        }
    }

    /** Reads the file again: another writer (a notification's "mark as read") may have added to it. */
    fun reload(): Set<String> {
        loaded = false
        urls.clear()
        return all()
    }

    /** Whether the file is there: anything was ever added. */
    val exists: Boolean get() = file.exists()

    fun clear() {
        urls.clear()
        loaded = true
        file.delete()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (file.exists()) file.readLines().filter { it.isNotBlank() }.forEach(urls::add)
    }

    private fun write() {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(urls.joinToString("") { it + "\n" })
        tmp.renameTo(file)
    }

    companion object {
        const val LIMIT = 5000

        /** The history's file in the app's storage. */
        const val FILE = "read.txt"
    }
}
