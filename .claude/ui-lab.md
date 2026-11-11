# UI lab notes (for the ui-lab skill)

How the lab itself works: [docs/ui-lab.md](../docs/ui-lab.md).

## Platform

The app is Android only (`app/android`), so the lab is too, on every machine. It runs as **News UI Lab**
(`news.client.uilab`), a separate app beside the app: `./scripts/ui-lab.sh <screen> [option]` builds it, installs it on the
owner's phone when `adb devices` lists it (else the emulator; `DEVICE=emulator` or `DEVICE=<serial>` to choose) and
opens the screen.

## Option folders

`app/android/src/debug/kotlin/news/app/lab/<screen>/<b>/`, in the screens' package (`news.app`). Strings stay inline
as the app's screens do today, so there are no strings files.

## Files

- The screen `<Screen>Option<B>.kt` is `<Screen>Screen<B>(state, callbacks)`; on promotion it moves into
  `src/main/kotlin/news/app/`.
- Its state moves to `NewsViewModel.kt` / `core/model`: state the view model or the server doesn't produce yet.
- Components an option may copy: `HeadlineRow`, `StoryCard`.

## Sample data

`PreviewData` as it is: made-up sites and Hebrew headlines (`*.example` addresses, placeholder pictures). Keep sample data made up: no real headlines or site names.

## Fakes and edge cases

- Usual fakes: data the server or view model lacks; timing, a server that doesn't answer.
- Edge cases: Hebrew and English (RTL and LTR in one list), long headlines, missing images, empty, a site failing,
  light and dark.

## Screenshots

`adb -s <serial> exec-out screencap -p > <file>.png`, with the serial the script printed.

## Close the round

No extra docs to link the history file from. History title platform: Android.

## Promote

1. `git mv` the screen into `app/android/src/main/kotlin/news/app/`.
2. Move its state into `NewsViewModel.kt` (or `core/model`), have the view model produce it, and connect the
   callbacks in `MainActivity.kt`. Tests per `CLAUDE.md`.
3. No other platform.
4. Release build: `./gradlew :app:android:assembleRelease`.
