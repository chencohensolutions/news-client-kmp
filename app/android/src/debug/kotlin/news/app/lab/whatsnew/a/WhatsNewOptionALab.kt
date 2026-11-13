package news.app.lab

import news.app.WhatsNewSample
import news.app.WhatsNewScreenA

/**
 * a — "מה חדש" block on top of the story page. Fakes: the updates and their kinds (the incremental server sends them;
 * the app doesn't read them yet) and when the reader last opened the story (an hour ago). Taps on lines do nothing.
 */
internal val whatsNewOptionA = LabOption("a", "a: מה חדש on top", "Developments and corrections since the last visit, a line each; the rest behind ועוד") {
    WhatsNewScreenA(WhatsNewSample.state(), WhatsNewSample.names, onFootnote = {}, onBack = {})
}

/** a, opened for the first time: everything since the first report. */
internal val whatsNewOptionAFirst = LabOption("a-first", "a: first visit", "Never opened: every update since the first report") {
    WhatsNewScreenA(WhatsNewSample.state(lastSeenMinutes = null), WhatsNewSample.names, onFootnote = {}, onBack = {})
}

/** a, opened after the latest version: nothing new. */
internal val whatsNewOptionAQuiet = LabOption("a-quiet", "a: nothing new", "Opened 5 minutes ago: one quiet line") {
    WhatsNewScreenA(WhatsNewSample.state(lastSeenMinutes = 5), WhatsNewSample.names, onFootnote = {}, onBack = {})
}
