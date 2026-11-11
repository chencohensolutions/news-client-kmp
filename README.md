# news-client-kmp

An Android news reader (Kotlin, Jetpack Compose) for [news-server-go](https://github.com/chencohensolutions/news-server-go).
The server reads the news sites, groups their articles into stories and writes a generated article for each; the app
shows the feed, the articles, the stories and their generated articles, threads of related stories and a map of them,
and the server's statistics. The phone never reads the sites itself.

## How it works

- **Connecting**: on first launch the app asks to be connected to a server: scan the QR code `news-server enroll-code`
  prints, paste its link, or type the address and the one-time code. The phone makes a key in the Android Keystore
  and gets a client certificate; from then on every request uses mutual TLS. If the server moves, the phone finds it
  again by its key in the BitTorrent DHT.
- **Settings**: the server's own settings (its sites, how it groups stories, how it writes the generated articles) are
  read from it and changed on it. The phone keeps only display and reading preferences, its caches and notifications.
- **Offline**: what is already on the phone stays readable when the server doesn't answer; an optional archive folder
  keeps every article with its pictures.

## Layout

```
core/model     Article / ArticleSummary models (KMP)
app/android    the Android app (application id news.client; the UI lab app news.client.uilab)
docs/          architecture, the news server, video, the UI lab
scripts/       emulator, UI lab, battery measurement
```

## Common commands

```sh
./gradlew :app:android:testDebugUnitTest   # unit tests
./gradlew :app:android:assembleDebug       # build the app
./scripts/emulator.sh up                   # start the emulator (down when done)
./scripts/ui-lab.sh                        # the UI lab on the connected phone or emulator
```

Requires JDK 21+ and the Android SDK (`local.properties` with `sdk.dir`).

## Docs

- [Architecture](docs/architecture.md)
- [The news server](docs/news-server.md): enrolling, trust, what comes from it, trying it locally
- [Video](docs/video.md)
- [The UI lab](docs/ui-lab.md)
- [Measuring battery use](docs/battery.md)
