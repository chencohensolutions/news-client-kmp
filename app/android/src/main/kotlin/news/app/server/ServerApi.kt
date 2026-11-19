package news.app.server

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import news.app.server.graphql.ArticlesQuery
import news.app.server.graphql.EnrollDeviceMutation
import news.app.server.graphql.EnrollmentPreviewQuery
import news.app.server.graphql.HeadlinesQuery
import news.app.server.graphql.RenewCertificateMutation
import news.app.server.graphql.ServerInfoQuery
import news.app.server.graphql.ThreadQuery
import news.app.server.graphql.FollowingQuery
import news.app.server.graphql.FollowStoryMutation
import news.app.server.graphql.ThreadsQuery
import news.app.server.graphql.ThreadsMapQuery
import news.app.server.graphql.SummarySettingsQuery
import news.app.server.graphql.SetSummarySettingsMutation
import news.app.server.graphql.fragment.ServerThreadRef
import news.app.server.graphql.GroupingQuery
import news.app.server.graphql.SetGroupingMutation
import news.app.server.graphql.type.GroupingMethod
import news.app.server.graphql.SetSourceMutation
import news.app.server.graphql.fragment.ServerGroupingFields
import news.app.server.graphql.fragment.ServerSentence
import news.app.server.graphql.SourcesQuery
import news.app.server.graphql.StatsQuery
import news.app.server.graphql.CreateInviteMutation
import news.app.server.graphql.MarkReadMutation
import news.app.server.graphql.MeQuery
import news.app.server.graphql.ReadArticlesQuery
import news.app.server.graphql.RemoveUserMutation
import news.app.server.graphql.RevokeDeviceMutation
import news.app.server.graphql.SetSourceHiddenMutation
import news.app.server.graphql.SetUserRoleMutation
import news.app.server.graphql.UsersQuery
import news.app.server.graphql.VideoStreamQuery
import news.app.server.graphql.fragment.ServerUser as ServerUserFields
import news.app.server.graphql.type.Role as GraphqlRole
import news.app.server.graphql.fragment.ServerSourceFields
import news.app.server.graphql.StoriesQuery
import news.app.server.graphql.StoryVersionsQuery
import news.app.server.graphql.fragment.ServerStorySummary
import news.app.server.graphql.fragment.Enrollment
import news.app.server.graphql.fragment.ServerArticle
import news.app.server.graphql.type.EnrollDeviceInput
import news.app.server.graphql.type.EnrollmentRejectedReason
import news.app.server.graphql.type.KeyProtection
import news.core.model.Article
import news.core.model.ArticleBlock
import news.core.model.ArticleSummary
import news.core.model.ArticleVideo
import okhttp3.OkHttpClient
import kotlin.time.Instant
import news.app.server.graphql.fragment.ServerHeadline as HeadlineFields

/**
 * A site the server knows (`sources`) and how it reads it: [enabled], from its RSS feed ([feed], only when it [hasFeed])
 * and the wait between two of its requests ([waitMs]). Changed with `setSource`.
 */
data class ServerSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val language: String,
    val enabled: Boolean = true,
    val hasFeed: Boolean = false,
    val feed: Boolean = false,
    val waitMs: Int = 250,
    /** This user hid it from their feed ([ServerApi.setSourceHidden]): their choice, for all their devices. */
    val hidden: Boolean = false,
)

/** A user's role on the server: an admin changes what it does for everyone and manages its people; a user reads. */
enum class UserRole { ADMIN, USER }

/** Who this device is on the server (`me`). */
data class Me(val user: String, val role: UserRole, val deviceName: String?)

/** A user of the server and their devices, oldest first, revoked ones too (`users`, admins only). */
data class ServerUser(val name: String, val role: UserRole, val devices: List<ServerDevice>)

/** One enrolled device: its certificate's [serial], when it enrolled and expires, and whether it was revoked. */
data class ServerDevice(
    val serial: String,
    val name: String,
    val keyProtection: String,
    val registeredAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    /** The device asking: this phone. */
    val current: Boolean = false,
)

/** A one-time enrollment code for [user]'s next device, and the `news://enroll` link its QR code holds. */
data class Invite(val code: String, val user: String, val role: UserRole, val expiresAt: Instant, val link: String)

/** An article this user read, on any of their devices, and when the server heard of it. */
data class ReadMark(val url: String, val readAt: Instant)

