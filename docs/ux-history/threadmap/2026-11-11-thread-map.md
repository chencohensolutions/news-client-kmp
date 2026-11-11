# Threads — the map over time (2026-11-11, Android)

Tag: `ux/threadmap/2026-11-11` (local)

## Options
- **a — Lanes on a time axis**: a lane per thread, dots at each story's time, a line across lanes where threads share a
  story; day / week / month. *Turned down*: the lines cross the lanes between.
- **b — Overview strips**: a strip per thread fitted to the period, rings and "נפגש עם" where threads share a story.
  *Picked*, together with c, as the map's "רצועות" view.
- **c — Constellation**: thread bubbles joined by shared stories, opening into their stories, a bottom panel and a time
  slider. *Picked*, changed: one dot per thread (its area by its stories, no story dots around it), only threads joined
  to another, a dot between two threads for each story they share. Promoted with b in 7574e36 (ThreadMap.kt).

## What they faked
- All: a snapshot of news.db (14 threads, 112 stories, Nov 4–11), before the server sent a map query (now
  `threads(first:) { stories { firstReportAt lastReportAt … } related { id } }`); opening a story did nothing.
- c: colors per thread, not per category.

## Reviving an option
    git checkout ux/threadmap/2026-11-11 -- app/android/src/debug/kotlin/news/app/lab/threadmap/<a|b|c>
Then add `news.app.threadMapOption<A|B|C>,` to the `threadmap` screen in `LabScreens`.
Built against ThreadMapSample.kt (the snapshot) and the app's ThreadsScreen as of the tag.
