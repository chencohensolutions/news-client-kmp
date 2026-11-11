package news.app.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import news.app.server.discovery.DhtClient
import news.app.server.discovery.ServerAddresses
import news.app.server.discovery.ServerRecord
import news.app.server.discovery.firstToAnswer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import news.app.ServerError
import news.app.ServerState
import news.app.stories.Story
import news.app.server.graphql.type.EnrollmentRejectedReason
import news.app.server.graphql.type.KeyProtection
import news.core.model.Article
import news.core.model.ArticleSummary
import java.io.IOException
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Makes a [ServerApi] for the server at a URL, trusting it by [trust], as the phone ([identity]) once enrolled. */
typealias ServerApiFactory = (url: String, trust: X509TrustManager, identity: X509ExtendedKeyManager?) -> ServerApi

/**
 * The phone's connection to the news server (news-server-go), where all of the app's news comes from: the phone never
 * reads the sites itself. Until it is enrolled, the app shows only the connect screen.
 *
 * Enrolling, as the journal app does with journal-server-go: `news-server enroll-code <user>` prints a one-time code and a
 * QR code ([EnrollmentLink]); the phone makes an EC P-256 key in the Android Keystore ([DeviceKeys]), sends a
 * certificate request with the code (enrollDevice) and keeps the certificate, its identity for mutual TLS from then on.
 * The server's own certificate is checked by its key ([PinnedTrustManager]): the one in the QR code, or, when the code
 * is typed, the one the server shows, once the owner confirmed it ([ServerState.ConfirmKey]). The certificate is renewed
 * in its last 30 days ([renewIfDue]).
 *
 * Connected, [NewsRepository][news.app.NewsRepository] takes the headlines ([headlines], asking only for what changed
 * since the last time) and articles ([articles]) from the server, and
 * the view model its stories ([stories]) when it groups them. A failure marks it unreachable
 * ([ServerState.Connected.unreachableSince]): the feed stays as it was, and every later call tries the server again.
 */
