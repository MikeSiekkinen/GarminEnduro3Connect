package com.example.garminenduro3

import android.content.Context
import UIKit.services.AppErrorCode
import UIKit.services.IEvsAppEvents
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
                _glassesState.value = GlassesState.ERROR
                screen = null
            }
        }
        appEvents = events
        Evs.instance().registerAppEvents(events)
        Evs.instance().start()
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
        @Volatile private var instance: EverysightManager? = null
        fun getInstance(context: Context): EverysightManager =
            instance ?: synchronized(this) {
                instance ?: EverysightManager(context.applicationContext).also { instance = it }
            }
    }
}
