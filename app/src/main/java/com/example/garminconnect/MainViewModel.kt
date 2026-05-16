package com.example.garminconnect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ConnectIQManager.getInstance(application)

    val sdkState = manager.sdkState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectIQManager.SdkState.IDLE
    )

    val devices = manager.devices.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val deviceStatuses = manager.deviceStatuses.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap()
    )

    val log = manager.log.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun initialize() = manager.initialize()
    fun refresh() = manager.refreshDevices()
    fun clearLog() = manager.clearLog()

    fun sendTestMessage(device: IQDevice, appUuid: String) {
        manager.sendMessage(device, appUuid, mapOf("action" to "ping", "from" to "Android"))
    }

    fun openApp(device: IQDevice, appUuid: String) = manager.openApp(device, appUuid)

    override fun onCleared() {
        super.onCleared()
        manager.shutdown()
    }
}
