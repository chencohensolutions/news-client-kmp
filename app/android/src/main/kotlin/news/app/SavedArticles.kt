package news.app

import java.io.File
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import news.core.model.ArticleSummary
import news.core.model.InstantIsoSerializer

/** An article the owner saved to come back to, and when. */
@Serializable
data class SavedArticle(
    val summary: ArticleSummary,
    @Serializable(with = InstantIsoSerializer::class)
    val savedAt: Instant,
)

/**
 * The saved articles, latest saved first, in a JSON file in the app's own storage. Each keeps its headline, so the
 * Saved screen lists them without the server; the article itself comes from the server, which keeps every article.
 * Not thread-safe: the view model calls it from one coroutine at a time.
 */
class SavedArticles(private val file: File) {

    private var saved: List<SavedArticle>? = null

    fun all(): List<SavedArticle> = saved ?: load().also { saved = it }

    /** Saves [summary] as the latest, or takes it out if it was saved; the list after. */
    fun toggle(summary: ArticleSummary, now: Instant): List<SavedArticle> {
        val before = all()
        val after = if (before.any { it.summary.url == summary.url }) before.filter { it.summary.url != summary.url }
        else listOf(SavedArticle(summary, now)) + before
        write(after)
        return after
    }

    fun remove(url: String): List<SavedArticle> {
        val after = all().filter { it.summary.url != url }
        write(after)
        return after
    }

    /** A saved article's headline replaced by a fuller one ([ArticleSummary.filledFrom]), keeping when it was saved. */
    fun update(summary: ArticleSummary): List<SavedArticle> {
        val before = all()
        if (before.none { it.summary.url == summary.url && it.summary != summary }) return before
        val after = before.map { if (it.summary.url == summary.url) it.copy(summary = summary) else it }
        write(after)
        return after
    }

    private fun load(): List<SavedArticle> =
        if (!file.exists()) emptyList()
        else runCatching { json.decodeFromString<List<SavedArticle>>(file.readText()) }.getOrDefault(emptyList())

    private fun write(list: List<SavedArticle>) {
        saved = list
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(list))
        // Replaces the file where renameTo wouldn't (Windows, in the unit tests).
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val FILE = "saved.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
