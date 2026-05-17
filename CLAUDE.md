# GarminEnduro3Connect

Streams live run stats (pace, distance, elapsed time, HR) from a Garmin Enduro 3 native activity to an Android companion app via BLE, which also forwards stats to Everysight Maverick AR glasses for heads-up display.

## Project layout

```
watch-app/          Monkey C DataField (active)
android-app/        Kotlin Android companion app (active)
app/                Old Android app — ignore, superseded by android-app/
keys/               developer_key.pem + developer_key.der — DO NOT REGENERATE
```

## Build commands

**Watch app:**
```
SDK="/Users/mikes/Library/Application Support/Garmin/ConnectIQ/Sdks/connectiq-sdk-mac-9.1.0-2026-03-09-6a872a80b"
"$SDK/bin/monkeyc" -f watch-app/monkey.jungle -o watch-app/EnduroData.prg -y keys/developer_key.der -d enduro3
```

**Android app:**
```
cd android-app && ./gradlew assembleDebug
# APK: android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Deploy

**Watch:** Copy `watch-app/EnduroData.prg` to the watch `APPS/` folder via OpenMTP, then disconnect. The watch rescans and notifies "new app detected". The `APPS/` folder will appear empty after — that's normal, the watch consumed it.

**Android:** `adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk`

## App UUID

Both sides must share the same UUID:
- `watch-app/manifest.xml`: `id="e5f4a3b2c1d04e5f6a7b8c9d0e1f2a3b"`
- `android-app/.../MainViewModel.kt`: `WATCH_APP_UUID = "e5f4a3b2c1d04e5f6a7b8c9d0e1f2a3b"`

When changing the UUID (to force a fresh install on the watch), update both files and rebuild both.

## To use the DataField

Start a native Garmin run → edit a data screen → add a field slot → select "Enduro Data v6" from the Connect IQ section. The field will display live stats and transmit to the phone every ~5 seconds. On the Android app, tap "Listen" on the Enduro 3 device row.

## Manifest constraints (hard-won)

- DataField manifest type is `"datafield"` — not `"data-field"`, not `"dataField"`
- The manifest `entry` must be an `Application.AppBase` subclass, not the DataField class itself. Setting a DataField as entry causes "Unexpected Type Error" at runtime with an empty stack.
- `Fit` permission is not allowed for `datafield` type — compiler error if present
- `launcherIcon` attribute is fine for datafield type

## Architecture

```
EnduroDataApp.mc    AppBase — manifest entry point, returns EnduroDataField from getInitialView()
EnduroDataView.mc   EnduroDataField extends WatchUi.DataField — compute() reads Activity.Info,
                    throttled transmit via Communications.transmit(); onUpdate() draws to dc;
                    onTimerLap() fires at each auto-lap, computes lap avg pace, transmits lap_pace
EnduroDataDelegate.mc  SendListener extends Communications.ConnectionListener
```

DataFields run inside a native activity and receive live `Activity.Info` via `compute()`. A `watch-app` type cannot access native run data — it runs as a standalone app with no concurrent activity context.

Units: pace is min/mile, distance is miles. `METERS_PER_MILE = 1609.344` is a module-level constant in `EnduroDataView.mc` shared by `compute()` and `onTimerLap()`.

```
Garmin watch → ConnectIQManager (BLE) → MainViewModel.runStats (StateFlow)
                                                  ↓
                                     EverysightManager.updateStats()
                                                  ↓
                                        RunStatsScreen (AR HUD)
                                                  ↓
                                       Everysight Maverick glasses

Garmin watch → ConnectIQManager.lapSplit (SharedFlow)
                                                  ↓
                                     EverysightManager.showLapSplit()
                                                  ↓
                              RunStatsScreen.showLapSplit() → PopupMessage (5 sec)
```

### Android Everysight integration

- `EverysightManager.kt` — singleton; `start()` connects to glasses via BLE, `updateStats()` pushes pace/dist/time/HR to the screen, `showLapSplit()` triggers a mile-split popup; `GlassesState` enum: DISCONNECTED / CONNECTING / CONNECTED / ERROR
- `RunStatsScreen.kt` — `Screen` subclass; 2×2 HUD (PACE/DIST top row, TIME/HR bottom row); green labels, white values; `showLapSplit()` fires a centered `PopupMessage` auto-dismissed after 5 seconds
- `MainViewModel.kt` — `init {}` block collects `runStats` → `updateStats()` and `lapSplit` → `showLapSplit()` automatically
- `MainActivity.kt` — "Connect" button calls `viewModel.connectGlasses()`; button disabled while connected/connecting

### Mile-split popup

At each auto-lap the watch calls `onTimerLap()`, which diffs `_lastDist`/`_lastTime` against `_lapStartDist`/`_lapStartTime` to compute the lap's average pace in min/mile, then transmits `{"lap_pace": "M:SS"}` as a separate BLE message. Android routes it: `ConnectIQManager.lapSplit (SharedFlow)` → `MainViewModel` → `EverysightManager.showLapSplit()` → `RunStatsScreen.showLapSplit()` → `PopupMessage` centered on the HUD, auto-dismissed after 5 seconds.

### Everysight SDK setup (one-time, per dev machine)

SDK version: **2.6.1**, hosted on GitHub Packages.

1. Create a GitHub PAT with `read:packages` scope at github.com → Settings → Developer Settings → Personal Access Tokens
2. Add to `~/.gradle/gradle.properties` (not committed):
   ```
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.token=YOUR_GITHUB_PAT
   ```
3. Place the `ApiKey` file (from everysight.com/developer) in `android-app/app/src/main/assets/ApiKey`

Maven repo: `https://maven.pkg.github.com/everysight-maverick/m1-android-maven`
Dependencies: `com.everysight:evskit:2.6.1`, `com.everysight:nativeevskit:2.6.1`

## Android build config

- Java 21 required — Java 25 breaks Kotlin DSL. Path set in `android-app/gradle.properties`
- Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21
- Use `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` — `kotlinOptions` is deprecated in this version
- Android SDK at `~/Library/Android/sdk` (set in `android-app/local.properties`)

## Sideloading troubleshooting

- No "new app detected" notification: uninstall the old version via watch Settings → Connect IQ Store → Installed, then copy the .prg again
- Watch app not visible: DataFields don't appear in the app list. They only appear in the data screen field picker during an activity
- CIQ log at `watch-app/CIQ_LOG.BAK` after a crash — check `Details:` and `Stack:` lines
- Version the app name (e.g. "Enduro Data v6") to match log entries to specific builds
