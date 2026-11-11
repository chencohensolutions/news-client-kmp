# Home — a modern bottom bar and topic search (2026-11-03, Android)

Tag: `ux/feed-nav/2026-11-03`

The owner asked for a more modern bottom bar, and with it a search by topic: a control opens a bottom sheet where a
topic is typed ("אוכל", "בית המשפט העליון", "נתניהו") and saved; saved topics show as labels that switch on and off
and stay between visits. With topics on, the feed is only the headlines whose embedding closeness to a topic that is on
reaches a threshold, newest first. The threshold is a setting. The closeness would come from the model stories are
already grouped with (`TextEmbedder`, `VectorCache` in `app/android/src/main/kotlin/news/app/stories/`).

## Options
- **a — אי צף + כפתור חיפוש (floating island)**: the bar becomes a capsule floating off the screen's edges with
  חדשים, אתרים, נושאים (the one on in a pill), and beside it a round search button, badged with how many topics are on.
  The button opens a sheet: a field to type and add a topic, the saved topics as chips (tap: on or off; ✕: delete) and
  how many headlines the topics on bring. The topics on float as chips above the island, an ✕ on each.
  *Picked* ("A with the search button"), revised into a2.
- **a2**: a with the search results drawn like the stories feed: a story shows whole, as the stories feed has it (its
  lead, subtitle and every other report), when any of its reports matches; a matching headline inside a story never
  shows again as a card of its own ("show all the cluster, the cluster is not filtered by search criteria"). The
  topic and closeness under the story's title. *Picked*, promoted in c3e90f3.
- **b — לשונית מעקב (a tab of its own)**: the bar is navigation only: three tabs (הכל, חדשים, מעקב) in a short bar,
  the one open in a pill with its label beside its icon; חדשים is badged with the new count, מעקב with its matches. The
  sites and categories move under the title as two dropdown chips ("כל האתרים ▾"). מעקב is the topic search as its own
  place: a "+ נושא" chip and the saved topics in a row under the title, the matches below; its sheet adds topics and
  lists each with its count, a switch and a delete button. The feed under הכל is never filtered by topic.
  *Not picked*.
- **c — שורת חיפוש (a docked search field)**: the bar is a search field with the three filters beside it as small round
  toggles. The topics on show inside the field as chips, an ✕ switching them all off. A tap opens the sheet with the
  keyboard up; typing previews how many headlines the words bring and the first three before "שמירה והפעלה"; the saved
  topics are rows with a checkbox, their count and ✕. *Not picked*.

All three: a matched headline shows its site, time, title, picture and the topic that brought it with its closeness;
an empty result points to the threshold in settings. The settings row (all three, from the gear) is "סף התאמה" under a
new section "חיפוש לפי נושא": a slider from 10 to 90 like the stories' strictness, with how many headlines each saved
topic brings at the value shown. The bar and the topics above it slide away while scrolling down.

## What they faked
- a, b, c: the closeness of a headline to a topic. A table of hand-picked scores for the sample topics and headlines
  (their English names too: "food", "supreme court"), high for a typed topic only on headlines containing its words,
  low otherwise. The real values, and so the threshold's default (55 here), need the model on the phone.
- a, b, c: saved topics and the threshold live in the lab screen's memory, not in settings or on disk.
- a, b, c: the settings page is a stand-in with only the new section; `SettingsScreen` would get the row.
- b: the topics start with איראן on, so מעקב has headlines at once.
- a2: the stories are the sample's four (2–3 sites each); a "ספנות" topic and higher scores for the sample's
  stories were added so clusters match; איראן and ספנות start on.
- Each option draws the feed with its own copy of the app's story row (without video marks or the read animation) and
  a plain top bar (no download or story-finding progress lines, no pull to refresh).

## Reviving an option
    git checkout ux/feed-nav/2026-11-03 -- app/android/src/debug/kotlin/news/app/lab/feednav/<option>
Then add `feedNavOption<B>,` to the feed-nav screen's list in `LabScreens` (with its import).
Built against `HeadlinesScreen`'s story row and `ChoiceSheet` as of cb88f50 (each option has copies), `PreviewData`
and `labSources()` as of the tag. Compile errors after reviving are in the option's copies and sample data.
