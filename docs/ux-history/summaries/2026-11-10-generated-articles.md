# Feed — generated articles and the digest (2026-11-10, Android)

Tag: `ux/summaries/2026-11-10`

The news server (news-server-go, milestone 6) writes with Gemini a short article for each story that changed in the
last hours and a digest of what is most covered in the last hour: Hebrew, facts only, each fact once, every sentence
with footnotes (the articles it comes from), and where the sites state different facts, a sentence saying which says
what. The owner asked for a personal newspaper ("dedicated to me"). Where do the story's article and the digest go?

## Options
- **a — In the feed**: each story card is its generated article (written title, the first facts with the sites as
  footnote labels; a tap shows the rest, "האתרים חלוקים" and the reports); the digest is the first card, "בשעה
  האחרונה". *Picked, with b and c (d)*.
- **b — Pages of their own**: short story cards ("✦ כתבה מ־3 אתרים"); a tap opens the generated article as a page with
  superscript footnote numbers, "מה שונה בין האתרים" and the sources by number; the digest is its own tab, העיתון שלי.
  *Picked, with a and c (d)*.
- **c — A newspaper**: a masthead with the date, the last hour as the lead paragraph, each story as a short piece with
  the sites' labels in the text and ⚖ lines where they disagree. *Picked, with a and b (d)*.
- **d — a + b + c**: the owner picked all three: העיתון שלי is c's newspaper; סיפורים is a's feed with the digest card
  first; "לכתבה המלאה" opens b's page. *Picked*, promoted in 5c11f93 (העיתון שלי opens from a newspaper icon in the feed's title bar and from the digest card, rather than as a tab).

## What they faked
- All: the generated articles and the digest (made-up Hebrew, in the server's API shape, from PreviewData's stories;
  the disagreement on the Zim story was made up to show one), their times, and opening an article (a toast).

## Reviving an option
    git checkout ux/summaries/2026-11-10 -- app/android/src/debug/kotlin/news/app/lab/summaries/<option> app/android/src/debug/kotlin/news/app/lab/summaries/SummariesSample.kt
Then add `summariesOption<B>` to the `summaries` screen in `LabScreens`.
Built against `SitePill`, `shortSiteName`, `ago`, `AppLayout` and PreviewData's stories as of the tag. Compile errors
after reviving are in the option's copies and sample data; fix them there.
