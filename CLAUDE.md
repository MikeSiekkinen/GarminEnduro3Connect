# GarminEnduro3Connect

Streams live run stats (pace, distance, elapsed time, HR) from a Garmin Enduro 3 native activity to an Android companion app via BLE, which also forwards stats to Everysight Maverick AR glasses for heads-up display.

## Project layout

```
watch-app/          Monkey C DataField (active)
android-app/        Kotlin Android companion app (active)
app/                Old Android app — ignore, superseded by android-app/
keys/               developer_key.pem + developer_key.der — DO NOT REGENERATE
docs/               Design plans and notes (e.g. street-name-ar-hud-plan.md)
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

Android FusedLocationProviderClient (1Hz GPS)
                    ↓
          StreetNameProvider (reverse geocode via Android Geocoder, triggers on ≥10m move)
                    ↓
          MainViewModel → EverysightManager.updateStreetName()
                    ↓
          RunStatsScreen.updateStreetName() → white text overlay at top of HUD
```

### Android Everysight integration

- `EverysightManager.kt` — singleton; `start()` inits + connects to glasses, `updateStats()` pushes pace/dist/time/HR, `showLapSplit()` triggers mile-split popup, `updateStreetName()` updates street overlay, `setStreetNameVisible()` toggles it; `GlassesState` enum: DISCONNECTED / CONNECTING / CONNECTED / ERROR
- `RunStatsScreen.kt` — `Screen` subclass; 2×2 HUD (PACE/DIST top row, TIME/HR bottom row); green labels, white values; white street name text centered at top; `showLapSplit()` fires a centered `PopupMessage`; `updateStreetName()`/`setStreetNameVisible()` control the overlay
- `StreetNameProvider.kt` — wraps `FusedLocationProviderClient` + `Geocoder`; emits `StateFlow<String>` of current street name (`.thoroughfare`); exposes `lastBearing: Float` for Phase 2 AR; geocodes on moves ≥10m; call `start()`/`stop()` from ViewModel
- `MainViewModel.kt` — `AndroidViewModel`; `init {}` collects `runStats` → `updateStats()`, `lapSplit` → `showLapSplit()`, `streetNameProvider.streetName` → `updateStreetName()`; call `startStreetNameUpdates()` after location permission granted
- `MainActivity.kt` — "Connect" button calls `viewModel.connectGlasses()`; requests both BT and `ACCESS_FINE_LOCATION` permissions on all Android versions

### Everysight SDK API (SDK 2.6.1) — hard-won correct signatures

**Initialization (working flow on Android 16, SDK 2.6.1):**
```kotlin
Evs.init(context)
Evs.instance().registerAppEvents(listener)
// Auto-load of assets/sdk.[serial].key didn't fire for us — set the bytes explicitly.
val bytes = context.assets.open("sdk.255202400519.key").use { it.readBytes() }
Evs.instance().auth().setApiKey(bytes)
Evs.instance().startExt(hashSetOf("EV0519"))   // HashSet<String> of allowed BLE names
// First-time pairing only: launch SDK's bundled discovery activity so it learns the
// glasses' MAC. Once paired the SDK persists the device and reconnects automatically.
if (!Evs.instance().comm().hasConfiguredDevice()) {
    val intent = Intent().apply {
        setClassName(context.packageName,
            "com.everysight.evskit.android.internal.ui.EvsGlassesScanActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
```

Key facts the SDK docs don't make obvious:
- `startExt(HashSet<String>)` takes BLE **names**, not glasses types. An empty set is "match nothing" — not "any device". Earlier note here was wrong.
- `startExt` alone does NOT initiate a scan when no device is configured. The SDK only auto-connects to a previously-paired (remembered) device. To do first-time pairing you must launch `EvsGlassesScanActivity` (declared `exported=false` in the SDK manifest — launch by class name from within the package).
- After scan finds a device, SDK logs `setConfiguredGlasses: ->MAC, ->name` and flips an internal `_autoConnect=true`, then BleDevice connects.
- `Evs.instance().comm().hasConfiguredDevice()` — true once paired; gates whether to launch the scan activity again.

