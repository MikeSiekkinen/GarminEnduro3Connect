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

    fun start() {
        _glassesState.value = GlassesState.CONNECTING
        Evs.init(context)
        val events = object : IEvsAppEvents {
            override fun onReady() {
                _glassesState.value = GlassesState.CONNECTED
                val s = RunStatsScreen()
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
            .onFailure { Log.e("EverysightManager", "Failed to read $API_KEY_ASSET", it) }

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
        screen?.update(pace, dist, elapsed, hr)
    }

    fun showLapSplit(lapPace: String) {
        screen?.showLapSplit(lapPace)
    }

    fun updateStreetName(name: String) {
        screen?.updateStreetName(name)
    }

    fun setStreetNameVisible(visible: Boolean) {
        screen?.setStreetNameVisible(visible)
    }

    fun stop() {
        appEvents?.let { Evs.instance().unregisterAppEvents(it) }
        Evs.instance().stop()
        screen = null
        appEvents = null
        _glassesState.value = GlassesState.DISCONNECTED
    }

    companion object {
        private const val API_KEY_ASSET = "sdk.255202400519.key"
        private const val GLASSES_NAME = "EV0519"

        @Volatile private var instance: EverysightManager? = null
        fun getInstance(context: Context): EverysightManager =
            instance ?: synchronized(this) {
                instance ?: EverysightManager(context.applicationContext).also { instance = it }
            }
    }
}
