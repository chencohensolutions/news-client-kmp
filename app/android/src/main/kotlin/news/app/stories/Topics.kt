package news.app.stories

import news.core.model.ArticleSummary
import kotlin.math.roundToInt

/** The topic a headline is closest to among those on, and how close on the topic slider's scale ([wordScores]). */
data class TopicMatch(val topic: String, val score: Int)

/**
 * How much of [topic] each of [items] mentions, 0 to 100 by url: the share of the topic's words found in the headline's
 * title, subtitle or summary (a word also matches with a one-letter prefix, as Hebrew joins "and", "the", "in" to the
 * word). Headlines that mention none are left out. The phone has no meaning model: the server groups and summarizes.
 */
fun wordScores(topic: String, items: List<ArticleSummary>): Map<String, Int> {
    val words = words(topic).distinct()
    if (words.isEmpty()) return emptyMap()
    val scores = HashMap<String, Int>()
    for (item in items) {
        val text = words(listOfNotNull(item.title, item.subtitle, item.summary).joinToString(" ")).toHashSet()
        val found = words.count { w -> w in text || text.any { it.length == w.length + 1 && it.endsWith(w) } }
        if (found > 0) scores[item.url] = (found * 100f / words.size).roundToInt()
    }
    return scores
}

private fun words(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }

/**
 * Each headline's best topic among [on] whose score ([scores]: topic → url → score) reaches [threshold], by url; a
 * headline no topic on reaches is left out.
 */
fun topicMatches(scores: Map<String, Map<String, Int>>, on: Collection<String>, threshold: Int): Map<String, TopicMatch> {
    val best = HashMap<String, TopicMatch>()
    for (topic in on) {
        for ((url, score) in scores[topic].orEmpty()) {
            if (score < threshold) continue
            val now = best[url]
            if (now == null || score > now.score) best[url] = TopicMatch(topic, score)
        }
    }
    return best
}
