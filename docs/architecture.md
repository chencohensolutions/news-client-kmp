# Architecture

## Goal

A news reader for Android (Kotlin, Jetpack Compose) that shows what a news server collects: headlines from several
news sites, grouped into stories, with generated articles that summarize each story, threads of related stories, a
map of them, and statistics. The phone does no collecting of its own: it never reads the sites. Everything comes from
[news-server-go](https://github.com/chencohensolutions/news-server-go) over GraphQL with mutual TLS.

## Key decisions

### The server does the work, the phone shows it

The server reads the sites, keeps the articles, groups them into stories (its own model, or Gemini), writes the
generated articles and the threads, and keeps the per-site settings. The phone asks it for what changed and shows
it. That keeps the app small (no scraping engine, no on-device model, no browser-like network stack), cheap on the
battery (a background run is a few small requests), and the same on every phone: a fix to how a site is read is a
server change, never an app release.

### Nothing to do without a server

Until the phone is enrolled, the app shows only the connect screen ([ServerScreen.kt](../app/android/src/main/kotlin/news/app/ServerScreen.kt)):
scanning the enrollment QR code, pasting the enrollment link, or typing the address and code. Once connected, the feed
opens, and the server's details live in Settings › שרת החדשות. Disconnecting returns to the connect screen.
Enrolling, trust and renewal: [news-server.md](news-server.md).

### When the server doesn't answer

The feed stays as it was: the headlines and articles already on the phone remain readable, its title line says the
server doesn't answer, and every refresh asks again. The phone keeps a list of the server's addresses and looks it up
in the BitTorrent DHT by its key when they all stop answering (`news.app.server.discovery`).

### What stays on the phone

- **Display preferences**: how the feed and the reader look (filter labels, picture placement, the labels of a
  generated article, sentence lines, what a related story shows, snapping, sticky video), what counts as new,
  prefetching pictures while scrolling and reading, notifications, and when to refresh. In `AppSettings`
  ([Settings.kt](../app/android/src/main/kotlin/news/app/Settings.kt)).
- **Reading state**: read and viewed marks, the site and category filters, the saved topics of the topic search.
- **Caches**: articles (`FeedCache`), pictures (Coil) and played video (Media3), each within its quota, and an
  optional archive folder that keeps every article with its pictures and played videos (`ArticleArchive`).
- **Topic search** matches a saved topic's words in the headlines on the phone (`news.app.stories.wordScores`).

Settings that belong to the server are read from it and changed on it: the sites it reads (on or off, RSS or pages,
the wait between requests), how it groups stories (method, strictness, what it compares, the days), and how it writes
the generated articles (length, full articles, how often it rewrites, its daily reserve).

## Modules

```
core/model     Article, ArticleSummary, Categories: the shapes the app keeps and shows (KMP: android, ios, jvm)
app/android    the Android app
```

In the app (`app/android/src/main/kotlin/news/app`):

```
server/          NewsServer (enrolling, the connection, every call), ServerApi (Apollo), ServerFeed (the headlines
                 between refreshes), ServerTls and DeviceKeys (mutual TLS with a Keystore key), discovery/ (DHT lookup)
NewsRepository   headlines, articles and sites, from the server, through FeedCache
ArticleFetcher   brings the feed's articles the refresh didn't, and fills each headline from its article
NewsViewModel    the feed's state: refreshing, stories, generated articles, threads, filters, reading
BackgroundRefresh the background run: new headlines and articles, the server's stories, notifications
stories/         Story and its ordering, the topic search's matching
*Screen.kt, Ui.kt, Reader.kt, Summaries.kt, Threads.kt, ThreadMap.kt, StatsScreen.kt   the screens
```

The GraphQL schema in `app/android/src/main/graphql/*.graphqls` is the server's, copied with
`./gradlew :app:android:syncServerSchema`; the operations the app sends are in `Operations.graphql` beside it.

Data flow:

```
news server ──GraphQL/mTLS──▶ NewsServer ──▶ NewsRepository ──▶ NewsViewModel ──▶ screens
                                    │              │
                                    │              └─ FeedCache (articles, headlines, stories on the phone)
                                    └─ stories, generated articles, threads, stats, settings
```

Pictures and videos load from where the server's articles point (the sites' media servers); the server sends their
addresses, not the files.

## Roadmap

1. **Server-only client (done).** The app made from the earlier phone-scraping reader, its scraping, on-device
   grouping and fetch settings removed.
2. **Roles and video.** Admin and user roles (who may change the server's settings), and video streamed by the server
   so every article's video plays in the app's own player.
