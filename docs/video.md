# Video

An article's video comes from the news server as an `ArticleVideo`: a poster, and either a `stream` (an MP4 or an
HLS playlist the app plays itself) or only an `embed` (the site's own player, a web page).

- **A stream** plays in Media3's player in the article ([ArticleMedia.kt](../app/android/src/main/kotlin/news/app/ArticleMedia.kt)),
  at the top of the article and, while it plays, stuck below the status bar as the text scrolls (Settings › וידאו
  נצמד למעלה). Its full-screen button moves the same playback to full screen. `streamFor` (in
  [Sites.kt](../app/android/src/main/kotlin/news/app/Sites.kt)) takes the server's stream as it is; the phone works
  out no streams of its own.
- **Only an embed** runs the site's player in a web view, whose own full-screen button is honoured.
- Played bytes go to the app's video cache (`NewsApplication.videoCache`, Settings › אחסון וארכיון › מקום לסרטונים),
  so a replay reads them from disk. With an archive folder set, a played video is copied into the archive once a day
  as one MP4 at its highest resolution (`NewsApplication.archiveVideo`, [VideoArchiver.kt](../app/android/src/main/kotlin/news/app/VideoArchiver.kt)).

Planned: the server streams the video itself, so an article whose site has only an embedded player plays in the app's
own player too.
