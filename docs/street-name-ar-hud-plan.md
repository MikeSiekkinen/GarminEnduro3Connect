# Street Name HUD + AR Ground Label — Phase 1 & 2

## Context

The Garmin Enduro 3 watch has GPS but the ConnectIQ API does not expose on-watch map data or geocoding. The Android companion app already receives BLE run stats and forwards them to Everysight Maverick AR glasses. This plan adds street name awareness: first as a simple floating HUD overlay (Phase 1), then as an immersive AR label projected onto the road surface (Phase 2).

Key design decision: Android's own GPS (`FusedLocationProviderClient`) is used for geocoding rather than piggybacking on the Garmin BLE payload. This avoids any watch-side changes in Phase 1 and gives 1Hz location updates for smooth AR positioning in Phase 2. `ACCESS_FINE_LOCATION` is already declared in the manifest (it was added for legacy BLE scanning).

---

## Phase 1 — Floating Street Name HUD Overlay

**Goal:** Display current street name as a white floating overlay centered at the top of the glasses HUD, above the existing 2×2 run stats grid. No watch changes required.

### Files to modify

**`android-app/app/build.gradle.kts`**
- Add `implementation("com.google.android.gms:play-services-location:21.3.0")`

**`android-app/app/src/main/java/com/example/garminenduro3/MainActivity.kt`**
- In `requestBluetoothPermissionsOrInit()`, always add `Manifest.permission.ACCESS_FINE_LOCATION` to the needed list (currently it's only added for Android < 12; geocoding needs it on all versions).
- After permissions granted, call `viewModel.startStreetNameUpdates()`.

**`android-app/app/src/main/java/com/example/garminenduro3/MainViewModel.kt`**
- Inject `Application` context (change to `AndroidViewModel`).
- Instantiate `StreetNameProvider(context)`.
- In `init {}`, add a third coroutine: collect `streetNameProvider.streetName` → call `everysightManager.updateStreetName(it)`.
- Expose `fun startStreetNameUpdates()` that calls `streetNameProvider.start()`.
- In `onCleared()`, call `streetNameProvider.stop()`.

**`android-app/app/src/main/java/com/example/garminenduro3/EverysightManager.kt`**
- Add `fun updateStreetName(name: String)` that calls `runStatsScreen?.updateStreetName(name)`.

**`android-app/app/src/main/java/com/example/garminenduro3/RunStatsScreen.kt`**
- In `onCreate()`, add a centered `Text` widget at the top of the screen (y ≈ 5% of height, x = center). Color: white. Font: small. Initial text: `""`.
- Add `fun updateStreetName(name: String)` that sets this text widget's value. If name is blank, set to `""`.
- Add `fun setStreetNameVisible(visible: Boolean)` (used by Phase 2 toggle).

### Files to create

**`android-app/app/src/main/java/com/example/garminenduro3/StreetNameProvider.kt`**
```
class StreetNameProvider(context: Context) {
    val streetName: StateFlow<String>  // current street name, "" if unknown

    fun start()   // begins FusedLocationProviderClient updates at 5-sec interval
    fun stop()    // removes updates

    // Internal: on each location update, if moved >10m call geocode()
    // geocode(): Dispatchers.IO, Geocoder.getFromLocation(lat, lon, 1)
    //   → addresses[0].thoroughfare ?: ""
    //   → emit to streetName
    // Also exposes: val lastLocation: Location? and val lastBearing: Float
    //   (both used by Phase 2)
}
```
- Use `FusedLocationProviderClient` with `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMinUpdateDistanceMeters(10f)`.
- Geocode only when distance moved > 10m (avoids hammering geocoder while standing still).
- Handle `Geocoder.isPresent() == false` gracefully (emit `""`).
- Handle both API 33+ async callback and older blocking call (run blocking on `Dispatchers.IO`).

### Verification
1. Build + install APK (`./gradlew assembleDebug && adb install -r ...`).
2. Grant location permission when prompted.
3. Connect glasses. Walk/run outside. Confirm street name appears at top of HUD and updates as you move between streets.
4. Walk indoors or deny location — confirm overlay shows `""` gracefully.

---

## Phase 2 — AR Ground-Plane Street Label

**Goal:** Replace (or complement, user-togglable) the HUD overlay with an always-on street name label projected onto the road surface ahead of the runner, visible when glancing down. Uses the Everysight LOS Kit (`ArScreen`).

### Settings added to Android app

**`SharedPreferences` key: `"ar_distance_meters"` (Float, default 10f)**
- Options surfaced in UI: 5m / 10m / 15m / 20m.

**`SharedPreferences` key: `"show_hud_street_name"` (Boolean, default true)**
- Controls Phase 1 HUD strip visibility alongside Phase 2 AR label.

### Files to create

**`android-app/app/src/main/java/com/example/garminenduro3/ArStreetLabelScreen.kt`**

Extends `ArScreen`. Responsibilities:
- `onCreate()`: create one `ArWindow` containing a single `Text` widget (white, medium font, text aligned center).
- `onUpdateUI()`: wait for `quat.isValid` before placing in world space (same guard as the SDK AR sample).
- `fun setStreetName(name: String)`: updates the `Text` widget inside the `ArWindow`.
- `fun setDistance(meters: Float)`: stores distance; triggers a repositioning.
- `fun updateLocation(lat: Double, lon: Double, bearingDeg: Float)`: recomputes ENU position and reorients the `ArWindow`.

**ENU placement math** (called from `updateLocation`):
```kotlin
val headingRad = Math.toRadians(bearingDeg.toDouble()).toFloat()
val east  = distanceMeters * sin(headingRad)   // X
val north = distanceMeters * cos(headingRad)   // Y
val up    = -1.7f                              // Z — ground level below eye
arWindow.setPosition(east, north, up)
// Rotate to lay flat (face skyward) then align with road direction:
// rotate 90° around X (East) axis → panel faces up
// rotate -bearingDeg around Z (Up) axis → text aligned with road
// Exact API: ArWindow likely uses setRotation(quaternion) or rotate(angle, ax, ay, az)
// Use M.RotationX(90°) * M.RotationZ(-bearingDeg) — see M utility class
```

> **Note:** `ArWindow` has a `Geo` constructor suggesting GPS-native placement. Verify at implementation time — if `Geo(lat, lon, alt)` is supported, use it instead of manual ENU math (SDK handles the transform internally).

> **Note:** The LOS Kit is marked Beta. Test `quat.isValid` guard carefully; place the `ArWindow` only after quaternion is ready.

**`android-app/app/src/main/java/com/example/garminenduro3/ArSettings.kt`**
```
object ArSettings {
    val DISTANCE_OPTIONS = listOf(5f, 10f, 15f, 20f)  // meters

    fun getDistance(prefs: SharedPreferences): Float
    fun setDistance(prefs: SharedPreferences, meters: Float)
    fun isHudStreetNameVisible(prefs: SharedPreferences): Boolean
    fun setHudStreetNameVisible(prefs: SharedPreferences, visible: Boolean)
}
```

### Files to modify

**`android-app/app/src/main/java/com/example/garminenduro3/EverysightManager.kt`**
- Add `private var arStreetLabelScreen: ArStreetLabelScreen? = null`.
- In `start()`, after adding `RunStatsScreen`, also create and add `ArStreetLabelScreen` (add it first so it renders behind the HUD).
- Add `fun updateStreetNameAr(name: String)` → forwards to `arStreetLabelScreen`.
- Add `fun updateLocationForAr(lat: Double, lon: Double, bearing: Float)` → forwards to `arStreetLabelScreen`.
- Add `fun setArDistance(meters: Float)` → forwards to `arStreetLabelScreen`.
- Add `fun setHudStreetNameVisible(visible: Boolean)` → forwards to `runStatsScreen`.
- In `stop()`, clean up `arStreetLabelScreen`.

**`android-app/app/src/main/java/com/example/garminenduro3/StreetNameProvider.kt`**
- Expose `val lastBearing: Float` (from `Location.bearing`) alongside `streetName`.
- Emit bearing updates to `MainViewModel` (or via a combined data class).

**`android-app/app/src/main/java/com/example/garminenduro3/MainViewModel.kt`**
- Collect `streetNameProvider` location+bearing → call `everysightManager.updateLocationForAr(lat, lon, bearing)`.
- Expose `fun setArDistance(meters: Float)` → `prefs.save` + `everysightManager.setArDistance(meters)`.
- Expose `fun setHudStreetNameVisible(visible: Boolean)` → `prefs.save` + `everysightManager.setHudStreetNameVisible(visible)`.
- Load saved prefs and apply at `connectGlasses()` time.

**`android-app/app/src/main/java/com/example/garminenduro3/MainActivity.kt`**
- Add a `Spinner` (or `SegmentedButton`) labeled "AR Distance" with options 5m / 10m / 15m / 20m. Wire to `viewModel.setArDistance()`. Initialize from saved pref.
- Add a `Switch` labeled "Show street name in HUD". Wire to `viewModel.setHudStreetNameVisible()`. Initialize from saved pref.
- These controls can be added below the existing "Connect Glasses" button in the layout.

**Long-press gesture on glasses (distance cycling):**
- In `ArStreetLabelScreen.onTouch()`, listen for `TouchDirection.longPress` (verify this enum value exists in SDK 2.6.1; if not, accumulate tap-hold duration manually).
- On long-press: cycle to next distance in `ArSettings.DISTANCE_OPTIONS`, call `setDistance()`, persist via callback to `MainViewModel`.
- Requires a callback reference from `ArStreetLabelScreen` back to the ViewModel (pass as constructor lambda).

### Verification
1. Build + install. Connect glasses. Run outside with phone.
2. Walk forward — glance down 10–15°. Confirm white street name text appears painted on the road at selected distance ahead.
3. Turn a corner — confirm text repositions to align with new heading within 1–2 location updates.
4. Open app, change spinner from 10m to 5m — confirm text appears closer.
5. Long-press glasses touchpad — confirm distance cycles through 5/10/15/20m.
6. Toggle HUD street name switch — confirm top overlay disappears/reappears while AR label persists.
7. Test with glasses disconnected — confirm graceful no-op (no crash).
