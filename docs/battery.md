# Measuring battery use

The app asks the news server for news in the background every few minutes (Settings › רענון ברקע, 30 by default), so
its battery cost is worth watching. Two views of it:

## Android's own accounting

Android estimates every app's battery use (mAh) from its CPU time, network, wakelocks and jobs. Only time on
battery counts, so measure unplugged (wireless debugging is fine):

```bash
./scripts/battery.sh start     # reset the statistics, then use the phone normally for a few hours
./scripts/battery.sh report    # the app's estimated mAh, its share of the phone's drain, CPU, network, jobs
```

The emulator has no real power profile, so its estimates stay empty; use the phone.

## The app's own log

Each background refresh records how long it ran, its CPU time, the data it downloaded and the battery level
(`BackgroundLog`, the last 500 runs). Settings › יומן הרענונים shows the last 24 hours in sum and the latest runs.
The battery level is the whole phone's, so it only means something compared over many runs on battery.

What drives the cost: the interval (each run wakes the radio and asks the server for what changed since the last
one) and the new articles it brings. The server does the reading, grouping and writing, so a run is a few small
requests. Android also skips runs when the battery is low and stretches them while the phone is idle.
