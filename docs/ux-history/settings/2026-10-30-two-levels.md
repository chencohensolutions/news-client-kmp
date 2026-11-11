# Settings — two levels in Hebrew (2026-10-30)

Tag: `ux/settings/2026-10-30`

## Options
- **a**: one page of what the owner changes, as list rows with icons: the sites (one row, a page of switches behind
  it), headlines, reading, stories, the background refresh; what is only read moves to pages of its own: storage and
  the archive, the background log, about. *Picked*, promoted in a7f46f0.

## What they faked
- Nothing: every value shown is one the view model provides. The folder picker is the real one.

## Reviving an option
    git checkout ux/settings/2026-10-30 -- app/android/src/debug/kotlin/news/app/lab/settings/<option>
Then add `settingsOption<B>,` to the `settings` screen in `LabScreens`.
Built against `AppSettings`, `ModelInfo`, `StorageInfo`, `BackgroundRun`, `ArticleDownloads` and
`PreviewData.storage/backgroundRuns` as of the tag.
