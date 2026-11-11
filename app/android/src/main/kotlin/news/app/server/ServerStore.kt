package news.app.server

import android.content.Context
import kotlinx.serialization.Serializable
import news.app.server.discovery.ServerAddresses
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import news.core.model.InstantIsoSerializer
import java.io.File
import kotlin.time.Instant

/** The phone's enrollment with a news server: where it is, its pinned key, the certificate's details, the owner's switch. */
@Serializable
data class ServerAccount(
    /** The server's base URL ([serverUrl]). */
    val url: String,
    /** The server certificate's key, pinned ([fingerprintOf]). */
    val fingerprint: String,
    val user: String,
    val deviceName: String,
    /** The Keystore alias of the phone's key: it alternates on each renewal, so the old key works until the new one is in. */
    val keyAlias: String,
    @Serializable(with = InstantIsoSerializer::class) val certificateExpires: Instant,
    /** KeyProtection's name: STRONGBOX, TEE or SOFTWARE. */
    val keyProtection: String,
    /** serverInfo, as last answered. */
    val version: String = "",
    val stories: Boolean = false,
    val renewCertificates: Boolean = false,
    /** serverInfo.summaries: the server writes generated articles and the digest. */
    val summaries: Boolean = false,
    /**
     * Where to look when [url] stops answering: the server's DHT key (ed25519, hex), from the enrollment link and
     * serverInfo.discovery, and its [addresses] (`https://host:port`, the one that last worked first; [ServerAddresses]).
     */
    val dhtKey: String? = null,
    val addresses: List<String> = emptyList(),
    /** The address the phone enrolled with: always kept among [addresses]. */
    val enrolled: String? = null,
    /** serverInfo.discovery.id: the server's id as people read it. */
    val serverId: String? = null,
    /** When the DHT last found the server (its address had changed); null if it never had to. */
    @Serializable(with = news.core.model.InstantIsoSerializer::class) val dhtFoundAt: Instant? = null,
)

/** Where [NewsServer] keeps the account and the server's headlines between runs. Blocking. */
interface ServerStore {
    fun loadAccount(): ServerAccount?
    fun saveAccount(account: ServerAccount?)
    fun loadHeadlines(): List<ServerHeadline>
    fun saveHeadlines(rows: List<ServerHeadline>)
}

/** [ServerStore] in the app's private files: the account in shared preferences, the headlines in a JSON file. */
class AndroidServerStore(context: Context) : ServerStore {
    private val prefs = context.getSharedPreferences("news-server", Context.MODE_PRIVATE)
    private val headlinesFile = File(context.filesDir, "server-headlines.json")
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadAccount(): ServerAccount? =
        prefs.getString(ACCOUNT, null)?.let { runCatching { json.decodeFromString(ServerAccount.serializer(), it) }.getOrNull() }

    override fun saveAccount(account: ServerAccount?) {
        prefs.edit().apply {
            if (account == null) remove(ACCOUNT) else putString(ACCOUNT, json.encodeToString(ServerAccount.serializer(), account))
        }.apply()
    }

    override fun loadHeadlines(): List<ServerHeadline> =
        runCatching { json.decodeFromString(ROWS, headlinesFile.readText()) }.getOrDefault(emptyList())

    override fun saveHeadlines(rows: List<ServerHeadline>) {
        if (rows.isEmpty()) {
            headlinesFile.delete()
            return
        }
        val temp = File(headlinesFile.path + ".tmp")
        temp.writeText(json.encodeToString(ROWS, rows))
        temp.renameTo(headlinesFile)
    }

    private companion object {
        const val ACCOUNT = "account"
        val ROWS = ListSerializer(ServerHeadline.serializer())
    }
}