/** An article's video on the server: its full [url] there, played with the phone's certificate; HLS when [hls]. */
data class ServerVideo(val url: String, val hls: Boolean)

/** An article's video as the server relays it: [path] on the server, an HLS playlist when [hls], else an MP4. */
data class VideoPath(val path: String, val hls: Boolean)

/** What the server says it can do (serverInfo). */
data class ServerInfo(
    val api: Int,
    val version: String,
    val renewCertificates: Boolean,
    val stories: Boolean,
    val summaries: Boolean = false,
    val discovery: ServerDiscovery? = null,
)

/** Who the server is and where it is now (serverInfo.discovery): its key's fingerprint, id, DHT key and addresses. */
data class ServerDiscovery(val fingerprint: String, val id: String, val key: String?, val addresses: List<String>)

/** An article a generated sentence comes from; [sourceId] and [title] are null when the server no longer has its headline. */
@kotlinx.serialization.Serializable
data class Footnote(
    val url: String,
    val sourceId: String? = null,
    val title: String? = null,
    val video: Boolean = false,
    /** When the site published it (or the server first saw it); null when not known. */
    @kotlinx.serialization.Serializable(with = news.core.model.InstantIsoSerializer::class) val publishedAt: Instant? = null,
)

/** A generated sentence and the articles it comes from (at least one). */
@kotlinx.serialization.Serializable
data class Sentence(
    val text: String,
    val footnotes: List<Footnote>,
    /** A contradiction's fact in dispute ("מספר הפצועים"); null for other sentences and older contradictions. */
    val topic: String? = null,
    /** A contradiction's versions, each with its articles; empty for other sentences and older contradictions. */
    val sides: List<ContradictionSide> = emptyList(),
)

/** One version of a contradiction's fact ("8", "פיגוע דריסה") and the articles that state it. */
@kotlinx.serialization.Serializable
data class ContradictionSide(val value: String, val footnotes: List<Footnote>)

/**
 * A story's generated article (Gemini, on the server): facts only, each once, every sentence with its footnotes; and
 * where the sites state different facts, a sentence each saying which says what ([contradictions], usually none).
 */
@kotlinx.serialization.Serializable
data class StorySummary(
    val title: String,
    val sentences: List<Sentence>,
    val contradictions: List<Sentence> = emptyList(),
    @kotlinx.serialization.Serializable(with = news.core.model.InstantIsoSerializer::class) val writtenAt: Instant,
    /** The story's reports about a different event (Gemini's check): kept out of the article and its footnotes. */
    val unrelated: List<String> = emptyList(),
    /** The story's id on the server, kept by its later versions; "" from an older server. */
    val id: String = "",
    /** The long affairs (threads) the story is part of, latest updated first. */
    val threads: List<ThreadRef> = emptyList(),
    /** The settings it was written with; null for a story written before the server kept them. */
    val writtenWith: WrittenWith? = null,
    /** The URLs of the reports it was written from (Gemini read them); empty for a story written before they were kept. */
    val reports: List<String> = emptyList(),
    /** Of [reports], those read only by title and subtitle (a paywall stub, or no article). */
    val headlineOnly: List<String> = emptyList(),
    /** Which version of the story this is, 1 for the first, of [versions] so far ([ServerApi.storyVersions]). */
    val version: Int = 1,
    val versions: Int = 1,
    /**
     * Written from the story's reports as they are. False while reports that joined it wait for its next version on the
     * server: this is the version before, and a new one follows.
     */
    val current: Boolean = true,
    /** What it changed from the version before; null for a first version, or from an older server. */
    val changes: StoryChanges? = null,
)

/** What a version of a story changed from the version before it ([since]; its title was [title]). */
@kotlinx.serialization.Serializable
data class StoryChanges(
    @kotlinx.serialization.Serializable(with = news.core.model.InstantIsoSerializer::class) val since: Instant,
    val title: String,
    /** One for each of the version's sentences, in order. */
    val sentences: List<SentenceChange>,
    /** The version before's sentences this one has nothing like. */
    val removed: List<Sentence> = emptyList(),
)

/** How a sentence differs from the closest one before: its text in [parts], the [TextPart.added] ones new. */
@kotlinx.serialization.Serializable
data class SentenceChange(val kind: ChangeKind, val parts: List<TextPart>)

@kotlinx.serialization.Serializable
enum class ChangeKind { SAME, CHANGED, NEW }

