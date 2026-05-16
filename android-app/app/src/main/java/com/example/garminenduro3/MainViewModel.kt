package com.example.garminenduro3

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val WATCH_APP_UUID = "e5f4a3b2c1d04e5f6a7b8c9d0e1f2a3b"
    }

    private val manager = ConnectIQManager.getInstance(application)

    val sdkState = manager.sdkState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectIQManager.SdkState.NOT_INITIALIZED
    )
    val devices = manager.devices.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val messages = manager.messages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val errorMessage = manager.errorMessage.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )
    val runStats = manager.runStats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectIQManager.RunStats()
    )

    fun initialize() = manager.initialize()

    fun listenToDevice(device: IQDevice) = manager.watchForAppEvents(device, WATCH_APP_UUID)

    override fun onCleared() {
        super.onCleared()
        manager.shutdown()
    }
}
