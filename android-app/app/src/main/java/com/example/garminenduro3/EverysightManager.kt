package com.example.garminenduro3

import android.content.Context
import android.content.Intent
import UIKit.services.AppErrorCode
import UIKit.services.IEvsAppEvents
import android.util.Log
import com.everysight.evskit.android.Evs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EverysightManager private constructor(private val context: Context) {

    enum class GlassesState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _glassesState = MutableStateFlow(GlassesState.DISCONNECTED)
    val glassesState: StateFlow<GlassesState> = _glassesState

    @Volatile private var screen: RunStatsScreen? = null
    private var appEvents: IEvsAppEvents? = null

    // True once Evs.init() has run in this process. Guards teardown: Evs.instance()
    // throws NPE("instance is called before init") if init() was never called, so
    // stop() must be a no-op when the glasses were never connected (the common
    // watch-only-HUD-then-exit path would otherwise crash on ViewModel teardown).
    @Volatile private var initialized = false

    // Latest values pushed to the HUD, cached on this (process-lifetime) singleton so a
    // reconnect can repaint a freshly-created screen immediately instead of flashing
    // placeholders until the next watch message / geocode arrives. The screen is
    // recreated on every onReady(); this cache outlives it.
    @Volatile private var lastPace = DEFAULT_PACE
    @Volatile private var lastDist = DEFAULT_DIST
    @Volatile private var lastElapsed = DEFAULT_ELAPSED
    @Volatile private var lastHr = DEFAULT_HR
    @Volatile private var lastStreetName = ""
    @Volatile private var streetVisible = true

    fun start() {
        // Idempotent: ignore taps while connecting or already connected.
        val state = _glassesState.value
        if (state == GlassesState.CONNECTING || state == GlassesState.CONNECTED) return
        // Re-entry from DISCONNECTED/ERROR (e.g. a mid-run reconnect after the glasses
        // dropped out of range): tear down any prior listener/screen first so we never
        // leak a listener or stack a duplicate screen on the SDK.
        teardown()

        _glassesState.value = GlassesState.CONNECTING
        Evs.init(context)
        initialized = true
        val events = object : IEvsAppEvents {
            override fun onReady() {
                _glassesState.value = GlassesState.CONNECTED
                // Seed the new screen with the last-known values so a reconnect repaints
                // immediately rather than flashing placeholders. On the first connect the
                // defaults are used, which equal the screen's own placeholder strings.
                val s = RunStatsScreen(
                    lastPace, lastDist, lastElapsed, lastHr, lastStreetName, streetVisible
                )
                screen = s
                Evs.instance().screens().addScreen(s)
            }
            override fun onUnReady() {
                _glassesState.value = GlassesState.DISCONNECTED
                screen = null
            }
            override fun onError(errCode: AppErrorCode, description: String) {
                Log.e("EverysightManager", "onError errCode=$errCode description=$description")
                _glassesState.value = GlassesState.ERROR
                screen = null
            }
        }
        appEvents = events
        Evs.instance().registerAppEvents(events)

        // The SDK's auto-load of assets/sdk.[serial].key doesn't fire reliably; feed
        // the key bytes in directly.
        runCatching { context.assets.open(API_KEY_ASSET).use { it.readBytes() } }
            .onSuccess { Evs.instance().auth().setApiKey(it) }
            .onFailure {
                // A missing/unreadable key means the glasses will never authenticate.
                // Surface it loudly (ERROR state + clear log) instead of silently
                // appearing to start while auth never happens.
                Log.e(
                    "EverysightManager",
                    "API key asset '$API_KEY_ASSET' not found — glasses will not authenticate", it
                )
                _glassesState.value = GlassesState.ERROR
            }

        Evs.instance().startExt(hashSetOf(GLASSES_NAME))

        // startExt alone doesn't trigger a scan or reconnect; launching the SDK's
        // built-in scan activity is what actually causes BleDevice to connect. It
        // shows a brief discovery UI on first pair and reconnects automatically to
        // a remembered device on subsequent runs.
        try {
            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    "com.everysight.evskit.android.internal.ui.EvsGlassesScanActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("EverysightManager", "Failed to launch EvsGlassesScanActivity", e)
        }
    }

    fun updateStats(pace: String, dist: String, elapsed: String, hr: String) {
        lastPace = pace
        lastDist = dist
        lastElapsed = elapsed
        lastHr = hr
        screen?.update(pace, dist, elapsed, hr)
    }

    fun showLapSplit(lapPace: String) {
        screen?.showLapSplit(lapPace)
    }

    fun updateStreetName(name: String) {
        lastStreetName = name
        screen?.updateStreetName(name)
    }

    fun setStreetNameVisible(visible: Boolean) {
        streetVisible = visible
        screen?.setStreetNameVisible(visible)
    }

    fun stop() {
        teardown()
        _glassesState.value = GlassesState.DISCONNECTED
    }

    // Unregister the app-events listener and drop the screen. No-op when the SDK was
    // never initialized, so exiting the app without ever connecting cannot crash on
    // Evs.instance() (which throws before init()). Safe to call repeatedly.
    private fun teardown() {
        if (!initialized) return
        appEvents?.let { Evs.instance().unregisterAppEvents(it) }
        Evs.instance().stop()
        screen = null
        appEvents = null
    }

    companion object {
        private const val API_KEY_ASSET = "sdk.255202400519.key"
        private const val GLASSES_NAME = "EV0519"

        private const val DEFAULT_PACE = "--:--"
        private const val DEFAULT_DIST = "-.--"
        private const val DEFAULT_ELAPSED = "-:--:--"
        private const val DEFAULT_HR = "--"

        @Volatile private var instance: EverysightManager? = null
        fun getInstance(context: Context): EverysightManager =
            instance ?: synchronized(this) {
                instance ?: EverysightManager(context.applicationContext).also { instance = it }
            }
    }
}
