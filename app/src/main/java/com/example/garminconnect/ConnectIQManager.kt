package com.example.garminconnect

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectIQManager private constructor(private val context: Context) {

    enum class SdkState { IDLE, INITIALIZING, READY, ERROR, SHUTDOWN }

    private val _sdkState = MutableStateFlow(SdkState.IDLE)
    val sdkState: StateFlow<SdkState> = _sdkState.asStateFlow()

    private val _devices = MutableStateFlow<List<IQDevice>>(emptyList())
    val devices: StateFlow<List<IQDevice>> = _devices.asStateFlow()

    private val _deviceStatuses = MutableStateFlow<Map<Long, IQDevice.IQDeviceStatus>>(emptyMap())
    val deviceStatuses: StateFlow<Map<Long, IQDevice.IQDeviceStatus>> = _deviceStatuses.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var connectIQ: ConnectIQ? = null

    fun initialize() {
        if (_sdkState.value == SdkState.READY) {
            refreshDevices()
            return
        }
        _sdkState.value = SdkState.INITIALIZING
        connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ?.initialize(context, true, object : ConnectIQ.ConnectIQListener {
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                _sdkState.value = SdkState.ERROR
                appendLog("SDK init error: $errStatus")
            }

            override fun onSdkReady() {
                _sdkState.value = SdkState.READY
                appendLog("SDK ready")
                refreshDevices()
            }

            override fun onSdkShutDown() {
                _sdkState.value = SdkState.SHUTDOWN
                appendLog("SDK shut down")
            }
        })
    }

    fun refreshDevices() {
        try {
            val known = connectIQ?.knownDevices ?: emptyList()
            _devices.value = known
            appendLog("Found ${known.size} device(s)")
            known.forEach { device ->
                try {
                    connectIQ?.registerForDeviceEvents(device) { dev, status ->
                        _deviceStatuses.value = _deviceStatuses.value + (dev.deviceIdentifier to status)
                        appendLog("${dev.friendlyName}: $status")
                    }
                } catch (_: InvalidStateException) {}
            }
        } catch (_: InvalidStateException) {
            appendLog("Error: SDK not ready")
        } catch (_: ServiceUnavailableException) {
            appendLog("Error: Service unavailable — is Garmin Connect running?")
        }
    }

    // Looks up a Connect IQ app by UUID on the given device, then sends a message to it.
    fun sendMessage(device: IQDevice, appUuid: String, message: Any) {
        resolveApp(device, appUuid) { app ->
            try {
                connectIQ?.sendMessage(device, app, message, object : ConnectIQ.IQSendMessageListener {
                    override fun onMessageStatus(dev: IQDevice, a: IQApp, status: ConnectIQ.IQMessageStatus) {
                        appendLog("Send → ${dev.friendlyName}: $status")
                    }
                })
            } catch (e: InvalidStateException) {
                appendLog("Send error: SDK not ready")
            } catch (e: ServiceUnavailableException) {
                appendLog("Send error: service unavailable")
            }
        }
    }

    fun openApp(device: IQDevice, appUuid: String) {
        resolveApp(device, appUuid) { app ->
            try {
                connectIQ?.openApplication(device, app) { dev, _, status ->
                    appendLog("Open on ${dev.friendlyName}: $status")
                }
                registerForAppEvents(device, app)
            } catch (_: InvalidStateException) {}
        }
    }

    private fun resolveApp(device: IQDevice, appUuid: String, onFound: (IQApp) -> Unit) {
        try {
            connectIQ?.getApplicationInfo(appUuid, device, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) = onFound(app)
                override fun onApplicationNotInstalled(applicationId: String) {
                    appendLog("App $applicationId not installed on ${device.friendlyName}")
                }
            })
        } catch (_: InvalidStateException) {
            appendLog("Error: SDK not ready")
        }
    }

    private fun registerForAppEvents(device: IQDevice, app: IQApp) {
        try {
            connectIQ?.registerForAppEvents(device, app, object : ConnectIQ.IQApplicationEventListener {
                override fun onMessageReceived(
                    dev: IQDevice,
                    a: IQApp,
                    message: List<Any>,
                    status: ConnectIQ.IQMessageStatus
                ) {
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                        appendLog("← ${dev.friendlyName}: ${message.joinToString()}")
                    }
                }
            })
        } catch (_: InvalidStateException) {}
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun shutdown() {
        try {
            connectIQ?.shutdown(context)
        } catch (_: InvalidStateException) {}
    }

    private fun appendLog(msg: String) {
        _log.value = (_log.value + msg).takeLast(100)
    }

    companion object {
        @Volatile
        private var instance: ConnectIQManager? = null

        fun getInstance(context: Context): ConnectIQManager =
            instance ?: synchronized(this) {
                instance ?: ConnectIQManager(context.applicationContext).also { instance = it }
            }
    }
}
