package news.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import news.app.server.ServerSource
import java.io.File

/** A news site the server reads, as the app shows it: from the server's `sources`, kept on the phone between starts. */
@Serializable
data class Site(val id: String, val name: String, val baseUrl: String = "", val language: String = "he")

internal fun ServerSource.toSite() = Site(id, name, baseUrl, language)

/** The sites last heard from the server, in a file, so the feed's site names show before the server answers. */
internal class SiteList(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<Site> = runCatching { json.decodeFromString(ListSerializer(Site.serializer()), file.readText()) }.getOrDefault(emptyList())

    fun save(sites: List<Site>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Site.serializer()), sites))
            tmp.renameTo(file)
        }
    }
}

/** A native stream for an article's video (the server's relay, or the archive's file), and whether it is an HLS playlist. */
data class ResolvedStream(val url: String, val isHls: Boolean)

/** The User-Agent pictures and videos are loaded with: a phone browser's, since some media servers refuse others. */
const val MEDIA_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
