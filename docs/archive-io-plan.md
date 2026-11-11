# Plan: make the article archive cheap to write

Status: **built** in 94c7e85 (Nov 4, 2026). It went further than step 1: archiving runs once a day while charging
(`DailyArchive`), and when a folder is picked or the owner asks, instead of during each refresh. The on-phone
verification below is still to do. Code: `app/android/src/main/kotlin/news/app/ArticleArchive.kt`.

## The problem (measured on the Galaxy S25, Nov 4)

The phone ran hot and drained fast, but not from News itself: Android's media service
(`com.google.android.providers.media.module`) used a whole CPU core for long stretches. Over 35 minutes the battery
went from 30% to 14% (about 1 A), the battery temperature went from 35.5 °C to 42.8 °C, and the CPU reached 60–63 °C.
By then the media service had used almost 5 hours of CPU time in total.

The cause is how the archive writes:

- The archive is a Storage Access Framework folder (`DocumentFile`). Every create, write, rename and delete there goes
  through `com.android.externalstorage`, which then asks the media service to scan the file
  (`MEDIA_SCANNER_SCAN_FILE`). The media service runs each scan as a separate WorkManager job. It doesn't matter that
  `.nomedia` is in the folder: the job still runs, it just finds nothing.
- Each new article makes several writes:
  - `createFile <id>.json.part`, then write it
  - `findFile <id>.json` and delete the old copy
  - rename `.part` to `.json`
  - append a line to `.index`
  - append a line to `.headlines`, a 6.6 MB file that is rescanned each time
  - for each picture, the same steps again plus an append to `.pictures`
- Lookups are expensive too. Every `findFile` and `listFiles` call lists a whole folder through the provider:
  - `dir()` for each of the year, month, day and site folders
  - `ensureNoMedia()` on every save
  - `appendLine()` looks for the index files in the root on every append
  - `pictureLoaded()` lists the day's site folder for each picture
- A background refresh (`BackgroundRefreshWorker`) saves dozens of articles and their pictures. Measured: about 20
  scan requests a second, and almost 20,000 calls from `externalstorage` to the media service in 23 minutes. The
  media service keeps working through that backlog for minutes after News has finished.
- Every process start (`NewsApplication.useArchive`) saves every cached article again. Each one costs a lookup even
  when nothing is written, and it reads the 6.6 MB `.headlines` file.
- `bytes()` (the storage line in Settings) lists all 18,301 files in 1,353 folders.

## Goal

A background refresh should cause at most a few dozen provider operations, not thousands. The archive must keep its
layout and stay readable by older versions: same folders, same file names, same index file formats.

## Steps

1. **Keep the indexes in the app's own storage; write the archive copies in batches.**
   - `.index`, `.headlines`, `.pictures` and `.videos` are held in memory (they already are) and appended to files in
     `filesDir/archive/` with plain `File` I/O. This costs no provider calls and no scans.
   - Copy them to the archive folder in one write per file:
     - at the end of a background run (`BackgroundRefreshWorker`)
     - when the app goes to the background
     - every N minutes at most while in use
     That makes 4 scans per refresh instead of 2–3 per article. Use a `dirty` flag and skip files that didn't change.
   - On start, read the local copy. If it is missing (a reinstall, or a new folder was picked), read the archive's
     copy once, as today, and keep it locally.
   - This takes over the "last line wins" duty: the local file is the truth, and the archive copy is a full rewrite of it.

2. **Write each file once, under its final name.**
   - Drop the `.part` file, the `findFile` and delete of the old copy, and the rename in `writeFile` and
     `writeFileStreaming`. That's 4 provider operations and up to 4 scans per file.
   - For a new file, use `createFile(name)` and write it.
   - To overwrite an old-format file, open it in `"wt"` (truncate) mode instead of replacing it.
   - Partial files are tolerated: the index is written only after the file is complete, and a json that fails to
     parse is treated as missing and saved again.
   - Keep the temp-and-rename only for the index copies in step 1, if their atomicity matters (one rename per batch).
   - Note: a name given to `createFile` can come back changed (`name (1)`) if the file exists. The index tells us
     whether it exists, so overwrite in that case.

3. **Build addresses instead of searching for them.**
   - For a file under the tree, the document ID is the tree's root ID + `/` + the relative path.
     `DocumentsContract.buildDocumentUriUsingTree(folder, rootDocId + "/" + path)` gives its URI with no query. Use
     this in `find()`, `appendLine()`, `readLines()`, `readIndex()` and `readHeadlines()`.
   - Keep a set of the folders already created (year, month, day, site) in memory, so `dir()` only calls
     `createDirectory` for a folder not seen before. Fill it from `.index` paths on start.
   - Run `ensureNoMedia()` once per `ArticleArchive` instance, not on every save.
   - In `pictureLoaded()`, replace `listFiles().none { … }` with a check of `archivedPictures` (it is already the
     record).
   - Fall back to `findFile` only if a built URI fails, for example on a provider whose document IDs aren't
     path-based. Check `DocumentsContract.isTreeUri` and the authority `com.android.externalstorage.documents`, and
     use the old path for any other provider.

4. **Don't re-save the whole cache on every start.**
   - In `useArchive`, save only articles whose id is missing from the index or below `FORMAT`. Check this in memory
     before any provider call.
   - Today `save()` already returns early, but only after loading the index and headlines. Make that cheap and lazy,
     and skip the whole pass when the folder hasn't changed since the last run.

5. **Count `bytes()` without listing 18k files.**
   - Keep a running total in the local index: add each file's size when it is written, and store it next to the
     index.
   - Recount from the folders only on demand (a "Recount" action) or after picking a folder.

## Verification

- Unit tests (JVM) for the batching and the local index: appends survive a restart; the archive copy equals the
  local file after a flush; an old archive with only its own `.index` loads.
- On the phone, before and after, for one background refresh with the archive on:
  - Scan requests: `adb shell logcat -d | grep -c "Work initiated for action \[ android.intent.action.MEDIA_SCANNER_SCAN_FILE"`
    over the run. Target: tens, not thousands.
  - The media service's CPU: `/proc/<pid>/stat` ticks before and after the run, and 5 minutes later. Target: idle
    again within a minute.
  - Run `./scripts/battery.sh start` and `./scripts/battery.sh report` over a few hours on battery. Compare with
    today's numbers, and add them to [battery.md](battery.md).
- Open an old archive folder (made before the change) and a new one, and check that the feed's older headlines, the
  pictures and the videos all still load. Pick the same folder after a reinstall.

## Docs to update in the same change

- The layout comment at the top of `ArticleArchive.kt`: the indexes are written in batches, and kept in `filesDir`.
- [battery.md](battery.md): add "the archive's writes cost the media service CPU" to what drives the cost, with the
  before and after numbers.
