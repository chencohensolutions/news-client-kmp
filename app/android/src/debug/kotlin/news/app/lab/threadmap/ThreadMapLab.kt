package news.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import news.app.server.Sentence
import news.app.server.ServerThread
import news.app.server.ThreadRef
import news.app.server.ThreadStory
import kotlin.time.Instant

/** The threads map with the news.db snapshot ([sampleMapThreads]); opening a story or a thread does nothing. */
@Composable
internal fun ThreadMapLab() {
    var view by remember { mutableStateOf(ThreadMapView.MAP) }
    val threads = remember {
        sampleMapThreads.map { t ->
            val own = sampleMapStories.filter { t.id in it.threads }
            ServerThread(
                ThreadRef(t.id, t.name, own.size, Instant.fromEpochMilliseconds(own.minOf { it.at }), Instant.fromEpochMilliseconds(own.maxOf { it.lastAt }), t.summary),
                t.summary,
                own.map { s ->
                    ThreadStory(
                        s.id, s.title, listOf(Sentence(s.text, emptyList())), Instant.fromEpochMilliseconds(s.at), Instant.fromEpochMilliseconds(s.lastAt),
                        Instant.fromEpochMilliseconds(s.at), Instant.fromEpochMilliseconds(s.lastAt),
                    )
                },
                t.related.map { ThreadRef(it, "", 0, Instant.DISTANT_PAST, Instant.DISTANT_PAST) },
            )
        }
    }
    ThreadMapScreen(threads, view, { view = it }, onStory = { _, _ -> }, onThread = {}, onBack = {})
}