@kotlinx.serialization.Serializable
data class TextPart(val text: String, val added: Boolean)

/**
 * How often the server writes a story again: at most every [minutes] (0: at every change), at once when a new site
 * joins ([onNewSite]); past [reserveAfter] requests a day (0: off), only new stories are written.
 */
data class Rewrite(val minutes: Int, val onNewSite: Boolean, val reserveAfter: Int)

/** How a story was written: from the full articles or their first 600 characters, and at which length. */
@kotlinx.serialization.Serializable
data class WrittenWith(val fullArticles: Boolean, val length: String)

/** A thread as a card or another thread names it: an affair running over months, by Gemini's linking on the server. */
@kotlinx.serialization.Serializable
data class ThreadRef(
    val id: String,
    val name: String,
    val storyCount: Int,
    @kotlinx.serialization.Serializable(with = news.core.model.InstantIsoSerializer::class) val startedAt: Instant,
    @kotlinx.serialization.Serializable(with = news.core.model.InstantIsoSerializer::class) val updatedAt: Instant,
    /** The story so far ("עד כה"); only in the threads list, else "". */
    val summary: String = "",
)

/**
 * A story this user follows: its latest title, its threads, and its short [updates] since it was followed, newest
 * first: the new facts of its later versions and the stories that joined its threads.
 */
data class FollowedStory(
    val id: String,
    val title: String,
    val followedAt: Instant,
    val threads: List<ThreadRef>,
    val updates: List<FollowUpdate>,
)

/** A short update on a followed story, about it or about a story of its threads ([newStory]: one that joined them). */
data class FollowUpdate(
    val at: Instant,
    val storyId: String,
    val storyTitle: String,
    val newStory: Boolean,
    val sentences: List<Sentence>,
)

/** A thread's page: the story so far, its stories oldest first, and the related threads. */
data class ServerThread(val ref: ThreadRef, val summary: String, val stories: List<ThreadStory>, val related: List<ThreadRef>)

/** A story in a thread, its latest version; [firstWrittenAt] places it in the thread. */
data class ThreadStory(
    val id: String,
    val title: String,
    val sentences: List<Sentence>,
    val firstWrittenAt: Instant,
    val writtenAt: Instant,
    /** Its earliest and latest reports' publishing times: the event's span; null when not known. */
    val firstReportAt: Instant? = null,
    val lastReportAt: Instant? = null,
)

/** What the server has done (`stats`): its reads of the sites, what it keeps, its use of Gemini. */
data class ServerStats(
    val headlines: Int,
    val articles: Int,
    val stories: Int,
    val groupedAt: Instant?,
    val summaries: Int,
    val gemini: GeminiStats?,
    val sites: List<SiteStats>,
    val lastLoop: LoopStats? = null,
)

/** The server's latest round: read the sites, group the stories, write the summaries; seconds each, null when off or still running. */
data class LoopStats(
    val startedAt: Instant,
    val everyMinutes: Int,
    val readSeconds: Double,
    val headlines: Int,
    val newArticles: Int,
    val failedSites: Int,
    val groupSeconds: Double?,
    val summarySeconds: Double?,
    val totalSeconds: Double,
)

/** Gemini against the free tier: [budget] requests a day, counted by Google's day [today] (Pacific, YYYY-MM-DD). */
data class GeminiStats(val model: String, val budget: Int, val today: String, val days: List<GeminiDay>, val lastRun: SummaryRun?)

/** One day's requests and tokens: [tokensIn] includes [tokensCached]; [tokensThinking] counts as output. */
data class GeminiDay(val day: String, val requests: Int, val tokensIn: Int, val tokensCached: Int, val tokensOut: Int, val tokensThinking: Int)

data class SummaryRun(val at: Instant, val written: Int, val waiting: Int, val error: String?)

data class SiteStats(val sourceId: String, val enabled: Boolean, val lastRunAt: Instant?, val lastOkAt: Instant?, val headlines: Int, val error: String?)

/** How the server groups stories (`grouping`): the app's grouping settings, kept on the server, and its model. */
data class ServerGrouping(
    val model: String,
    val text: news.app.stories.StoryText,
    val strictness: Int,
    val days: Int,
    val threshold: Double,
    /** Gemini groups them (each round placing the new headlines), not the model; the model stands in when it fails. */
    val byGemini: Boolean = false,
    /** Whether Gemini can group them: the server has a Gemini key. */
    val geminiAvailable: Boolean = false,
)

