# ShipInfo

Android app that logs data about the ship you're on (speed, GPS position, heading, and roll/pitch) and shows it
as charts. Everything can be exported as CSV. It works offline and does not need Google Play services.

## Modes

| Tab | What it does |
| --- | --- |
| **Live** | Main mode. Turn Speed / GPS / Heading / Roll on or off, pick a sample interval, then press **Start**. Gauges show the current values. Recording keeps running with the screen off, and a notification lets you stop it. |
| **Positions** | Tap once to save a single GPS position ("pin"). You can also take pins automatically every 1–24 h, optionally lined up to a time of day (e.g. a noon position). Pins are shown as a track plot and a list with distance between pins. |
| **Roll watch** | Leave the phone on a table, locked, for hours. It calibrates level for 10 s, then samples motion continuously. Every *N* minutes it stores the peak roll to port and starboard, pitch, sideways/vertical g-force and roll period. A check is marked as a **new record** when its roll/pitch angle or sideways g beats the session maximum. |
| **Recordings** | All live and roll-watch sessions. Open one to see stat cards, zoomable charts (speed, heading, roll & pitch, acceleration), and the GPS track. Share or save each session as CSV, or export everything as a zip. |

### Conventions
* Roll: positive = starboard side down. Pitch: positive = bow up.
* Heading: *GPS course over ground* (only valid while moving) and *compass heading*, corrected to true north using
  magnetic declination. Phone compasses are often off on steel ships, so treat the compass value with care.
* Set **Phone placement** to match how the phone lies (top of the phone pointing to the bow, stern, port or
  starboard) so that roll and pitch are the right way round. Use **Set level** to zero out a tilted table.
* CSV timestamps are ISO-8601 UTC. Speeds are in knots and distances in nautical miles.

## Install
Every push builds APKs with GitHub Actions (**Actions → Build APK → Artifacts**). Download
`shipinfo-release-apk` (or `shipinfo-debug-apk`), unzip it, copy the APK to the phone and open it to install.
You'll need to allow installs from unknown sources. Android 8.0+ is required.

For long roll watches and automatic pins, allow the app to ignore battery optimisation (there is a button in the
app). For automatic pins, allow location access **all the time**.

## Build locally
Requires JDK 17 and the Android SDK (API 35).

```
./gradlew :core:test          # pure-Kotlin unit tests (motion math, peak tracking, CSV)
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

## Project layout
* `core/` is plain Kotlin/JVM code with unit tests: roll/pitch from gravity, levelling, roll period, peak tracking,
  CSV and unit helpers, and automatic pin scheduling.
* `app/` is the Android app: Jetpack Compose UI, Room database, foreground services for recording, and
  WorkManager for automatic pins.
