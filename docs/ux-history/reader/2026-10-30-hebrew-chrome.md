# Reader — Hebrew chrome, next card, category (2026-10-30)

Tag: `ux/reader/2026-10-30`

## Options
- **a**: back arrow, share and open-in-browser icons; "סיפור 2 מתוך 10" with a thin progress line; a story's
  reports as chips (site · time, a check once read); a tinted site label in the byline; body text at 18sp with
  roomier lines; a "הבא / הסיפור הבא" card at the end of each article that a tap turns to; "זה הכול ברשימה הזאת"
  at the end. *Turned down*: superseded by b.
- **b**: a plus the article's category, in the title bar's second line, as an outlined label in the byline, and on
  the next card. *Picked*, promoted in a7f46f0.

## What they faked
- Nothing the view model lacks: the category comes with the headline (`ArticleSummary.category`), the next page from
  `ReaderState`. Sharing opens the real share sheet. The lab shows the sample article under every headline (one site's
  pages the one with photos and a video), so subtitles don't match titles.

## Reviving an option
    git checkout ux/reader/2026-10-30 -- app/android/src/debug/kotlin/news/app/lab/reader/<option>
Then add `readerOption<B>,` to the `reader` screen in `LabScreens`.
Built against `ReaderState`, `ArticleState`, `VideoCard`, `ArticlePhoto`, `ContentDirection` and
`PreviewData.article/mediaArticle` as of the tag.