/** A story as the server grouped it: its reports' URLs and, once written, its generated article. */
data class ServerStory(val urls: List<String>, val summary: StorySummary? = null)

/** enrollmentPreview's answer: whose code it is, until when; or why it was turned down. */
sealed interface PreviewResult {
    data class Valid(val user: String, val expiresAt: Instant) : PreviewResult
    data class Rejected(val reason: EnrollmentRejectedReason) : PreviewResult
}

/** enrollDevice's or renewCertificate's answer: the certificate (DER) for the key, or why not. */
sealed interface EnrollResult {
    data class Enrolled(val user: String, val deviceName: String, val certificate: ByteArray, val expiresAt: Instant) : EnrollResult
    data class Rejected(val reason: EnrollmentRejectedReason) : EnrollResult
}

/**
 * The news server's GraphQL API (news-server-go, graph/schema), as the app uses it. A network failure, a refused key
 * or an error answer throws ([ServerException] for the latter).
 */
interface ServerApi {
    suspend fun preview(code: String): PreviewResult
    suspend fun enroll(code: String, deviceName: String, csr: ByteArray, protection: KeyProtection): EnrollResult
    suspend fun renew(csr: ByteArray, protection: KeyProtection): EnrollResult
    suspend fun info(): ServerInfo

    /** The headlines seen at or after [since] (all kept when null). */
    suspend fun headlines(since: Instant?): List<ServerHeadline>

    /** The articles of [urls] the server has (at most [MAX_ARTICLES]). */
    suspend fun articles(urls: List<String>): List<Article>

    /** The stories, each its reports' URLs and generated article; empty before the first grouping or with stories off. */
    suspend fun stories(): List<ServerStory>

    /** Every version of story [id] ([StorySummary.id]), newest first; empty when the server has none. */
    suspend fun storyVersions(id: String): List<StorySummary> = emptyList()

    /** Whether Gemini reads the full articles for the summaries; null with summaries off. */
    suspend fun fullArticles(): Boolean? = null

    /** How often a story is written again, and the day's reserve; null with summaries off. */
    suspend fun rewrite(): Rewrite? = null

    /** Changes [rewrite] (null fields keep their value); the settings as the server keeps them now. */
    suspend fun setRewrite(minutes: Int? = null, onNewSite: Boolean? = null, reserveAfter: Int? = null): Rewrite = Rewrite(30, false, 300)

    /** How long the generated stories are (SHORT, MEDIUM, LONG, FULL); null with summaries off. */
    suspend fun storyLength(): String? = null

    /** Changes how long the generated stories are; the length as the server keeps it now. */
    suspend fun setStoryLength(length: String): String = length

    /** Changes whether Gemini reads the full articles; the value as the server keeps it now. */
    suspend fun setFullArticles(on: Boolean): Boolean = on

    /** One thread with its stories; null when there is none with [id]. */
    suspend fun thread(id: String): ServerThread? = null

    /** The stories this user follows, latest followed first, with their updates. */
    suspend fun following(): List<FollowedStory> = emptyList()

    /** Follows story [id], or stops ([follow] false); whether it is followed now. */
    suspend fun followStory(id: String, follow: Boolean): Boolean = follow

    /** The threads, latest updated first, at most [first]. */
    suspend fun threads(first: Int): List<ThreadRef> = emptyList()

    /** Every thread with its stories (their sentences without footnotes) and related threads (only their ids): the map. */
    suspend fun threadsMap(first: Int): List<ServerThread> = emptyList()

    /** What the server has done: the sites, what it keeps, Gemini's use. */
    suspend fun stats(): ServerStats

    /** How the server groups stories; null with stories off. */
    suspend fun grouping(): ServerGrouping?

    /** Changes how the server groups stories (null fields keep their value); it regroups at once. */
    suspend fun setGrouping(strictness: Int? = null, text: news.app.stories.StoryText? = null, days: Int? = null, byGemini: Boolean? = null): ServerGrouping

    /** The sites the server knows, by id, switched-off ones too. */
    suspend fun sources(): List<ServerSource>

    /** Changes one site's settings on the server; null fields keep their value. Its settings as they are now. */
    suspend fun setSource(id: String, enabled: Boolean? = null, feed: Boolean? = null, waitMs: Int? = null): ServerSource

    /** Who this device is: its user and their role. */
    suspend fun me(): Me = Me("", UserRole.ADMIN, null)

