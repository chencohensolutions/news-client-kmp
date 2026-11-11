# Stories — the server's generated stories in the feed (2026-11-11, Android)

Tag: `ux/generated-stories/2026-11-11`

With the news server on, its stories have generated articles. The owner wanted them in the feed with the picture and
articles "as it was local in the same design", a way to toggle between generated and original, a חדשים badge that
counts something meaningful (it was mostly 99+), and the phone's own clusters hidden since the server's replace them.

## Options
All three show the generated story in the phone's own story card (sites, picture, "✦ כתבה מ־N אתרים" with ⚖, the
written title and first facts, the reports under it), the badge counting only unread generated stories, and no phone
clusters with the server on.
- **a — Toggle at the top**: one switch above the feed, כתבות כתובות | כותרות המקור. *Picked, with b*.
- **b — Toggle on each card**: a small ⇄ chip, למקור / לכתבה הכתובה. *Picked, with a*: the switch sets the feed, a
  card's ⇄ flips just that story (reset when the switch changes). Promoted in 5d0c845.
- **c — Tap the text to flip**. *Not picked*: hidden, and the tap competes with opening the story.

Unread rule (proposed, not objected to): a generated story is unread until its card, article page or a source is
opened; unread again only when a new site joins it, not when it is rewritten; only within "חדש עד".

## What they faked
- All: the server's stories and generated articles (the summaries sample), which were unread, the single headlines.

## Reviving an option
    git checkout ux/generated-stories/2026-11-11 -- app/android/src/debug/kotlin/news/app/lab/genstories
Then add the `gen-stories` screen with `genStoriesOptions` to `LabScreens`.
Built against SitePill, NewBadge, DisagreeChip, ago and the summaries sample as of the tag.
