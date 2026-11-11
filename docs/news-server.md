# The news server

[news-server-go](https://github.com/chencohensolutions/news-server-go) reads the sites, keeps the articles, groups
the stories and writes the generated articles, and serves them over GraphQL with mutual TLS. It is where all of the
app's news comes from: until the phone is enrolled, the app shows only the connect screen
([ServerScreen.kt](../app/android/src/main/kotlin/news/app/ServerScreen.kt), from the UI lab's
[server round](ux-history/server/2026-11-10-connect.md)); once connected, Settings › שרת החדשות shows its details.

The code is in [`news.app.server`](../app/android/src/main/kotlin/news/app/server/): `NewsServer` (enrolling, the
connection, what the repository and the view model ask), `ServerApi` (Apollo), `ServerFeed` (the headlines between
refreshes, pure), `ServerTls` and `DeviceKeys` (the keys).

## Connecting

As the journal app does with journal-server-go (news-server-go's [authentication.md](https://github.com/chencohensolutions/news-server-go/blob/main/docs/authentication.md)):

1. On the server: `news-server enroll-code <user>`. It prints the address, a one-time code (10 minutes, single use), the
   server's key ("Key  abcd ef01 2345 6789") and a QR code holding
   `news://enroll?code=…&server=…&fingerprint=…`.
2. On the phone: scan it (the connect screen's סריקת קוד QR, Google's code scanner, no camera permission), or with
   the camera app, which opens the app on the link (`MainActivity`'s `news://enroll` filter). Or type the address and
   the code.
3. The app asks `enrollmentPreview` (whose code it is), makes an EC P-256 key in the Android Keystore (StrongBox when
   the phone has it, else the TEE), sends a certificate request with the code (`enrollDevice`) and keeps the
   certificate beside the key. The key never leaves the Keystore. `news-server devices` lists the phone.

### Trusting the server: its key, not a CA

The server's certificate is signed by its own CA, which the phone doesn't know, and the address may be an IP address
the certificate doesn't list. So the phone checks the server certificate's key (the SHA-256 of its
SubjectPublicKeyInfo, `PinnedTrustManager`) instead of a CA and the host name:

- **Scanned:** the QR code's `fingerprint` is pinned from the first connection; a server showing another key is
  refused ("השרת בכתובת הזאת הציג מפתח אחר").
- **Typed:** the phone has nothing to check against. It sends only the code (`enrollmentPreview`), notes the key the
  server showed, and asks the owner to compare its first 16 digits with the "Key" line `enroll-code` printed
  (`ServerState.ConfirmKey`). Only then does it make the key and enroll, with that key pinned.

### Renewing

Certificates last 365 days. In the last 30, when `serverInfo.renewCertificates`, the app makes a new key under the
other of two Keystore aliases, asks `renewCertificate` with the current certificate, and deletes the old key once the
new certificate is in (`NewsServer.renewIfDue`, at every start of the app).

## What comes from the server

Everything, in the foreground and in the background:

- **Headlines** (`NewsRepository.latest`): `headlines(since)`, since the newest sighting the phone keeps, merged into
  the headlines kept in `server-headlines.json` (`ServerFeed`). The feed is each site's page at the server's last read
  of it (seen within 15 minutes of the site's newest sighting), the sites interleaved.
  Headlines not seen for 3 days are forgotten; the phone's own cache keeps the feed's older part, as before.
- **Articles**: the feed's articles the phone lacks come in batches of 200 (`articles`) with each refresh, so the feed
  shows at once. An article the server doesn't have can't be opened yet ("הכתבה עוד לא בשרת").
- **Stories**: when `serverInfo.stories`, the server's (`stories`) over the feed, a story kept when it has reports from
  two sites. Otherwise each headline is a story of its own.
- **Sites** (Settings › אתרים): the server's sites (`sources`) with how it reads each; switching a site on or off, its
  RSS feed instead of its pages, and the wait between its requests are saved on the server (`setSource`) and count
  from its next read. Only an admin changes these; for everyone else they show read-only. Each user also picks the
  sites of their own feed ("בפיד שלי", `setSourceHidden`, `Source.hidden`), kept on the server for all their devices;
  the feed leaves out those and the sites the server doesn't read.
- **Roles**: the phone asks `me` when it connects. An admin (Settings › משתמשים ומכשירים) sees the users and their
  devices, invites someone (`createInvite`: the code and its QR code, for a new user or another device of one),
  changes a role, revokes a device and removes a user. A server from before roles (no `me`) lets everyone change its
  settings. An `@admin` call that answers FORBIDDEN takes the admin controls away.
- **Read marks** follow the user across devices (`ReadSync`): an opened article goes out with `markRead`, a few
  seconds after the last one (the marks wait in `read-outbox.txt` until the server takes them), and every refresh
  brings in `readArticles` since the newest read time heard (`read-since.txt`).
- **Video** comes through the server: on play, `videoStream(url)` gives a path on the server (HLS or an MP4), played
  in the app's player with the phone's certificate and the pinned key (Media3's OkHttp data source,
  `NewsServer.mediaClient`). When it is null, the article's embed plays in a web view. The archive saves a played
  video from the same server stream, asked for again when it archives (the paths last 6 hours).
- The background refresh goes through the same `NewsRepository.latest`.
- Pictures load from the sites' media servers: the server sends their addresses, not the files.
- Topic search, notifications and the archive stay on the phone.

When the server doesn't answer, the feed stays as it was, its title line says "שרת החדשות לא עונה", Settings shows
"השרת לא עונה" with a retry, and every refresh asks the server again. What is already on the phone stays readable.

## The schema

`app/android/src/main/graphql/*.graphqls` is the server's `graph/schema/*.graphql` (news-server-go, or news-server-core-go with `-PnewsServerDir`), copied:

```bash
./gradlew :app:android:syncServerSchema    # from ../news-server-core-go, or -PnewsServerDir=<path>
```

The app's operations are in `Operations.graphql` beside it.

## Trying it against a local server

```bash
cd news-server-go
scripts/generate-ca.sh && SERVER_NAMES="10.0.2.2" scripts/generate-server-cert.sh
# config.json: "public_url": "https://10.0.2.2:56399" (the emulator's name for this Mac)
go run . &
go run . enroll-code <user>
adb shell "am start -a android.intent.action.VIEW -d 'news://enroll?code=…&fingerprint=…&server=…'"
```