    /** Hides a site from this user's feed, or shows it again; the site as it is now. */
    suspend fun setSourceHidden(id: String, hidden: Boolean): ServerSource = throw UnsupportedOperationException()

    /** Marks [urls] read for this user (at most [MAX_MARK_READ]); how many. */
    suspend fun markRead(urls: List<String>): Int = urls.size

    /** The articles this user read since [since] (all when null), from any of their devices. */
    suspend fun readArticles(since: Instant?): List<ReadMark> = emptyList()

    /** [articleUrl]'s video as the server relays it; null when it has none it can relay. */
    suspend fun videoStream(articleUrl: String): VideoPath? = null

    // Admins only: anyone else gets FORBIDDEN.
    suspend fun users(): List<ServerUser> = emptyList()
    suspend fun createInvite(user: String, role: UserRole): Invite = throw UnsupportedOperationException()
    suspend fun setUserRole(user: String, role: UserRole): ServerUser = throw UnsupportedOperationException()
    suspend fun revokeDevice(serial: String): Boolean = throw UnsupportedOperationException()
    suspend fun removeUser(user: String): Boolean = throw UnsupportedOperationException()

    companion object {
        /** The most URLs `articles` takes at once. */
        const val MAX_ARTICLES = 200

        /** The most URLs `markRead` takes at once. */
        const val MAX_MARK_READ = 500
    }
}

/** The server answered with an error ([code]: its extensions.code, like UNAUTHORIZED). */
class ServerException(message: String, val code: String?) : Exception(message)

/** [ServerApi] over Apollo and OkHttp: [http] carries the TLS choices ([serverHttpClient]). */
class ApolloServerApi(url: String, http: OkHttpClient) : ServerApi {

    private val apollo = ApolloClient.Builder().serverUrl(graphqlUrl(url)).okHttpClient(http).build()

    private suspend fun <D : Operation.Data> run(call: suspend ApolloClient.() -> ApolloResponse<D>): D = withContext(Dispatchers.IO) {
        val response = apollo.call()
        response.exception?.let { throw it }
        response.errors?.firstOrNull()?.let { error ->
            throw ServerException(error.message, error.extensions?.get("code") as? String)
        }
        response.data ?: throw ServerException("No answer", null)
    }

    override suspend fun preview(code: String): PreviewResult {
        val answer = run { query(EnrollmentPreviewQuery(code)).execute() }.enrollmentPreview
        answer.onEnrollmentPreview?.let { return PreviewResult.Valid(it.user, Instant.parse(it.expiresAt)) }
        return PreviewResult.Rejected(answer.onEnrollmentRejected?.reason ?: EnrollmentRejectedReason.UNKNOWN__)
    }

    override suspend fun enroll(code: String, deviceName: String, csr: ByteArray, protection: KeyProtection): EnrollResult =
        run { mutation(EnrollDeviceMutation(EnrollDeviceInput(code, deviceName, base64(csr), protection))).execute() }
            .enrollDevice.enrollment.toResult()

    override suspend fun renew(csr: ByteArray, protection: KeyProtection): EnrollResult =
        run { mutation(RenewCertificateMutation(base64(csr), protection)).execute() }.renewCertificate.enrollment.toResult()

    override suspend fun info(): ServerInfo =
        run { query(ServerInfoQuery()).execute() }.serverInfo.let { info ->
            ServerInfo(
                info.api, info.version, info.renewCertificates, info.stories, info.summaries,
                info.discovery?.let { ServerDiscovery(it.fingerprint, it.id, it.key, it.addresses) },
            )
        }

    override suspend fun headlines(since: Instant?): List<ServerHeadline> =
        run { query(HeadlinesQuery(since = Optional.presentIfNotNull(since?.toString()))).execute() }
            .headlines.map { it.serverHeadline.toServerHeadline() }

    override suspend fun articles(urls: List<String>): List<Article> =
        run { query(ArticlesQuery(urls.take(ServerApi.MAX_ARTICLES))).execute() }.articles.map { it.serverArticle.toArticle() }

    override suspend fun stories(): List<ServerStory> =
        run { query(StoriesQuery()).execute() }.stories.stories.map { story ->
            ServerStory(
                urls = story.reports.map { it.url },
                summary = story.summary?.let { s -> s.serverStorySummary.toStorySummary().copy(threads = s.threads.map { it.serverThreadRef.toRef() }) },
            )
        }

