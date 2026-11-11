# The UI lab

A library of the app's screens and of suggested designs for them, running on the phone or emulator with made-up
data, so they scroll, tap and switch themes like the app. The first page lists the screens; each screen lists its
options, the first being the screen as the app has it now (`current`). UX suggestions are built and reviewed here
before the app changes: see the [ui-lab skill](https://github.com/chencohensolutions/claude-skills/blob/main/plugins/ui-lab/skills/ui-lab/SKILL.md) (this project's notes for it:
[.claude/ui-lab.md](../.claude/ui-lab.md)).

```bash
./scripts/ui-lab.sh                    # build, install on the connected phone (else the emulator), open the list
./scripts/ui-lab.sh stories            # a screen's options
./scripts/ui-lab.sh stories current    # one option (ids in LabScreens.kt)
DEVICE=emulator ./scripts/ui-lab.sh    # the emulator even with a phone connected (or DEVICE=<serial>)
```

## How it works

- The script builds a separate app, **News Client UI Lab**: the `uiLab` build type in `app/android/build.gradle.kts`,
  which is the debug build with the id `news.client.uilab`, compiling `src/debug`. `src/uiLab/AndroidManifest.xml`
  makes the lab its only launcher entry, so it sits beside the app on the phone and never replaces it.
- The lab is [UiLabActivity.kt](../app/android/src/debug/kotlin/news/app/lab/UiLabActivity.kt), its list is
  [LabScreens.kt](../app/android/src/debug/kotlin/news/app/lab/LabScreens.kt), and its data is
  [PreviewData.kt](../app/android/src/debug/kotlin/news/app/lab/PreviewData.kt): made-up sites, Hebrew headlines,
  stories and articles of realistic lengths. Pictures are placeholders loaded from the web, so the lab needs a network
  connection to show them.
- It draws the real screens from `src/main` (`HeadlinesScreen`, `ArticleScreen`, `SettingsScreen`) inside the app's
  `NewsTheme`. They take state and callbacks, so the lab passes made-up state; the view switch, source chips and
  settings work while a screen is open, and taps that would ask the server, download or save do nothing.
- Everything is in `src/debug`, so release builds never compile it. The debug build declares the lab activity
  without a launcher icon; open it there with `adb shell am start -n news.client/news.app.lab.UiLabActivity`.

## Adding a screen

A screen joins the lab once it takes its data as parameters rather than reading the view model. Add what it needs
to `PreviewData`, then a `LabScreen` with its `current` option (and any states worth seeing as more options) to
`labScreens`.
