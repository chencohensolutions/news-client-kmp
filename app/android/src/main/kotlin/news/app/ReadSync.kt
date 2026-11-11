package news.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import news.app.server.ReadMark
import news.app.server.ServerApi
import java.io.File
import kotlin.time.Instant

/**
 * The read marks, the same on all of the user's devices through the server: an article opened here is sent with
 * `markRead` ([queue], then [flush]), and the ones read on the other devices come with `readArticles` ([pull]), so the
 * unread badge follows the user.
 *
 * What wasn't sent yet waits in [outboxFile] (one URL per line), so a mark made without the server goes out with the
 * next refresh that reaches it. [sinceFile] keeps the newest read time heard, so each [pull] asks only for what is new.
 * Safe to call from several coroutines; blocking file work happens on the caller's dispatcher.
 */
class ReadSync(
    private val outboxFile: File,
    private val sinceFile: File,
    /** Sends a batch (at most [ServerApi.MAX_MARK_READ]); whether the server took it. */
    private val send: suspend (List<String>) -> Boolean,
    /** The marks since a time (all when null); null when the server doesn't answer. */
    private val fetch: suspend (Instant?) -> List<ReadMark>?,
) {
    private val lock = Mutex()
    private var outbox: LinkedHashSet<String>? = null

    /** Adds [urls] to what is sent with the next [flush]. */
    suspend fun queue(urls: Collection<String>) {
        if (urls.isEmpty()) return
        lock.withLock {
            val pending = load()
            val added = urls.filter(pending::add)
            if (added.isEmpty()) return
            outboxFile.parentFile?.mkdirs()
            outboxFile.appendText(added.joinToString("") { it + "\n" })
        }
    }

    /** How many marks wait to be sent. */
    suspend fun pending(): Int = lock.withLock { load().size }

    /**
     * Sends what waits, [ServerApi.MAX_MARK_READ] at a time, oldest first; each batch the server took leaves the
     * outbox. Stops at the first it didn't take (the rest wait for the next time). Whether all went.
     */
    suspend fun flush(): Boolean = lock.withLock {
        val pending = load()
        if (pending.isEmpty()) return true
        var all = true
        for (batch in pending.toList().chunked(ServerApi.MAX_MARK_READ)) {
            if (!send(batch)) {
                all = false
                break
            }
            pending.removeAll(batch.toSet())
        }
        save(pending)
        all
    }

    /**
     * The articles read on any of the user's devices since the last pull (all of them the first time); null when the
     * server didn't answer. The next pull asks from the newest read time in the answer.
     */
    suspend fun pull(): List<String>? = lock.withLock {
        val since = runCatching { Instant.parse(sinceFile.readText().trim()) }.getOrNull()
        val marks = fetch(since) ?: return null
        marks.maxOfOrNull { it.readAt }?.let { newest ->
            if (since == null || newest > since) {
                sinceFile.parentFile?.mkdirs()
                sinceFile.writeText(newest.toString())
            }
        }
        marks.map { it.url }.distinct()
    }

    /** Forgets what waits and the time of the last pull: another server, or none. */
    suspend fun reset() = lock.withLock {
        outbox = LinkedHashSet()
        outboxFile.delete()
        sinceFile.delete()
    }

    private fun load(): LinkedHashSet<String> {
        outbox?.let { return it }
        val read = LinkedHashSet<String>()
        if (outboxFile.exists()) outboxFile.readLines().filter { it.isNotBlank() }.forEach(read::add)
        outbox = read
        return read
    }

    private fun save(pending: Set<String>) {
        if (pending.isEmpty()) {
            outboxFile.delete()
            return
        }
        val tmp = File(outboxFile.path + ".tmp")
        tmp.writeText(pending.joinToString("") { it + "\n" })
        tmp.renameTo(outboxFile)
    }

    companion object {
        const val OUTBOX_FILE = "read-outbox.txt"
        const val SINCE_FILE = "read-since.txt"
    }
}
