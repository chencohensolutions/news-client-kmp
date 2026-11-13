package news.app.lab

import news.app.WhatsNewSample
import news.app.WhatsNewScreenB

/**
 * b — the story page as a timeline of updates. Fakes: the updates, their kinds and versions (the incremental server
 * sends them; the app doesn't read them yet) and the last visit (an hour ago). Taps on updates do nothing.
 */
internal val whatsNewOptionB = LabOption("b", "b: timeline", "The updates newest first on a line, new ones marked; the full story collapsed below") {
    WhatsNewScreenB(WhatsNewSample.state(), WhatsNewSample.names, onFootnote = {}, onBack = {})
}
