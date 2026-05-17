package com.example.garminenduro3

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConnectIQManager private constructor(private val context: Context) {

    enum class SdkState { NOT_INITIALIZED, INITIALIZING, READY, ERROR, SHUTDOWN }

    data class DeviceInfo(
        val device: IQDevice,
        val status: IQDevice.IQDeviceStatus,
        val app: IQApp? = null,
    )

    data class RunStats(
        val pace: String = "--:--",
        val distMi: String = "-.--",
        val elapsed: String = "-:--:--",
        val heartRate: String = "--",
    )

    private val _sdkState = MutableStateFlow(SdkState.NOT_INITIALIZED)
    val sdkState: StateFlow<SdkState> = _sdkState.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _runStats = MutableStateFlow(RunStats())
    val runStats: StateFlow<RunStats> = _runStats.asStateFlow()

    private val _lapSplit = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val lapSplit: SharedFlow<String> = _lapSplit.asSharedFlow()

    private var connectIQ: ConnectIQ? = null

    fun initialize() {
        val state = _sdkState.value
        if (state == SdkState.INITIALIZING || state == SdkState.READY) return
        _sdkState.value = SdkState.INITIALIZING

        connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ?.initialize(context, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                _sdkState.value = SdkState.READY
                loadDevices()
            }

            override fun onSdkShutDown() {
                _sdkState.value = SdkState.SHUTDOWN
            }

            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                _sdkState.value = SdkState.ERROR
                _errorMessage.value = "SDK init failed: ${errStatus.name}"
            }
        })
    }

    private fun loadDevices() {
        try {
            val knownDevices = connectIQ?.getKnownDevices() ?: return
            _devices.value = knownDevices.map { device ->
                DeviceInfo(
                    device = device,
                    status = safeGetStatus(device),
                )
            }
            knownDevices.forEach { watchDeviceStatus(it) }
        } catch (e: InvalidStateException) {
            _errorMessage.value = "SDK not ready: ${e.message}"
        } catch (e: ServiceUnavailableException) {
            _errorMessage.value = "Service unavailable: ${e.message}"
        }
    }

    private fun safeGetStatus(device: IQDevice): IQDevice.IQDeviceStatus =
        try { connectIQ?.getDeviceStatus(device) ?: IQDevice.IQDeviceStatus.NOT_CONNECTED }
        catch (e: Exception) { IQDevice.IQDeviceStatus.NOT_CONNECTED }

    private fun watchDeviceStatus(device: IQDevice) {
        try {
            connectIQ?.registerForDeviceEvents(device, object : ConnectIQ.IQDeviceEventListener {
                override fun onDeviceStatusChanged(dev: IQDevice, newStatus: IQDevice.IQDeviceStatus) {
                    _devices.update { list ->
                        list.map { info ->
                            if (info.device.deviceIdentifier == dev.deviceIdentifier)
                                info.copy(device = dev, status = newStatus)
                            else info
                        }
                    }
                }
            })
        } catch (e: InvalidStateException) { /* SDK not yet ready */ }
    }

    fun watchForAppEvents(device: IQDevice, appUuid: String) {
        try {
            connectIQ?.getApplicationInfo(appUuid, device, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    _devices.update { list ->
                        list.map { info ->
                            if (info.device.deviceIdentifier == device.deviceIdentifier)
                                info.copy(app = app)
                            else info
                        }
                    }
                    registerForMessages(device, app)
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    appendMessage("Watch app not installed on ${device.friendlyName}")
                }
            })
        } catch (e: InvalidStateException) {
            appendMessage("Error: SDK not ready")
        }
    }

    private fun registerForMessages(device: IQDevice, app: IQApp) {
        try {
            connectIQ?.registerForAppEvents(device, app, object : ConnectIQ.IQApplicationEventListener {
                override fun onMessageReceived(
                    dev: IQDevice,
                    receivedApp: IQApp,
                    messageData: List<Any>,
                    status: ConnectIQ.IQMessageStatus,
                ) {
                    appendMessage("rx status=${status.name} items=${messageData.size}")
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) return

                    @Suppress("UNCHECKED_CAST")
                    val map = messageData.firstOrNull() as? Map<*, *>
                    if (map != null) {
                        val lapPace = map["lap_pace"] as? String
                        if (lapPace != null) {
                            _lapSplit.tryEmit(lapPace)
                            appendMessage("lap split: $lapPace /mi")
                        } else {
                            _runStats.value = RunStats(
                                pace      = map["pace"] as? String ?: "--:--",
                                distMi    = map["dist"] as? String ?: "-.--",
                                elapsed   = map["time"] as? String ?: "-:--:--",
                                heartRate = map["hr"]   as? String ?: "--",
                            )
                            appendMessage("pace=${map["pace"]}  dist=${map["dist"]}mi  hr=${map["hr"]}")
                        }
                    } else {
                        appendMessage(messageData.joinToString(", ") { it.toString() })
                    }
                }
            })
            appendMessage("Listening on ${device.friendlyName}")
        } catch (e: InvalidStateException) { /* ignore */ }
    }

    private fun appendMessage(msg: String) {
        _messages.update { it + msg }
    }

    fun shutdown() {
        try {
            connectIQ?.shutdown(context)
        } catch (e: InvalidStateException) { /* already shut down */ }
    }

    companion object {
        @Volatile private var instance: ConnectIQManager? = null

        fun getInstance(context: Context): ConnectIQManager =
            instance ?: synchronized(this) {
                instance ?: ConnectIQManager(context.applicationContext).also { instance = it }
            }
    }
}
