# Home — download progress in the header (2026-10-30)

Tag: `ux/home/2026-10-30-2`

The owner asked to see the progress of the articles downloading in advance (the prefetch that fills pictures and
saves articles for reading offline) in the header of the feeds. Settings already showed it; the headline screen did not.

## Options
- **a — שורה (line)**: the count joins the line under the title: "עודכן לפני 4 דק׳ · 8 חדשות · ⬇ 102/251", or
  "ממתין ל-Wi-Fi" on mobile data; it leaves the line when every article is in. *Not picked*.
- **b — פס (bar)**: a thin progress bar along the bottom of the title bar, with a caption under it: "שומר כתבות לקריאה
  בלי רשת · 120 מתוך 251" and the percentage; grey with "ממתין ל-Wi-Fi" on mobile data; both fold away when every
  article is in. *Picked*, promoted in 2f835b0.
- **c — טבעת (ring)**: a progress ring with a download arrow beside the settings button; tapping it opens a short note
  with the count. *Not picked*.

## What they faked
- All: the downloads' pace (251 articles at one per 80 ms, then 4 s without progress, and again). The state the
  header reads, `ArticleDownloads(done, total, running, waitingForWifi)`, is what `NewsViewModel.downloads` already
  produces for Settings.
- All: the "waiting for Wi-Fi" variants (`a-wifi`, `b-wifi`, `c-wifi`) show 40 of 251 saved, standing still.

## Reviving an option
    git checkout ux/home/2026-10-30-2 -- app/android/src/debug/kotlin/news/app/lab/home/<option>
Then add `homeOption<B>,` and `homeOption<B>Wifi,` to the home screen's list in `LabScreens` (with their imports).
Built against `HeadlinesScreen` as of a7141ba (each option is a copy of it with its own header), `PreviewData` and
`labSources()` as of the tag. Compile errors after reviving are in the option's copy; fix them there.
