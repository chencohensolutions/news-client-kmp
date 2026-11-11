# CLAUDE.md

Android news reader (Kotlin, Jetpack Compose) that shows what the news server
([news-server-go](https://github.com/chencohensolutions/news-server-go)) collects: headlines, articles, stories,
generated articles, threads, the threads map and statistics, over GraphQL with mutual TLS. The phone never reads the
news sites itself. Read [docs/architecture.md](docs/architecture.md) first, and [docs/news-server.md](docs/news-server.md)
for enrolling and what comes from the server.

## Rules

- **Server only.** No scraping, no site-specific code, no on-device model. A feature that needs data the server
  doesn't send is a server change first (its schema), then the app.
- **No site-specific content** anywhere: code, tests, docs, sample data, commit messages. Sample and test data use
  made-up sites (`*.example` addresses) and made-up headlines.
- **Settings**: what belongs to the server (its sites, grouping, generated articles) is read from it and changed on
  it; only display and reading preferences, caches and notifications live in `AppSettings` on the phone.
- The GraphQL schema (`app/android/src/main/graphql/*.graphqls`) is the server's, copied with
  `./gradlew :app:android:syncServerSchema`; don't edit it by hand. The app's operations are in `Operations.graphql`.

## UI changes: the UI lab

UX/UI suggestions follow the `ui-lab` skill (plugin from the [claude-skills](https://github.com/chencohensolutions/claude-skills/blob/main/plugins/ui-lab/skills/ui-lab/SKILL.md) marketplace, enabled in
`.claude/settings.json`; this project's notes for it: [.claude/ui-lab.md](.claude/ui-lab.md)): mocks in
lab code only (`app/android/src/debug/kotlin/news/app/lab/`), shown on the phone with `./scripts/ui-lab.sh`; the app
changes after the owner approves a plan. How the lab works: [docs/ui-lab.md](docs/ui-lab.md).

## Commands

Android emulator: use the `news` AVD (2 cores, 2 GB RAM, Android 37.2 with Google Play). Run it only while needed:
`./scripts/emulator.sh up` before installing or testing on it, `./scripts/emulator.sh down` when done. `up` starts it
headless on the Mac's GPU, shuts down any other emulator, since scripts and `adb` pick the first running one, and
waits for the boot. `WINDOW=1` shows its window.

```sh
./gradlew :app:android:testDebugUnitTest   # unit tests
./gradlew :app:android:assembleDebug       # the app: news.client (the UI lab app: assembleUiLab, news.client.uilab)
./gradlew :app:android:syncServerSchema    # the server's schema, from ../news-server-core-go or -PnewsServerDir=<path>
```

Against a local server on the emulator: [docs/news-server.md](docs/news-server.md#trying-it-against-a-local-server).