    override suspend fun storyVersions(id: String): List<StorySummary> =
        run { query(StoryVersionsQuery(id)).execute() }.storyVersions.map { it.serverStorySummary.toStorySummary() }

    private fun ServerStorySummary.toStorySummary() = StorySummary(
        title,
        sentences.map { it.serverSentence.toSentence() },
        contradictions.map { it.serverSentence.toSentence() },
        Instant.parse(writtenAt),
        unrelated,
        id,
        emptyList(),
        writtenWith?.let { WrittenWith(it.fullArticles, it.length.rawValue) },
        reports,
        headlineOnly,
        version = version,
        versions = versions,
        current = current,
        changes = changes?.let { c ->
            StoryChanges(
                Instant.parse(c.since),
                c.title,
                c.sentences.map { sc ->
                    SentenceChange(
                        when (sc.kind.rawValue) { "SAME" -> ChangeKind.SAME; "CHANGED" -> ChangeKind.CHANGED; else -> ChangeKind.NEW },
                        sc.parts.map { TextPart(it.text, it.added) },
                    )
                },
                c.removed.map { it.serverSentence.toSentence() },
            )
        },
    )

    override suspend fun fullArticles(): Boolean? = run { query(SummarySettingsQuery()).execute() }.summarySettings?.fullArticles

    override suspend fun setFullArticles(on: Boolean): Boolean =
        run { mutation(SetSummarySettingsMutation(fullArticles = Optional.present(on))).execute() }.setSummarySettings.fullArticles

    override suspend fun rewrite(): Rewrite? =
        run { query(SummarySettingsQuery()).execute() }.summarySettings?.let { Rewrite(it.rewriteMinutes, it.rewriteOnNewSite, it.reserveAfter) }

    override suspend fun setRewrite(minutes: Int?, onNewSite: Boolean?, reserveAfter: Int?): Rewrite =
        run {
            mutation(
                SetSummarySettingsMutation(
                    rewriteMinutes = Optional.presentIfNotNull(minutes),
                    rewriteOnNewSite = Optional.presentIfNotNull(onNewSite),
                    reserveAfter = Optional.presentIfNotNull(reserveAfter),
                ),
            ).execute()
        }.setSummarySettings.let { Rewrite(it.rewriteMinutes, it.rewriteOnNewSite, it.reserveAfter) }

    override suspend fun storyLength(): String? = run { query(SummarySettingsQuery()).execute() }.summarySettings?.length?.rawValue

    override suspend fun setStoryLength(length: String): String =
        run { mutation(SetSummarySettingsMutation(length = Optional.present(news.app.server.graphql.type.StoryLength.safeValueOf(length)))).execute() }
            .setSummarySettings.length.rawValue

    override suspend fun threads(first: Int): List<ThreadRef> =
        run { query(ThreadsQuery(Optional.present(first))).execute() }.threads.map { it.serverThreadRef.toRef().copy(summary = it.summary) }

    override suspend fun threadsMap(first: Int): List<ServerThread> =
        run { query(ThreadsMapQuery(Optional.present(first))).execute() }.threads.map { t ->
            ServerThread(
                t.serverThreadRef.toRef().copy(summary = t.summary),
                t.summary,
                t.stories.map { s ->
                    ThreadStory(
                        s.id, s.title, s.sentences.map { Sentence(it.text, emptyList()) }, Instant.parse(s.firstWrittenAt), Instant.parse(s.writtenAt),
                        s.firstReportAt?.let { Instant.parse(it.toString()) }, s.lastReportAt?.let { Instant.parse(it.toString()) },
                    )
                },
                t.related.map { ThreadRef(it.id, "", 0, Instant.DISTANT_PAST, Instant.DISTANT_PAST) },
            )
        }

    override suspend fun following(): List<FollowedStory> =
        run { query(FollowingQuery()).execute() }.following.map { f ->
            FollowedStory(
                f.id, f.title, Instant.parse(f.followedAt), f.threads.map { it.serverThreadRef.toRef() },
                f.updates.map { u -> FollowUpdate(Instant.parse(u.at), u.storyId, u.storyTitle, u.newStory, u.sentences.map { it.serverSentence.toSentence() }) },
            )
        }

    override suspend fun followStory(id: String, follow: Boolean): Boolean =
        run { mutation(FollowStoryMutation(id, follow)).execute() }.followStory

