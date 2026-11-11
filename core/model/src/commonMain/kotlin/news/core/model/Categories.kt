package news.core.model

/** The shared categories every source's own sections are mapped to. Display names live in the app. */
object Categories {
    const val ISRAEL = "israel"
    const val POLITICS = "politics"
    const val SECURITY = "security"
    const val WORLD = "world"
    const val ECONOMY = "economy"
    const val LAW = "law"
    const val OPINION = "opinion"
    const val SPORT = "sport"
    const val CULTURE = "culture"
    const val TECH = "tech"
    const val HEALTH = "health"
    const val LIFESTYLE = "lifestyle"

    /** In display order. */
    val IDS = listOf(ISRAEL, POLITICS, SECURITY, WORLD, ECONOMY, LAW, OPINION, SPORT, CULTURE, TECH, HEALTH, LIFESTYLE)
}