**Teardown:**
```kotlin
Evs.instance().unregisterAppEvents(listener)
Evs.instance().stop()
```

**`IEvsAppEvents` methods** (abstract = must implement; open = optional override):
```kotlin
override fun onReady()    // glasses connected and rendering — abstract
override fun onUnReady()  // glasses disconnected  ← NOT onDisconnected() — abstract
override fun onError(errCode: AppErrorCode, description: String)  // abstract
override fun onBeginAuth(serial: String, fwVersion: Int)  // open — fires when glasses
                                                          // found and auth starts; good
                                                          // for debugging connection
```

**`PopupMessage` constructor:**
```kotlin
PopupMessage(uiElement: UIElement, alignV: AlignV, timeoutMs: Int)
// e.g. PopupMessage(label, AlignV.center, 5000)
// Second arg is AlignV enum (top/center/bottom), NOT a Float position
// Third arg is Int milliseconds, NOT Long
```

**`AndroidManifest.xml`** — Everysight SDK declares its own theme; override it. The SDK's
own manifest omits several permissions and a foreground-service type that are required on
Android 14+ (and especially Android 16/API 36) — without these the SDK silently scans nothing:
```xml
<manifest xmlns:android="..." xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <application ... tools:replace="android:theme">
        <!-- Add the foregroundServiceType the SDK's own manifest forgot. -->
        <service
            android:name="com.everysight.evskit.android.internal.ui.preview.PreviewService"
            android:foregroundServiceType="connectedDevice"
            tools:node="merge" />
    </application>
```
Request `POST_NOTIFICATIONS` at runtime too (Android 13+ = `Build.VERSION_CODES.TIRAMISU`).

**ApiKey file** — the Everysight developer portal delivers `sdk.<serial>.key` inside a folder.
Put it as a **flat file** at the assets root, keeping the original name:
```
android-app/app/src/main/assets/sdk.<serial>.key
```
The SDK's documented auto-load looks for `app.key`, `sdk.<serial>.key`, or `sdk.key` (in that
order) in assets. In practice the auto-load wasn't firing on Android 16 / SDK 2.6.1 for us, so
`EverysightManager.start()` reads the bytes and calls `auth().setApiKey(bytes)` explicitly.

### Mile-split popup

At each auto-lap the watch calls `onTimerLap()`, which diffs `_lastDist`/`_lastTime` against `_lapStartDist`/`_lapStartTime` to compute the lap's average pace in min/mile, then transmits `{"lap_pace": "M:SS"}` as a separate BLE message. Android routes it: `ConnectIQManager.lapSplit (SharedFlow)` → `MainViewModel` → `EverysightManager.showLapSplit()` → `RunStatsScreen.showLapSplit()` → `PopupMessage(label, AlignV.center, 5000)` on the HUD.

### Everysight SDK setup (one-time, per dev machine)

SDK version: **2.6.1**, hosted on GitHub Packages.

1. Create a GitHub PAT with `read:packages` scope at github.com → Settings → Developer Settings → Personal Access Tokens
2. Add to `~/.gradle/gradle.properties` (not committed):
   ```
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.token=YOUR_GITHUB_PAT
   ```
3. Place the `sdk.<serial>.key` file (from everysight.com/developer) as a **flat file** at
   `android-app/app/src/main/assets/sdk.<serial>.key` (keep the original name; do not wrap in
   a folder, do not rename to `ApiKey`). See ApiKey note in SDK API section.
   `EverysightManager.kt` holds the filename in `API_KEY_ASSET` and the BLE name in `GLASSES_NAME`
   — update both when targeting a different developer kit.

