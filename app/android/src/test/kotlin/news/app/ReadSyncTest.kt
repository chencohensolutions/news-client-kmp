package news.app

import kotlinx.coroutines.test.runTest
import news.app.server.ReadMark
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReadSyncTest {
    private val dir: File = Files.createTempDirectory("read-sync").toFile()
    private val sent = mutableListOf<List<String>>()
    private var takes = { _: List<String> -> true }
    private val asked = mutableListOf<Instant?>()
    private var marks: List<ReadMark>? = emptyList()

    private fun sync() = ReadSync(
        File(dir, ReadSync.OUTBOX_FILE),
        File(dir, ReadSync.SINCE_FILE),
        send = { urls -> sent += urls; takes(urls) },
        fetch = { since -> asked += since; marks },
    )

    @Test
    fun marksGoInBatchesAndLeaveTheOutbox() = runTest {
        val sync = sync()
        sync.queue((1..1100).map { "https://a.example/$it" })
        assertTrue(sync.flush())
        assertEquals(listOf(500, 500, 100), sent.map { it.size })
        assertEquals(0, sync.pending())
        assertFalse(File(dir, ReadSync.OUTBOX_FILE).exists())
    }

    @Test
    fun whatTheServerDidntTakeWaitsEvenAcrossRuns() = runTest {
        takes = { false }
        val sync = sync()
        sync.queue(listOf("u1", "u2"))
        sync.queue(listOf("u2"))
        assertFalse(sync.flush())
        // A new run reads the outbox from its file.
        takes = { true }
        sent.clear()
        val again = sync()
        assertEquals(2, again.pending())
        assertTrue(again.flush())
        assertEquals(listOf(listOf("u1", "u2")), sent)
    }

    @Test
    fun aFailedBatchKeepsItAndTheOnesAfter() = runTest {
        var n = 0
        takes = { n++ == 0 }
        val sync = sync()
        sync.queue((1..1200).map { "u$it" })
        assertFalse(sync.flush())
        assertEquals(700, sync.pending())
    }

    @Test
    fun pullAsksFromTheNewestReadHeard() = runTest {
        val sync = sync()
        marks = listOf(ReadMark("u1", Instant.parse("2026-11-10T08:00:00Z")), ReadMark("u2", Instant.parse("2026-11-10T09:00:00Z")))
        assertEquals(listOf("u1", "u2"), sync.pull())
        marks = emptyList()
        assertEquals(emptyList(), sync.pull())
        assertEquals(listOf(null, Instant.parse("2026-11-10T09:00:00Z")), asked)
        // An older answer doesn't move it back.
        marks = listOf(ReadMark("u0", Instant.parse("2026-11-01T00:00:00Z")))
        sync.pull()
        sync.pull()
        assertEquals(Instant.parse("2026-11-10T09:00:00Z"), asked.last())
    }

    @Test
    fun noAnswerIsNull() = runTest {
        marks = null
        assertNull(sync().pull())
        assertFalse(File(dir, ReadSync.SINCE_FILE).exists())
    }

    @Test
    fun resetForgetsBoth() = runTest {
        val sync = sync()
        takes = { false }
        sync.queue(listOf("u1"))
        marks = listOf(ReadMark("u1", Instant.parse("2026-11-10T08:00:00Z")))
        sync.pull()
        sync.reset()
        assertEquals(0, sync.pending())
        sync.pull()
        assertNull(asked.last())
    }
}