class NewsServer(
    private val store: ServerStore,
    private val keys: DeviceKeys,
    private val apiFor: ServerApiFactory,
    /** The phone's name as the server lists it (`news-server devices`). */
    private val deviceName: String,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = { Clock.System.now() },
    /** Looks the server up in the BitTorrent DHT by its key, opening its record with its fingerprint ([DhtClient]). */
    private val dht: suspend (key: String, fingerprint: String) -> ServerRecord? = { key, fingerprint -> DhtClient().lookup(key, fingerprint) },
    /** Kept addresses are tried this far apart, the likeliest first ([firstToAnswer]). */
    private val stagger: Duration = 400.milliseconds,
    /** After the kept addresses found nothing, how long before a failure tries them again. */
    private val addressesEvery: Duration = 10.seconds,
    /** At most one DHT lookup this often. */
    private val dhtEvery: Duration = 2.minutes,
) {
    private var account: ServerAccount? = store.loadAccount()
    private var rows: List<ServerHeadline> = emptyList()
    private var rowsLoaded = false

    /** When the server last read the sites ([ServerFeed.lastRead]), once known. */
    private var lastRead: Instant? = null
    private val feedLock = Mutex()

    /** The API with the phone's certificate, for [account]; dropped when the account changes. */
    private var connected: ServerApi? = null

    private val _state = MutableStateFlow<ServerState>(account?.let { connectedState(it, null, null) } ?: ServerState.NotConnected())
    val state: StateFlow<ServerState> = _state

    private var job: Job? = null

    /** A typed code's enrollment waiting for the owner to confirm the server's key ([ServerState.ConfirmKey]). */
    private var pending: Pending? = null

    private class Pending(
        val url: String,
        val code: String,
        val fingerprint: String,
        val user: String,
        val dhtKey: String? = null,
        val addresses: List<String> = emptyList(),
    )

    /** Whether the phone is enrolled: the news comes from the server, whether or not it answers just now. */
    val inUse: Boolean get() = account != null

    /** The server's generated articles and digest ([stories]); empty when it doesn't write them. */
    @kotlinx.serialization.Serializable
    data class Writing(val summaries: Map<String, StorySummary> = emptyMap())

    private val _writing = MutableStateFlow(Writing())

    /**
     * Keeps [writing] in [file] between runs: the last generated articles show at once on launch, before the server
     * answers, and are replaced by its own as they come.
     */
    fun keepWritingIn(file: java.io.File) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        scope.launch(io) {
            if (inUse) {
                runCatching { json.decodeFromString(Writing.serializer(), file.readText()) }.getOrNull()
                    ?.let { cached -> _writing.compareAndSet(Writing(), cached) }
            }
            var saved = _writing.value
            _writing.collect { w ->
                if (w == saved) return@collect
                saved = w
                runCatching {
                    val tmp = java.io.File(file.path + ".tmp")
                    tmp.writeText(json.encodeToString(Writing.serializer(), w))
                    tmp.renameTo(file)
                }
            }
        }
    }

    /** The generated articles of the stories last asked for, by any of their reports' URLs, and the latest digest. */
    val writing: StateFlow<Writing> = _writing

    /** [inUse] and not marked unreachable. */
    val answering: Boolean get() = inUse && (_state.value as? ServerState.Connected)?.unreachableSince == null

    /** Whether the stories should come from the server: [inUse], answering, and grouping them. */
    val storiesOn: Boolean
        get() = inUse && account?.stories == true && (_state.value as? ServerState.Connected)?.unreachableSince == null

    // --- Enrolling ---------------------------------------------------------------------------------------------------

    fun setAddress(address: String) = editForm { it.copy(address = address, error = null) }

    fun setCode(code: String) = editForm { it.copy(code = code, error = null) }

    private fun editForm(change: (ServerState.NotConnected) -> ServerState.NotConnected) {
        _state.update { (it as? ServerState.NotConnected)?.let(change) ?: it }
    }

    /** Enrolls with the typed address and code: the server's key is shown for confirming first. */
    fun connect() {
        val form = _state.value as? ServerState.NotConnected ?: return
        start(form.address, form.code, fingerprint = null)
    }

    /**
     * Enrolls from a scanned QR code or a tapped `news://enroll` link: its key is trusted, so nothing waits for the
     * owner. A link without a server keeps the typed address.
     */
    fun enroll(link: EnrollmentLink) {
        if (account != null) return
        val address = link.server ?: link.addresses.firstOrNull() ?: (_state.value as? ServerState.NotConnected)?.address.orEmpty()
        start(address, link.code, link.fingerprint, link)
    }

    private fun start(address: String, code: String, fingerprint: String?, link: EnrollmentLink? = null) {
        val url = serverUrl(address)
        if (url == null) {
            _state.value = ServerState.NotConnected(address, code, ServerError.BAD_ADDRESS)
            return
        }
        val shown = displayAddress(url)
        job?.cancel()
        job = scope.launch {
            _state.value = ServerState.Connecting(shown, code)
            try {
                if (fingerprint != null) {
                    val preview = apiFor(url, PinnedTrustManager(fingerprint), null).preview(code)
                    val user = (preview as? PreviewResult.Valid)?.user ?: return@launch reject(shown, code, (preview as PreviewResult.Rejected).reason)
                    finish(Pending(url, code, fingerprint, user, link?.key, link?.addresses.orEmpty()))
                } else {
                    // Nothing to check the server against yet: only the code goes out, and the key that came back is shown.
                    val seeing = KeySeeingTrustManager()
                    val preview = apiFor(url, seeing, null).preview(code)
                    val user = (preview as? PreviewResult.Valid)?.user ?: return@launch reject(shown, code, (preview as PreviewResult.Rejected).reason)
                    val seen = seeing.seen ?: throw IOException("No server key")
                    pending = Pending(url, code, seen, user)
                    _state.value = ServerState.ConfirmKey(shown, code, user, shortKey(seen))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ServerState.NotConnected(shown, code, errorOf(e))
            }
        }
    }

    /** The owner compared the key with what `enroll-code` printed: enrolls with it pinned. */
    fun confirmKey() {
        val waiting = pending ?: return
        pending = null
        job = scope.launch {
            try {
                finish(waiting)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ServerState.NotConnected(displayAddress(waiting.url), waiting.code, errorOf(e))
            }
        }
    }

    /** Stops an enrollment under way, or a key waiting for confirming; the form keeps the address and code. */
    fun cancel() {
        job?.cancel()
        pending = null
        val now = _state.value
        if (account == null) {
            _state.value = when (now) {
                is ServerState.Connecting -> ServerState.NotConnected(now.address, now.code)
                is ServerState.ConfirmKey -> ServerState.NotConnected(now.address, now.code)
                else -> now
            }
        }
    }

    private suspend fun finish(enrolling: Pending) {
        val shown = displayAddress(enrolling.url)
        _state.value = ServerState.Connecting(shown, enrolling.code, enrolling.user)
        val alias = KEY_ALIASES.first()
        val (key, csr) = withContext(io) {
            val key = keys.generate(alias)
            key to CertificateRequest.build(deviceName, key.publicKeyInfo, key::sign)
        }
        val answer = try {
            apiFor(enrolling.url, PinnedTrustManager(enrolling.fingerprint), null).enroll(enrolling.code, deviceName, csr, key.protection)
        } catch (e: Exception) {
            withContext(io) { keys.delete(alias) }
            throw e
        }
        when (answer) {
            is EnrollResult.Rejected -> {
                withContext(io) { keys.delete(alias) }
                reject(shown, enrolling.code, answer.reason)
            }
            is EnrollResult.Enrolled -> {
                withContext(io) { keys.saveCertificate(alias, answer.certificate) }
                val enrolled = ServerAccount(
                    url = enrolling.url,
                    fingerprint = enrolling.fingerprint,
                    user = answer.user,
                    deviceName = answer.deviceName,
                    keyAlias = alias,
                    certificateExpires = answer.expiresAt,
                    keyProtection = key.protection.name,
                    dhtKey = enrolling.dhtKey,
                    // The enrollment address first, then the link's others: tried in that order when it stops answering.
                    addresses = (listOf(enrolling.url) + enrolling.addresses.mapNotNull(ServerAddresses::originOf)).distinct(),
                    enrolled = enrolling.url,
                )
                setAccount(enrolled)
                _state.value = connectedState(enrolled, null, null)
                // What it can do: stories, renewal. Not knowing yet only leaves the stories to the phone.
                runCatching { refreshInfo() }
            }
        }
    }

    private fun reject(address: String, code: String, reason: EnrollmentRejectedReason) {
        _state.value = ServerState.NotConnected(
            address, code,
            when (reason) {
                EnrollmentRejectedReason.INVALID_CODE -> ServerError.CODE_EXPIRED
                EnrollmentRejectedReason.RATE_LIMITED -> ServerError.RATE_LIMITED
                else -> ServerError.FAILED
            },
        )
    }

    // --- Connected ---------------------------------------------------------------------------------------------------

    /** Asks the server again (serverInfo); answering, the unreachable mark goes. */
    fun retry() {
        scope.launch { runCatching { refreshInfo() } }
    }

    /** Forgets the server: the key, the certificate and its headlines. The server still lists the device until revoked. */
    fun disconnect() {
        val current = account ?: return
        job?.cancel()
        scope.launch {
            withContext(io) {
                KEY_ALIASES.forEach { runCatching { keys.delete(it) } }
                feedLock.withLock {
                    rows = emptyList()
                    store.saveHeadlines(emptyList())
                }
            }
        }
        setAccount(null)
        _writing.value = Writing()
        _state.value = ServerState.NotConnected(displayAddress(current.url))
    }

    /** Called when the app starts: what the server can do now, and the certificate renewed when due. */
    fun start() {
        if (account == null) return
        scope.launch {
            feedLock.withLock { loadRows() }
            runCatching { refreshInfo() }
            runCatching { renewIfDue() }
        }
    }

    private suspend fun refreshInfo() {
        val current = account ?: return
        try {
            val info = call { it.info() }
            val now = account ?: return
            // Where the server is now and its DHT key, refreshed at every answer; a new key would be another server.
            val found = info.discovery?.takeIf { it.fingerprint.equals(now.fingerprint, ignoreCase = true) }
            val updated = now.copy(
                version = info.version, stories = info.stories, renewCertificates = info.renewCertificates, summaries = info.summaries,
                addresses = found?.let { ServerAddresses.merge(now.addresses, now.url, it.addresses, now.enrolled) } ?: now.addresses,
                dhtKey = found?.key ?: now.dhtKey,
                serverId = found?.id ?: now.serverId,
            )
            setAccount(updated)
            reachable(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            throw e
        }
    }

    /**
     * In the certificate's last 30 days, a new key and a certificate for it (renewCertificate), asked with the
     * current one; the old key is deleted once the new one is in. Nothing when the server doesn't renew.
     */
    suspend fun renewIfDue() {
        val current = account ?: return
        if (!current.renewCertificates || current.certificateExpires - clock() > RENEW_BEFORE) return
        val next = KEY_ALIASES.first { it != current.keyAlias }
        val (key, csr) = withContext(io) {
            val key = keys.generate(next)
            key to CertificateRequest.build(current.deviceName, key.publicKeyInfo, key::sign)
        }
        when (val answer = call { it.renew(csr, key.protection) }) {
            is EnrollResult.Rejected -> withContext(io) { keys.delete(next) }
            is EnrollResult.Enrolled -> {
                withContext(io) { keys.saveCertificate(next, answer.certificate) }
                val renewed = current.copy(keyAlias = next, certificateExpires = answer.expiresAt, keyProtection = key.protection.name)
                setAccount(renewed)
                withContext(io) { keys.delete(current.keyAlias) }
                _state.update { (it as? ServerState.Connected)?.copy(certificateExpires = answer.expiresAt) ?: it }
            }
        }
    }

    // --- The news --------------------------------------------------------------------------------------------------

    /**
     * The server's feed, but [skip]'s sites ([ServerFeed.current]), after asking for what changed since the last time;
     * null when it isn't [inUse] or doesn't answer (then marked unreachable): the feed stays as it was.
     */
    suspend fun headlines(skip: Set<String> = emptySet()): List<ArticleSummary>? {
        val current = account ?: return null
        return try {
            val feed = feedLock.withLock {
                loadRows()
                val now = clock()
                val since = ServerFeed.since(rows) ?: (now - ServerFeed.FIRST_ASK)
                val fresh = call { it.headlines(since) }
                rows = ServerFeed.prune(ServerFeed.merge(rows, fresh), now)
                withContext(io) { store.saveHeadlines(rows) }
                ServerFeed.current(rows, skip)
            }
            reachable(current)
            feed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /**
     * The articles of [urls] the server has, asked [ServerApi.MAX_ARTICLES] at a time; empty when it isn't [inUse] or
     * doesn't answer.
     */
    suspend fun articles(urls: List<String>): Map<String, Article> {
        val current = account ?: return emptyMap()
        if (urls.isEmpty()) return emptyMap()
        return try {
            urls.distinct().chunked(ServerApi.MAX_ARTICLES).flatMap { chunk -> call { it.articles(chunk) } }.associateBy { it.url }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            emptyMap()
        }
    }

    /**
     * The server's stories over [items] ([ServerFeed.stories]); null when they aren't [storiesOn] or it doesn't answer.
     * With summaries on, [writing] gets their generated articles and the digest along.
     */
    suspend fun stories(items: List<ArticleSummary>): List<Story>? {
        if (!storiesOn) return null
        val current = account ?: return null
        return try {
            val fromServer = call { it.stories() }
            val stories = ServerFeed.stories(fromServer.map { it.urls }, items)
            _writing.value = if (current.summaries) {
                Writing(ServerFeed.summaries(fromServer, stories))
            } else {
                Writing()
            }
            stories
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /**
     * Changes one site's settings on the server ([ServerApi.setSource]); its settings as they are now, or null when it
     * isn't [inUse] or doesn't take it.
     */
    suspend fun setSource(id: String, enabled: Boolean? = null, feed: Boolean? = null, waitMs: Int? = null): ServerSource? {
        val current = account ?: return null
        return try {
            call { it.setSource(id, enabled, feed, waitMs) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            // BAD_REQUEST: the server answered, it just didn't take this change.
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** Whether Gemini reads the full articles; null when it isn't [inUse], summaries are off, or it doesn't answer. */
    suspend fun fullArticles(): Boolean? {
        account ?: return null
        return try {
            call { it.fullArticles() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** How often stories are written again ([ServerApi.rewrite]); null when it isn't [inUse] or doesn't say. */
    suspend fun rewrite(): Rewrite? {
        account ?: return null
        return try {
            call { it.rewrite() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Changes [rewrite]; the settings now, or null when it didn't take them. */
    suspend fun setRewrite(minutes: Int? = null, onNewSite: Boolean? = null, reserveAfter: Int? = null): Rewrite? {
        account ?: return null
        return try {
            call { it.setRewrite(minutes, onNewSite, reserveAfter) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** How long the generated stories are (SHORT, MEDIUM, LONG, FULL); null when it isn't [inUse] or doesn't say. */
    suspend fun storyLength(): String? {
        account ?: return null
        return try {
            call { it.storyLength() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Changes how long the generated stories are; the length now, or null when it didn't take it. */
    suspend fun setStoryLength(length: String): String? {
        account ?: return null
        return try {
            call { it.setStoryLength(length) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Changes whether Gemini reads the full articles; the value now, or null when it didn't take it. */
    suspend fun setFullArticles(on: Boolean): Boolean? {
        account ?: return null
        return try {
            call { it.setFullArticles(on) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The threads, latest updated first; null when it isn't [inUse] or doesn't answer. */
    suspend fun threads(first: Int = 200): List<ThreadRef>? {
        val current = account ?: return null
        return try {
            call { it.threads(first) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** Every thread with its stories, for the map; null when it isn't [inUse] or doesn't answer. */
    suspend fun threadsMap(first: Int = 500): List<ServerThread>? {
        val current = account ?: return null
        return try {
            call { it.threadsMap(first) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** One thread's page ([ServerApi.thread]); null when it isn't [inUse], there is none, or it doesn't answer. */
    suspend fun thread(id: String): ServerThread? {
        val current = account ?: return null
        return try {
            call { it.thread(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** How the server groups stories; null when it isn't [inUse], has stories off, or doesn't answer. */
    suspend fun grouping(): ServerGrouping? {
        val current = account ?: return null
        return try {
            call { it.grouping() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** Changes how the server groups stories; its grouping as it is now, or null when it didn't take it. */
    suspend fun setGrouping(strictness: Int? = null, text: news.app.stories.StoryText? = null, days: Int? = null, byGemini: Boolean? = null): ServerGrouping? {
        val current = account ?: return null
        return try {
            call { it.setGrouping(strictness, text, days, byGemini) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            null
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** What the server has done (the statistics page); null when it isn't [inUse] or doesn't answer. */
    suspend fun stats(): ServerStats? {
        val current = account ?: return null
        return try {
            call { it.stats() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    /** The sites the server reads; null when it isn't [inUse] or doesn't answer. */
    suspend fun sources(): List<ServerSource>? {
        val current = account ?: return null
        return try {
            call { it.sources() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreachable(current)
            null
        }
    }

    // --- Helpers -----------------------------------------------------------------------------------------------------

    /** The headlines kept from earlier runs, once; under [feedLock]. */
    private suspend fun loadRows() {
        if (rowsLoaded) return
        rows = withContext(io) { store.loadHeadlines() }
        rowsLoaded = true
        ServerFeed.lastRead(rows)?.let { read -> lastRead = read; _state.update { (it as? ServerState.Connected)?.copy(lastRead = read) ?: it } }
    }

    /**
     * [block] with the server's API; when it fails on the network, the server is looked for at its other addresses and
     * in the DHT ([relocate]), and [block] runs once more where it answers.
     */
    private suspend fun <T> call(block: suspend (ServerApi) -> T): T {
        val at = account?.url
        try {
            return block(api())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (at == null || errorOf(e) != ServerError.UNREACHABLE) throw e
            relocate(at) ?: throw e
            return block(api())
        }
    }

    private val findLock = Mutex()
    private var lastRound: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastDht: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * Finds the server again after it stopped answering at [failed] (as the journal app's ServerFinder): the kept
     * addresses, the one that last worked first, each [stagger] after the one before; when none answers, its record in
     * the DHT (at most every [dhtEvery]) and the addresses in it. Whatever answers is the server: the pinned key is
     * checked at every address. The address found is used from then on and goes first in the kept list. Null when
     * nothing answers.
     */
    suspend fun relocate(failed: String): String? = findLock.withLock {
        val current = account ?: return null
        // Another call found it while this one waited.
        if (current.url != failed) return current.url
        if (lastRound?.let { it.elapsedNow() < addressesEvery } == true) return null
        lastRound = TimeSource.Monotonic.markNow()
        val identity = withContext(io) { keys.identity(current.keyAlias) } ?: return null
        val probe: suspend (String) -> Boolean = { origin ->
            apiFor(origin, PinnedTrustManager(current.fingerprint), identity).info()
            true
        }
        firstToAnswer(current.addresses.filter { it != failed }, stagger, probe)?.let { return use(it, viaDht = false) }

        val key = current.dhtKey ?: return null
        if (lastDht?.let { it.elapsedNow() < dhtEvery } == true) return null
        lastDht = TimeSource.Monotonic.markNow()
        val record = try {
            dht(key, current.fingerprint)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        setAccount(current.copy(addresses = ServerAddresses.withFound(current.addresses, record.addresses, current.enrolled)))
        val found = record.addresses.mapNotNull(ServerAddresses::originOf).distinct().filter { it != failed }
        firstToAnswer(found, stagger, probe)?.let { use(it, viaDht = true) }
    }

    /** [origin] answers: the server's address from now on, first in the kept list. */
    private fun use(origin: String, viaDht: Boolean): String {
        val current = account ?: return origin
        val moved = current.copy(
            url = origin,
            addresses = ServerAddresses.working(current.addresses, origin, current.enrolled),
            dhtFoundAt = if (viaDht) clock() else current.dhtFoundAt,
        )
        setAccount(moved)
        _state.update { (it as? ServerState.Connected)?.copy(address = displayAddress(origin), addressCount = moved.addresses.size, dhtFoundAt = moved.dhtFoundAt) ?: it }
        return origin
    }

    private suspend fun api(): ServerApi {
        connected?.let { return it }
        val current = account ?: throw IllegalStateException("Not enrolled")
        val identity = withContext(io) { keys.identity(current.keyAlias) } ?: throw IllegalStateException("No key for the server")
        return apiFor(current.url, PinnedTrustManager(current.fingerprint), identity).also { connected = it }
    }

    private fun setAccount(new: ServerAccount?) {
        if (new?.url != account?.url || new?.keyAlias != account?.keyAlias || new?.fingerprint != account?.fingerprint) connected = null
        account = new
        // In order: two saves on background jobs could land the older one last. (Preferences write to disk on their own.)
        store.saveAccount(new)
    }

    // Both take the account as it is now, not as the caller found it: a call that started before serverInfo answered
    // would otherwise show the account without what it said (its version, its stories).
    private fun reachable(caller: ServerAccount) {
        val current = account ?: return
        if (current.url != caller.url) return
        _state.value = connectedState(current, ServerFeed.lastRead(rows), null)
    }

    private fun unreachable(caller: ServerAccount) {
        val current = account ?: return
        if (current.url != caller.url) return
        val since = (_state.value as? ServerState.Connected)?.unreachableSince ?: clock()
        _state.value = connectedState(current, ServerFeed.lastRead(rows), since)
    }

    private fun connectedState(account: ServerAccount, lastRead: Instant?, unreachableSince: Instant?): ServerState.Connected {
        if (lastRead != null) this.lastRead = lastRead
        return ServerState.Connected(
            address = displayAddress(account.url),
            user = account.user,
            deviceName = account.deviceName,
            version = account.version,
            storiesOn = account.stories,
            lastRead = this.lastRead,
            addressCount = account.addresses.size,
            dhtFoundAt = account.dhtFoundAt,
            certificateExpires = account.certificateExpires,
            unreachableSince = unreachableSince,
        )
    }

    companion object {
        /** The Keystore aliases the phone's key alternates between, one per renewal. */
        val KEY_ALIASES = listOf("news-server-a", "news-server-b")

        /** A certificate is renewed in its last 30 days, as the server allows. */
        val RENEW_BEFORE = 30.days
    }
}

/** What went wrong, as the server screen tells it. */
internal fun errorOf(e: Throwable): ServerError {
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is WrongServerKeyException) return ServerError.WRONG_KEY
        if (cause is IOException || cause.javaClass.name.startsWith("com.apollographql.apollo.exception.ApolloNetwork")) return ServerError.UNREACHABLE
        cause = cause.cause
    }
    return if (e is com.apollographql.apollo.exception.ApolloException) ServerError.UNREACHABLE else ServerError.FAILED
}