Maven repo: `https://maven.pkg.github.com/everysight-maverick/m1-android-maven`
Dependencies: `com.everysight:evskit:2.6.1`, `com.everysight:nativeevskit:2.6.1`

## Android build config

- Java 21 required — Java 25 breaks Kotlin DSL. Path set in `android-app/gradle.properties`
- Gradle 8.13, AGP 8.13.2, Kotlin 2.3.21
- `compileSdk = 35`, `minSdk = 25`, `targetSdk = 34`
  - compileSdk 35 required by `play-services-location` transitive deps
  - minSdk 25 required by Everysight evskit SDK
- Use `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` — `kotlinOptions` is deprecated in this version
- Android SDK at `~/Library/Android/sdk` (set in `android-app/local.properties`)
- JVM heap: `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m` (in `android-app/gradle.properties`)

### Gitignored files — must copy to worktrees manually

`android-app/gradle.properties`, `android-app/local.properties`, and `android-app/gradle/wrapper/gradle-wrapper.jar` are all gitignored. When working in a git worktree, copy them before building:
```
WORKTREE=.claude/worktrees/<name>/android-app
cp android-app/gradle.properties android-app/local.properties "$WORKTREE/"
cp android-app/gradle/wrapper/gradle-wrapper.jar "$WORKTREE/gradle/wrapper/"
```

## In-progress work

### Phase 1 — Street name HUD overlay (branch: `worktree-street-name-phase1`)

**Status:** Working end-to-end on Samsung Android 16 (API 36) + Everysight Maverick "EV0519".

Uses Android's own GPS (`FusedLocationProviderClient`) + `Geocoder` to reverse-geocode position and show the current street name as a white floating overlay at the top of the glasses HUD. No watch-side changes required.

#### Glasses connection fix (resolved 2026-05-18)

The earlier hypotheses (ApiKey path, Android 16 foreground-service permissions) were partial — none of them alone unblocked the connection. The actual fix was multiple pieces together:

1. **API key delivered programmatically.** Auto-load of `assets/sdk.<serial>.key` didn't fire reliably; we now read the bytes and call `Evs.instance().auth().setApiKey(bytes)` explicitly. Asset is at `assets/sdk.255202400519.key` (flat file at the assets root — not wrapped in a folder, not renamed to `ApiKey`).
2. **Manifest additions** (`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`) and a `foregroundServiceType="connectedDevice"` merge for the SDK's `PreviewService`. The SDK's own manifest omits these.
3. **First-time device discovery via the SDK's bundled `EvsGlassesScanActivity`.** `startExt(names)` does NOT initiate a scan when no device is configured — it only auto-connects to a previously-paired device. The bundled scan activity (declared `exported=false` in the SDK manifest) is launched by class name; it scans, displays found devices, and persists the chosen MAC into the SDK. Subsequent launches see `comm().hasConfiguredDevice() == true` and auto-reconnect without re-pairing.

Symptom that was misleading: `Evs.instance().start()` and `startExt(set)` both returned `true` while the SDK silently did nothing — the SDK's "started" state and "actually scanning/connecting" state are decoupled.

### Phase 2 — AR ground-plane street label (not yet started)

Full design in `docs/street-name-ar-hud-plan.md`. Uses Everysight LOS Kit (`ArScreen` + `ArWindow` in ENU world space) to project the street name onto the road surface ahead of the runner. Always-on, user-configurable distance (5/10/15/20m via spinner in app + long-press on glasses to cycle). User-togglable alongside Phase 1 HUD strip.

## Sideloading troubleshooting

- No "new app detected" notification: uninstall the old version via watch Settings → Connect IQ Store → Installed, then copy the .prg again
- Watch app not visible: DataFields don't appear in the app list. They only appear in the data screen field picker during an activity
- CIQ log at `watch-app/CIQ_LOG.BAK` after a crash — check `Details:` and `Stack:` lines
- Version the app name (e.g. "Enduro Data v6") to match log entries to specific builds
