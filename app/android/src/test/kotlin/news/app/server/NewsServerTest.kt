package news.app.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import news.app.ServerError
import news.app.ServerState
import news.app.server.graphql.type.EnrollmentRejectedReason
import news.app.server.graphql.type.KeyProtection
import news.core.model.Article
import news.core.model.ArticleSummary
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NewsServerTest {

    private val fingerprint = "903ed60d03a8101e46d164881f9b181197c1b8b2da9ae7755935377f3c0143f7"
    private var now = Instant.parse("2026-11-10T08:00:00Z")

    /** The server: answers as told, and records how each call trusted it. */
    private inner class FakeApi(val url: String, val trust: X509TrustManager, val identity: X509ExtendedKeyManager?) : ServerApi {
        /** The server answers only at [answering] (every address when null). */
        private fun reach() {
            answering?.let { if (url !in it) throw IOException("No route to $url") }
        }

        override suspend fun preview(code: String): PreviewResult {
            calls += "preview $code"
            // The TLS handshake: the server shows its certificate to whatever trusts it.
            trust.checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
            return if (code == "EXPIRED") PreviewResult.Rejected(EnrollmentRejectedReason.INVALID_CODE) else PreviewResult.Valid("reader", now + 10.minutes)
        }

        override suspend fun enroll(code: String, deviceName: String, csr: ByteArray, protection: KeyProtection): EnrollResult {
            calls += "enroll $code $deviceName $protection"
            trust.checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
            return enrollAnswer ?: EnrollResult.Enrolled("reader", deviceName, byteArrayOf(1, 2, 3), now + 365.days)
        }

        override suspend fun renew(csr: ByteArray, protection: KeyProtection): EnrollResult {
            calls += "renew"
            return EnrollResult.Enrolled("reader", "Pixel", byteArrayOf(4), now + 365.days)
        }

        override suspend fun info(): ServerInfo {
            calls += if (answering == null) "info" else "info $url"
            down?.let { throw it }
            reach()
            return ServerInfo(1, "0.5.0", renewCertificates = true, stories = true, summaries = true, discovery = discovery)
        }

        override suspend fun headlines(since: Instant?): List<ServerHeadline> {
            calls += if (answering == null) "headlines $since" else "headlines $url"
            down?.let { throw it }
            reach()
            return serverHeadlines
        }

        override suspend fun articles(urls: List<String>): List<Article> {
            calls += "articles ${urls.size}"
            down?.let { throw it }
            return urls.mapNotNull { url -> Article("yellow", url, "t", body = listOf("text")).takeIf { url in serverArticles } }
        }

        override suspend fun grouping(): ServerGrouping? {
            calls += "grouping"
            return grouping
        }

        override suspend fun setGrouping(strictness: Int?, text: news.app.stories.StoryText?, days: Int?, byGemini: Boolean?): ServerGrouping {
            calls += "setGrouping $strictness $text $days"
            if (strictness != null && strictness !in 10..90) throw ServerException("strictness out of range", "BAD_REQUEST")
            return grouping!!.copy(strictness = strictness ?: grouping!!.strictness, text = text ?: grouping!!.text, days = days ?: grouping!!.days)
                .also { grouping = it }
        }

        override suspend fun stats(): ServerStats {
            calls += "stats"
            down?.let { throw it }
            return ServerStats(10, 5, 2, now, 1, null, emptyList())
        }

        override suspend fun sources(): List<ServerSource> {
            calls += "sources"
            down?.let { throw it }
            return listOf(ServerSource("white", "בטא", "https://beta.example", "he"), ServerSource("yellow", "yellow", "https://alpha.example", "he"))
        }

        override suspend fun setSource(id: String, enabled: Boolean?, feed: Boolean?, waitMs: Int?): ServerSource {
            calls += "setSource $id $enabled $feed $waitMs"
            down?.let { throw it }
            if (waitMs != null && waitMs !in 0..60_000) throw ServerException("waitMs out of range", "BAD_REQUEST")
            return ServerSource(id, id, "https://$id", "he", enabled = enabled ?: true, feed = feed ?: false, waitMs = waitMs ?: 250)
        }

        override suspend fun stories(): List<ServerStory> {
            calls += "stories"
            down?.let { throw it }
            return listOf(ServerStory(listOf("y1", "w1"), summary), ServerStory(listOf("y2", "w2")))
        }

        override suspend fun me(): Me {
            meError?.let { throw it }
            return Me("reader", role, "Pixel")
        }

        override suspend fun markRead(urls: List<String>): Int {
            calls += "markRead ${urls.size}"
            down?.let { throw it }
            return urls.size
        }

        override suspend fun videoStream(articleUrl: String): VideoPath? {
            calls += "videoStream $articleUrl"
            return video
        }

        override suspend fun users(): List<ServerUser> {
            calls += "users"
            if (role != UserRole.ADMIN) throw ServerException("admins only", "FORBIDDEN")
            return listOf(ServerUser("reader", UserRole.ADMIN, emptyList()))
        }
    }

    private var role = UserRole.USER
    private var meError: Exception? = null
    private var video: VideoPath? = null

    private val calls = mutableListOf<String>()
    private var answering: Set<String>? = null
    private var grouping: ServerGrouping? = ServerGrouping("bge-m3", news.app.stories.StoryText.TITLE_AND_SUBTITLE, 60, 7, 0.62)
    private var discovery: ServerDiscovery? = null
    private val dhtAsked = mutableListOf<String>()
    private var dhtRecord: news.app.server.discovery.ServerRecord? = null
    private val summary = StorySummary(
        "אונייה הוטבעה",
        listOf(Sentence("אונייה הוטבעה בים השחור.", listOf(Footnote("y1", "yellow", "y1"), Footnote("w1", "white", "w1")))),
        writtenAt = Instant.parse("2026-11-10T08:00:00Z"),
    )
    private val apis = mutableListOf<FakeApi>()
    private var enrollAnswer: EnrollResult? = null
    private var down: Exception? = null
    private var serverHeadlines = listOf(
        ServerHeadline(ArticleSummary("yellow", "y1", "y1"), now, now),
        ServerHeadline(ArticleSummary("white", "w1", "w1"), now, now),
    )
    private var serverArticles = setOf("y1")

    private class FakeKeys : DeviceKeys {
        val keys = mutableMapOf<String, java.security.KeyPair>()
        val certificates = mutableMapOf<String, ByteArray>()

        override fun generate(alias: String): DeviceKey {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            keys[alias] = pair
            return object : DeviceKey {
                override val protection = KeyProtection.STRONGBOX
                override val publicKeyInfo: ByteArray = pair.public.encoded
                override fun sign(data: ByteArray): ByteArray =
                    Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(data); sign() }
            }
        }

        override fun saveCertificate(alias: String, certificateDer: ByteArray) {
            certificates[alias] = certificateDer
        }

        override fun identity(alias: String): DeviceKeyManager? =
            keys[alias]?.takeIf { alias in certificates }?.let { DeviceKeyManager(alias, it.private, arrayOf(testCertificate())) }

        override fun delete(alias: String) {
            keys.remove(alias)
            certificates.remove(alias)
        }
    }

    private class MemoryStore : ServerStore {
        var account: ServerAccount? = null
        var rows: List<ServerHeadline> = emptyList()
        override fun loadAccount() = account
        override fun saveAccount(account: ServerAccount?) {
            this.account = account
        }
        override fun loadHeadlines() = rows
        override fun saveHeadlines(rows: List<ServerHeadline>) {
            this.rows = rows
        }
    }

    private val keys = FakeKeys()
    private val store = MemoryStore()

    private fun TestScope.server(): NewsServer {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return NewsServer(
            store = store,
            keys = keys,
            apiFor = { url, trust, identity -> FakeApi(url, trust, identity).also { apis += it } },
            dht = { key, fingerprint -> dhtAsked += "$key $fingerprint"; dhtRecord },
            stagger = kotlin.time.Duration.ZERO,
            addressesEvery = kotlin.time.Duration.ZERO,
            dhtEvery = kotlin.time.Duration.ZERO,
            deviceName = "Pixel",
            scope = this,
            io = dispatcher,
            clock = { now },
        )
    }

    private suspend fun TestScope.enrolled(): NewsServer = server().apply {
        enroll(EnrollmentLink("K7QM-3XRP", "192.168.50.2", fingerprint))
        advanceUntilIdle()
    }

    @Test
    fun aScannedCodeEnrollsWithTheServersKeyPinned() = runTest {
        val server = enrolled()
        val state = assertIs<ServerState.Connected>(server.state.value)
        assertEquals("192.168.50.2", state.address)
        assertEquals("reader", state.user)
        assertEquals("0.5.0", state.version)
        assertTrue(state.storiesOn)
        // The preview and the enrollment trusted the QR code's key only; then the phone's certificate went along.
        assertTrue(apis.take(2).all { it.trust is PinnedTrustManager && it.identity == null })
        assertNotNull(apis.last().identity)
        assertEquals(listOf("preview K7QM-3XRP", "enroll K7QM-3XRP Pixel STRONGBOX", "info"), calls)
        val account = assertNotNull(store.account)
        assertEquals("https://192.168.50.2:56399", account.url)
        assertEquals(fingerprint, account.fingerprint)
        assertEquals("STRONGBOX", account.keyProtection)
        assertTrue(keys.certificates.containsKey(account.keyAlias))
        assertTrue(server.inUse)
    }

    @Test
    fun aLinkWithoutAServerUsesItsFirstAddressAndKeepsTheDhtKey() = runTest {
        val server = server()
        server.enroll(EnrollmentLink("K7QM-3XRP", null, fingerprint, key = "ab12", addresses = listOf("https://203.0.113.7:56400", "https://192.168.50.150:56399")))
        advanceUntilIdle()
        assertIs<ServerState.Connected>(server.state.value)
        val account = assertNotNull(store.account)
        assertEquals("https://203.0.113.7:56400", account.url)
        assertEquals("ab12", account.dhtKey)
        assertEquals(2, account.addresses.size)
    }

    @Test
    fun aServerShowingAnotherKeyThanTheQrCodesIsRefused() = runTest {
        val server = server()
        server.enroll(EnrollmentLink("K7QM-3XRP", "192.168.50.2", "0".repeat(64)))
        advanceUntilIdle()
        assertEquals(ServerState.NotConnected("192.168.50.2", "K7QM-3XRP", ServerError.WRONG_KEY), server.state.value)
        assertNull(store.account)
        assertTrue(keys.keys.isEmpty())
    }

    @Test
    fun aTypedCodeWaitsForTheOwnerToConfirmTheKey() = runTest {
        val server = server()
        server.setAddress("192.168.50.2:8443")
        server.setCode("K7QM-3XRP")
        server.connect()
        advanceUntilIdle()
        assertEquals(ServerState.ConfirmKey("192.168.50.2:8443", "K7QM-3XRP", "reader", "903e d60d 03a8 101e"), server.state.value)
        // Only the code went out, and nothing was made yet.
        assertEquals(listOf("preview K7QM-3XRP"), calls)
        assertTrue(keys.keys.isEmpty())

        server.confirmKey()
        advanceUntilIdle()
        assertIs<ServerState.Connected>(server.state.value)
        // From then on the key the owner confirmed is the one trusted.
        assertIs<PinnedTrustManager>(apis[1].trust)
        assertEquals(fingerprint, store.account?.fingerprint)
    }

    @Test
    fun cancellingTheKeyKeepsTheForm() = runTest {
        val server = server()
        server.setAddress("192.168.50.2")
        server.setCode("K7QM-3XRP")
        server.connect()
        advanceUntilIdle()
        server.cancel()
        assertEquals(ServerState.NotConnected("192.168.50.2", "K7QM-3XRP"), server.state.value)
    }

    @Test
    fun anExpiredCodeSaysSo() = runTest {
        val server = server()
        server.enroll(EnrollmentLink("EXPIRED", "192.168.50.2", fingerprint))
        advanceUntilIdle()
        assertEquals(ServerState.NotConnected("192.168.50.2", "EXPIRED", ServerError.CODE_EXPIRED), server.state.value)
    }

    @Test
    fun aCodeTurnedDownAtEnrollmentDeletesTheNewKey() = runTest {
        enrollAnswer = EnrollResult.Rejected(EnrollmentRejectedReason.INVALID_CODE)
        val server = server()
        server.enroll(EnrollmentLink("K7QM-3XRP", "192.168.50.2", fingerprint))
        advanceUntilIdle()
        assertEquals(ServerError.CODE_EXPIRED, (server.state.value as ServerState.NotConnected).error)
        assertTrue(keys.keys.isEmpty())
    }

    @Test
    fun noAddressAnywhereIsABadAddress() = runTest {
        val server = server()
        server.enroll(EnrollmentLink("K7QM-3XRP", null, fingerprint))
        assertEquals(ServerState.NotConnected("", "K7QM-3XRP", ServerError.BAD_ADDRESS), server.state.value)
    }

    @Test
    fun anUnreachableAddressSaysSo() = runTest {
        val server = NewsServer(
            store, keys, apiFor = { _, _, _ -> throw IOException("connect timed out") }, deviceName = "Pixel",
            scope = this, io = StandardTestDispatcher(testScheduler), clock = { now },
        )
        server.enroll(EnrollmentLink("K7QM-3XRP", "192.168.50.2", fingerprint))
        advanceUntilIdle()
        assertEquals(ServerError.UNREACHABLE, (server.state.value as ServerState.NotConnected).error)
    }

    @Test
    fun headlinesAreAskedForSinceTheNewestSighting() = runTest {
        val server = enrolled()
        calls.clear()
        assertEquals(listOf("w1", "y1"), server.headlines()?.map { it.url })
        now += 10.minutes
        server.headlines(skip = setOf("white"))
        assertEquals(listOf("headlines ${now - 10.minutes - 1.days}", "headlines ${now - 10.minutes}"), calls)
        assertEquals(2, store.rows.size)
    }

    @Test
    fun aServerThatDoesntAnswerIsMarkedUntilItAnswersAgain() = runTest {
        val server = enrolled()
        down = IOException("No route to host")
        assertNull(server.headlines())
        val state = assertIs<ServerState.Connected>(server.state.value)
        assertEquals(now, state.unreachableSince)
        assertTrue(server.inUse)
        assertTrue(!server.answering && !server.storiesOn)
        assertNull(server.stories(emptyList()))
        // Back: the mark goes at the next answer.
        down = null
        assertEquals(2, server.headlines()?.size)
        assertNull((server.state.value as ServerState.Connected).unreachableSince)
        assertTrue(server.answering)
    }

    @Test
    fun disconnectedNothingIsAsked() = runTest {
        val server = enrolled()
        server.disconnect()
        advanceUntilIdle()
        calls.clear()
        assertFalse(server.inUse)
        assertNull(server.headlines())
        assertTrue(server.articles(listOf("y1")).isEmpty())
        assertNull(server.stories(emptyList()))
        assertEquals(emptyList(), calls)
    }

    @Test
    fun articlesAndStoriesComeFromTheServer() = runTest {
        val server = enrolled()
        assertEquals(setOf("y1"), server.articles(listOf("y1", "y2")).keys)
        val items = listOf(ArticleSummary("yellow", "y1", "y1"), ArticleSummary("white", "w1", "w1"))
        assertEquals(listOf(listOf("y1", "w1")), server.stories(items)?.map { s -> s.reports.map { it.url } })
    }

    @Test
    fun theSitesAreTheServers() = runTest {
        val server = enrolled()
        assertEquals(listOf("white", "yellow"), server.sources()?.map { it.id })
    }

    @Test
    fun aSitesSettingsChangeOnTheServer() = runTest {
        val server = enrolled()
        calls.clear()
        assertEquals(false, server.setSource("white", enabled = false)?.enabled)
        assertEquals(1000, server.setSource("yellow", waitMs = 1000)?.waitMs)
        assertEquals(listOf("setSource white false null null", "setSource yellow null null 1000"), calls)
        // Turned down: nothing changes, and the server still counts as answering.
        assertNull(server.setSource("yellow", waitMs = 99_000))
        assertTrue(server.answering)
    }

    @Test
    fun theGeneratedArticlesComeWithTheStories() = runTest {
        val server = enrolled()
        val items = listOf("y1" to "yellow", "w1" to "white", "y2" to "yellow", "w2" to "white").map { (u, s) -> ArticleSummary(s, u, u) }
        assertEquals(2, server.stories(items)?.size)
        // Found by any of the story's reports; a story not written yet has none.
        assertEquals(summary, server.writing.value.summaries["w1"])
        assertEquals(summary, server.writing.value.summaries["y1"])
        assertNull(server.writing.value.summaries["y2"])
    }

    @Test
    fun theServersAddressesAndDhtKeyAreKeptFromServerInfo() = runTest {
        discovery = ServerDiscovery(fingerprint, "ID", "ab".repeat(32), listOf("https://203.0.113.7:56400", "https://[2001:db8::7]:56399"))
        enrolled()
        val account = assertNotNull(store.account)
        assertEquals(listOf("https://192.168.50.2:56399", "https://203.0.113.7:56400", "https://[2001:db8::7]:56399"), account.addresses)
        assertEquals("ab".repeat(32), account.dhtKey)
        assertEquals("ID", account.serverId)
        // Another server's discovery (another key) isn't taken.
        discovery = ServerDiscovery("00".repeat(32), "OTHER", "cd".repeat(32), listOf("https://203.0.113.9:1"))
        enrolled().start()
        advanceUntilIdle()
        assertEquals("ab".repeat(32), store.account?.dhtKey)
    }

    @Test
    fun whenTheAddressStopsAnsweringTheKeptOnesAreTriedInOrder() = runTest {
        discovery = ServerDiscovery(fingerprint, "ID", "ab".repeat(32), listOf("https://203.0.113.7:56400", "https://10.0.0.9:56399"))
        val server = enrolled()
        calls.clear()
        // Away from home: only the public address answers.
        answering = setOf("https://203.0.113.7:56400")
        assertEquals(2, server.headlines()?.size)
        assertEquals(
            listOf("headlines https://192.168.50.2:56399", "info https://203.0.113.7:56400", "headlines https://203.0.113.7:56400"),
            calls.filter { it.startsWith("headlines") || it == "info https://203.0.113.7:56400" },
        )
        // It is used from then on and goes first; the DHT wasn't needed.
        assertEquals("https://203.0.113.7:56400", store.account?.url)
        assertEquals("https://203.0.113.7:56400", store.account?.addresses?.first())
        assertEquals("203.0.113.7:56400", (server.state.value as ServerState.Connected).address)
        assertTrue(dhtAsked.isEmpty())
        assertNull(store.account?.dhtFoundAt)
    }

    @Test
    fun whenNoKeptAddressAnswersTheDhtFindsTheServer() = runTest {
        discovery = ServerDiscovery(fingerprint, "ID", "ab".repeat(32), listOf("https://10.0.0.9:56399"))
        val server = enrolled()
        // The router gave the house a new IP: only the DHT knows it.
        answering = setOf("https://198.51.100.4:56400")
        dhtRecord = news.app.server.discovery.ServerRecord(listOf("https://198.51.100.4:56400"), 0)
        assertEquals(2, server.headlines()?.size)
        assertEquals(listOf("${"ab".repeat(32)} $fingerprint"), dhtAsked)
        val account = assertNotNull(store.account)
        assertEquals("https://198.51.100.4:56400", account.url)
        assertEquals(now, account.dhtFoundAt)
        assertEquals(now, (server.state.value as ServerState.Connected).dhtFoundAt)
        // The key stays pinned at the new address.
        assertTrue(apis.last().trust is PinnedTrustManager)
    }

    @Test
    fun nothingAnswersAnywhere() = runTest {
        discovery = ServerDiscovery(fingerprint, "ID", "ab".repeat(32), listOf("https://10.0.0.9:56399"))
        val server = enrolled()
        answering = emptySet()
        assertNull(server.headlines())
        assertEquals(1, dhtAsked.size)
        assertEquals("https://192.168.50.2:56399", store.account?.url)
        assertNotNull((server.state.value as ServerState.Connected).unreachableSince)
    }

    @Test
    fun theServersGroupingIsReadAndChanged() = runTest {
        val server = enrolled()
        assertEquals(60, server.grouping()?.strictness)
        val changed = server.setGrouping(strictness = 70, text = news.app.stories.StoryText.TITLE)
        assertEquals(70, changed?.strictness)
        assertEquals(news.app.stories.StoryText.TITLE, changed?.text)
        assertEquals(7, changed?.days)
        // Out of range: the server says no, nothing changes, and it still counts as answering.
        assertNull(server.setGrouping(strictness = 99))
        assertTrue(server.answering)
    }

    @Test
    fun aCertificateIsRenewedInItsLast30Days() = runTest {
        val server = enrolled()
        val first = store.account!!.keyAlias
        server.renewIfDue()
        assertEquals(first, store.account!!.keyAlias)

        now += 340.days
        server.renewIfDue()
        advanceUntilIdle()
        val renewed = store.account!!
        assertTrue(renewed.keyAlias != first)
        assertEquals(now + 365.days, renewed.certificateExpires)
        // The old key goes once the new one is in.
        assertEquals(setOf(renewed.keyAlias), keys.keys.keys)
    }

    @Test
    fun disconnectingForgetsTheServer() = runTest {
        val server = enrolled()
        server.headlines()
        server.disconnect()
        advanceUntilIdle()
        assertEquals(ServerState.NotConnected("192.168.50.2"), server.state.value)
        assertNull(store.account)
        assertTrue(store.rows.isEmpty())
        assertTrue(keys.keys.isEmpty())
        assertTrue(!server.inUse)
    }

    // --- Roles, read marks, video ---

    @Test
    fun theUsersRoleComesWithConnecting() = runTest {
        val server = enrolled()
        assertFalse(assertIs<ServerState.Connected>(server.state.value).admin)
        assertEquals(UserRole.USER, server.role)
        assertEquals("USER", store.account?.role)
    }

    @Test
    fun anAdminIsToldSo() = runTest {
        role = UserRole.ADMIN
        val server = enrolled()
        assertTrue(assertIs<ServerState.Connected>(server.state.value).admin)
        assertEquals(listOf(ServerUser("reader", UserRole.ADMIN, emptyList())), server.users().getOrThrow())
    }

    @Test
    fun aServerFromBeforeRolesLetsEveryoneChangeIt() = runTest {
        meError = ServerException("Cannot query field \"me\" on type \"Query\".", "GRAPHQL_VALIDATION_FAILED")
        val server = enrolled()
        assertTrue(assertIs<ServerState.Connected>(server.state.value).admin)
    }

    @Test
    fun forbiddenTakesTheAdminControlsAway() = runTest {
        role = UserRole.ADMIN
        val server = enrolled()
        role = UserRole.USER // another admin made this one a user meanwhile
        val result = server.users()
        assertEquals("FORBIDDEN", (result.exceptionOrNull() as? ServerException)?.code)
        assertFalse(assertIs<ServerState.Connected>(server.state.value).admin)
        assertEquals(UserRole.USER, server.role)
    }

    @Test
    fun readMarksGoInBatchesOfFiveHundred() = runTest {
        val server = enrolled()
        calls.clear()
        assertTrue(server.markRead((1..1200).map { "u$it" }))
        assertEquals(listOf("markRead 500", "markRead 500", "markRead 200"), calls)
    }

    @Test
    fun readMarksThatCantGoSaySo() = runTest {
        val server = enrolled()
        down = IOException("down")
        assertFalse(server.markRead(listOf("u1")))
    }

    @Test
    fun theVideoPlaysFromTheServersAddress() = runTest {
        val server = enrolled()
        video = VideoPath("/video/abc/index.m3u8", hls = true)
        assertEquals(ServerVideo("https://192.168.50.2:56399/video/abc/index.m3u8", true), server.videoStream("y1"))
        video = null
        assertNull(server.videoStream("y2"))
    }

    @Test
    fun videoUrlJoinsBaseAndPath() {
        assertEquals("https://h.example:1/video/t/file", videoUrl("https://h.example:1/", "/video/t/file"))
        assertEquals("https://h.example:1/video/t/file", videoUrl("https://h.example:1", "video/t/file"))
    }
}