    override suspend fun thread(id: String): ServerThread? =
        run { query(ThreadQuery(id)).execute() }.thread?.let { t ->
            ServerThread(
                t.serverThreadRef.toRef(),
                t.summary,
                t.stories.map { s ->
                    ThreadStory(
                        s.id, s.title, s.sentences.map { it.serverSentence.toSentence() }, Instant.parse(s.firstWrittenAt), Instant.parse(s.writtenAt),
                        s.firstReportAt?.let { Instant.parse(it.toString()) }, s.lastReportAt?.let { Instant.parse(it.toString()) },
                    )
                },
                t.related.map { it.serverThreadRef.toRef() },
            )
        }

    private fun ServerThreadRef.toRef() = ThreadRef(id, name, storyCount, Instant.parse(startedAt), Instant.parse(updatedAt))

    override suspend fun stats(): ServerStats = run { query(StatsQuery()).execute() }.stats.let { s ->
        ServerStats(
            headlines = s.headlines,
            articles = s.articles,
            stories = s.stories,
            groupedAt = s.groupedAt?.let(::instantOrNull),
            summaries = s.summaries,
            gemini = s.gemini?.let { g ->
                GeminiStats(
                    g.model, g.budget, g.today,
                    g.days.map { GeminiDay(it.day, it.requests, it.tokensIn, it.tokensCached, it.tokensOut, it.tokensThinking) },
                    g.lastRun?.let { r -> instantOrNull(r.at)?.let { SummaryRun(it, r.written, r.waiting, r.error) } },
                )
            },
            sites = s.sites.map {
                SiteStats(it.sourceId, it.enabled, it.lastRunAt?.let(::instantOrNull), it.lastOkAt?.let(::instantOrNull), it.headlines, it.error)
            },
            lastLoop = s.lastLoop?.let { l ->
                instantOrNull(l.startedAt)?.let {
                    LoopStats(it, l.everyMinutes, l.readSeconds, l.headlines, l.newArticles, l.failedSites, l.groupSeconds, l.summarySeconds, l.totalSeconds)
                }
            },
        )
    }

    override suspend fun grouping(): ServerGrouping? = run { query(GroupingQuery()).execute() }.grouping?.serverGroupingFields?.toGrouping()

    override suspend fun setGrouping(strictness: Int?, text: news.app.stories.StoryText?, days: Int?, byGemini: Boolean?): ServerGrouping =
        run {
            mutation(
                SetGroupingMutation(
                    Optional.presentIfNotNull(strictness),
                    Optional.presentIfNotNull(text?.let { news.app.server.graphql.type.StoryText.safeValueOf(it.name) }),
                    Optional.presentIfNotNull(days),
                    Optional.presentIfNotNull(byGemini?.let { if (it) GroupingMethod.GEMINI else GroupingMethod.MODEL }),
                ),
            ).execute()
        }.setGrouping.serverGroupingFields.toGrouping()

    private fun ServerGroupingFields.toGrouping() = ServerGrouping(
        model,
        runCatching { news.app.stories.StoryText.valueOf(text.rawValue) }.getOrDefault(news.app.stories.StoryText.TITLE_AND_SUBTITLE),
        strictness, days, threshold,
        byGemini = method == GroupingMethod.GEMINI,
        geminiAvailable = geminiAvailable,
    )

    private fun ServerSentence.toSentence() = Sentence(
        text,
        footnotes.map { Footnote(it.url, it.sourceId, it.title, it.video, instantOf(it.publishedAt)) },
        topic,
        sides.map { side -> ContradictionSide(side.value, side.footnotes.map { Footnote(it.url, it.sourceId, it.title, it.video, instantOf(it.publishedAt)) }) },
    )

    private fun instantOf(time: Any?): Instant? = time?.let { t -> runCatching { Instant.parse(t.toString()) }.getOrNull() }

    override suspend fun sources(): List<ServerSource> =
        run { query(SourcesQuery()).execute() }.sources.map { it.serverSourceFields.toServerSource() }

    override suspend fun setSource(id: String, enabled: Boolean?, feed: Boolean?, waitMs: Int?): ServerSource =
        run {
            mutation(
                SetSourceMutation(id, Optional.presentIfNotNull(enabled), Optional.presentIfNotNull(feed), Optional.presentIfNotNull(waitMs)),
            ).execute()
        }.setSource.serverSourceFields.toServerSource()

