package news.app

import java.io.File
import kotlin.time.Instant

/**
 * When each generated story was last opened as a page, by its id on the server, in a file in the app's own storage:
 * one "id<TAB>epoch millis" a line, the latest line of an id winning. Past [limit] stories the oldest openings are
 * dropped. What's new on a card and a page is what came after it ([cardUpdate], [whatsNew]). Not thread-safe: the
 * view model calls it from one coroutine at a time.
 */
class StorySeen(private val file: File, private val limit: Int = 5_000) {

    private val seen = LinkedHashMap<String, Instant>()
    private var loaded = false
    private var lines = 0

    fun all(): Map<String, Instant> {
        load()
        return seen.toMap()
    }

    /** [id] opened [at]. */
    fun open(id: String, at: Instant) {
        load()
        seen.remove(id)
        seen[id] = at
        lines++
        if (lines > limit * 2) {
            while (seen.size > limit) seen.remove(seen.keys.first())
            write()
        } else {
            file.parentFile?.mkdirs()
            file.appendText("$id\t${at.toEpochMilliseconds()}\n")
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        file.forEachLine { line ->
            val tab = line.indexOf('\t')
            val millis = line.substring(tab + 1).toLongOrNull()
            if (tab > 0 && millis != null) {
                val id = line.substring(0, tab)
                seen.remove(id)
                seen[id] = Instant.fromEpochMilliseconds(millis)
                lines++
            }
        }
    }

    private fun write() {
        file.parentFile?.mkdirs()
        file.writeText(seen.entries.joinToString("") { "${it.key}\t${it.value.toEpochMilliseconds()}\n" })
        lines = seen.size
    }
}
