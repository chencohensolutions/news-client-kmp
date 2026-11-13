package news.app.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import news.app.AppLayout
import news.app.WhatsNewCState
import news.app.WhatsNewCardC
import news.app.WhatsNewSample
import news.app.WhatsNewScreenC

/** Which sentence of the sample's latest version each update wrote. */
private fun sampleC(lastSeenMinutes: Int? = 60) = WhatsNewSample.state(lastSeenMinutes).let { page ->
    val u = WhatsNewSample.updates
    WhatsNewCState(page, mapOf(3 to u[0], 5 to u[1], 2 to u[2], 4 to u[3], 1 to u[5]))
}

/**
 * c — a "חדש:" line on the feed card, and side bars on the page. Fakes: the updates and their kinds, which sentence
 * each wrote, and the last visit (an hour ago). The feed here is just this card and one plain one; a tap on the
 * updated card opens its page, back returns.
 */
internal val whatsNewOptionC = LabOption("c", "c: card line + side bars", "Feed card: עודכן + the latest development; page: new sentences on a side bar") {
    var open by rememberSaveable { mutableStateOf(false) }
    val state = sampleC()
    if (open) {
        WhatsNewScreenC(state, WhatsNewSample.names, onFootnote = {}, onBack = { open = false })
    } else {
        FeedC(state) { open = true }
    }
}

/** c's page straight away, for its screenshot. */
internal val whatsNewOptionCPage = LabOption("c-page", "c: the page", "The page alone: new sentences on a side bar") {
    WhatsNewScreenC(sampleC(), WhatsNewSample.names, onFootnote = {}, onBack = {})
}

@Composable
private fun FeedC(state: WhatsNewCState, onOpen: () -> Unit) {
    AppLayout {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("חדשות", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                WhatsNewCardC(state, WhatsNewSample.names, onOpen)
                // A story written once, for contrast: no badge, its first sentence.
                val quiet = sampleC(lastSeenMinutes = 0)
                val other = quiet.page.summary.copy(
                    title = "גל חום: עד 38 מעלות בסוף השבוע",
                    sentences = listOf(news.app.server.Sentence("טמפרטורות של עד 38 מעלות צפויות בסוף השבוע, והשירות המטאורולוגי ממליץ להימנע ממאמץ בצהריים.", emptyList())),
                )
                WhatsNewCardC(quiet.copy(page = quiet.page.copy(summary = other), sentenceUpdates = emptyMap()), WhatsNewSample.names) {}
            }
        }
    }
}
