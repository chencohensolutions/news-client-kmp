package news.app.stories

/** What the server compares headlines by when it groups stories (a grouping setting kept on the server). */
enum class StoryText { TITLE, SUBTITLE, TITLE_AND_SUBTITLE, TITLE_TIMES_SUBTITLE }

/** The grouping slider's ends and its step. */
const val STRICTNESS_MIN = 10
const val STRICTNESS_MAX = 90
const val STRICTNESS_STEP = 5
