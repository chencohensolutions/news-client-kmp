package news.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One entry on a site's headline list page. */
@Serializable
data class ArticleSummary(
    val sourceId: String,
    val url: String,
    val title: String,
    val summary: String? = null,
    val imageUrl: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val publishedAt: Instant? = null,
    /** One of [Categories.IDS], or null when the site gives none or it maps to none. */
    val category: String? = null,
    /**
     * The article's subtitle, or its first paragraph when it has none. Filled in by the app from the article page
     * ([withSubtitleFrom]); list pages never set it. Stories are grouped by it.
     */
    val subtitle: String? = null,
    /** Whether the article page has a video ([Article.video]). Filled in by the app from the article page, like [subtitle]. */
    val video: Boolean = false,
) {
    /** This headline with its [subtitle] taken from [article] (its subtitle, else its first paragraph), if it has none. */
    fun withSubtitleFrom(article: Article): ArticleSummary =
        if (subtitle != null) this else copy(subtitle = article.subtitle?.takeIf { it.isNotBlank() } ?: article.body.firstOrNull { it.isNotBlank() })
}

/**
 * A full article, as the server sends it. [body] holds the text paragraphs in reading order. When the source reads in-article
 * photos, [blocks] holds the same paragraphs with the photos between them, in reading order; otherwise it is empty.
 */
@Serializable
data class Article(
    val sourceId: String,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val author: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val publishedAt: Instant? = null,
    val imageUrl: String? = null,
    /** One of [Categories.IDS], from the page's own section name; null when the page gives none or it maps to none. */
    val category: String? = null,
    val body: List<String> = emptyList(),
    val blocks: List<ArticleBlock> = emptyList(),
    val video: ArticleVideo? = null,
)

/** One piece of an article's body, in reading order. Serialized with a `type` of `text` or `image`. */
@Serializable
sealed interface ArticleBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ArticleBlock

    @Serializable
    @SerialName("image")
    data class Image(val url: String, val caption: String? = null) : ArticleBlock
}

/**
 * The article's main video. [stream] is a file or HLS playlist a player can open directly; [embed] is the site's
 * own player page, for videos the site only plays there. At least one of the two is set.
 */
@Serializable
data class ArticleVideo(
    val poster: String? = null,
    val stream: String? = null,
    val embed: String? = null,
    /** As the page gives it, e.g. ISO-8601 "PT1M45S". */
    val duration: String? = null,
)