    override suspend fun setSourceHidden(id: String, hidden: Boolean): ServerSource =
        run { mutation(SetSourceHiddenMutation(id, hidden)).execute() }.setSourceHidden.serverSourceFields.toServerSource()

    private fun ServerSourceFields.toServerSource() = ServerSource(id, name, baseUrl, language, enabled, hasFeed, feed, waitMs, hidden)

    override suspend fun me(): Me = run { query(MeQuery()).execute() }.me.let { Me(it.user, it.role.toRole(), it.deviceName) }

    override suspend fun markRead(urls: List<String>): Int =
        run { mutation(MarkReadMutation(urls.take(ServerApi.MAX_MARK_READ))).execute() }.markRead

    override suspend fun readArticles(since: Instant?): List<ReadMark> =
        run { query(ReadArticlesQuery(Optional.presentIfNotNull(since?.toString()))).execute() }
            .readArticles.mapNotNull { r -> instantOrNull(r.readAt)?.let { ReadMark(r.url, it) } }

    override suspend fun videoStream(articleUrl: String): VideoPath? =
        run { query(VideoStreamQuery(articleUrl)).execute() }.videoStream?.let { VideoPath(it.path, it.hls) }

    override suspend fun users(): List<ServerUser> = run { query(UsersQuery()).execute() }.users.map { it.serverUser.toUser() }

    override suspend fun createInvite(user: String, role: UserRole): Invite =
        run { mutation(CreateInviteMutation(user, Optional.present(role.toGraphql()))).execute() }.createInvite.let {
            Invite(it.code, it.user, it.role.toRole(), Instant.parse(it.expiresAt), it.link)
        }

    override suspend fun setUserRole(user: String, role: UserRole): ServerUser =
        run { mutation(SetUserRoleMutation(user, role.toGraphql())).execute() }.setUserRole.serverUser.toUser()

    override suspend fun revokeDevice(serial: String): Boolean =
        run { mutation(RevokeDeviceMutation(serial)).execute() }.revokeDevice.revokedAt != null

    override suspend fun removeUser(user: String): Boolean = run { mutation(RemoveUserMutation(user)).execute() }.removeUser

    private fun ServerUserFields.toUser() = ServerUser(
        name, role.toRole(),
        devices.map { d ->
            ServerDevice(d.serial, d.name, d.keyProtection, Instant.parse(d.registeredAt), Instant.parse(d.expiresAt), d.revokedAt?.let(::instantOrNull), d.current)
        },
    )

    private fun GraphqlRole.toRole() = if (this == GraphqlRole.ADMIN) UserRole.ADMIN else UserRole.USER

    private fun UserRole.toGraphql() = if (this == UserRole.ADMIN) GraphqlRole.ADMIN else GraphqlRole.USER

    private fun Enrollment.toResult(): EnrollResult {
        onEnrolledDevice?.let {
            return EnrollResult.Enrolled(it.user, it.deviceName, java.util.Base64.getDecoder().decode(it.certificate), Instant.parse(it.expiresAt))
        }
        return EnrollResult.Rejected(onEnrollmentRejected?.reason ?: EnrollmentRejectedReason.UNKNOWN__)
    }

    private fun base64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)
}

/** A headline as the app keeps it. */
internal fun HeadlineFields.toServerHeadline() = ServerHeadline(
    summary = ArticleSummary(
        sourceId = sourceId,
        url = url,
        title = title,
        summary = summary,
        imageUrl = imageUrl,
        publishedAt = publishedAt?.let(::instantOrNull),
        category = category,
        subtitle = subtitle,
        video = video,
    ),
    firstSeen = Instant.parse(firstSeen),
    lastSeen = Instant.parse(lastSeen),
)

/** An article as the app shows it. */
internal fun ServerArticle.toArticle() = Article(
    sourceId = sourceId,
    url = url,
    title = title,
    subtitle = subtitle,
    author = author,
    publishedAt = publishedAt?.let(::instantOrNull),
    imageUrl = imageUrl,
    category = category,
    body = body,
    blocks = blocks.mapNotNull { block ->
        when (block.type) {
            "text" -> block.text?.let { ArticleBlock.Text(it) }
            "image" -> block.url?.let { ArticleBlock.Image(it, block.caption) }
            else -> null
        }
    },
    video = video?.let { ArticleVideo(it.poster, it.stream, it.embed, it.duration) },
)

private fun instantOrNull(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
