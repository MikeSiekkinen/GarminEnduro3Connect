package com.example.garminenduro3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garminenduro3.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val deviceAdapter = DeviceAdapter { device ->
        viewModel.listenToDevice(device)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.initialize()
        } else {
            updateStatus("Bluetooth permission denied — cannot connect to watch")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.sdkState.collect { onSdkState(it) } }
                launch { viewModel.devices.collect { onDevices(it) } }
                launch { viewModel.messages.collect { onMessages(it) } }
                launch { viewModel.runStats.collect { onRunStats(it) } }
                launch { viewModel.errorMessage.collect { msg -> msg?.let { updateStatus(it) } } }
            }
        }

        requestBluetoothPermissionsOrInit()
    }

    private fun requestBluetoothPermissionsOrInit() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) viewModel.initialize()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private var _sdkReady = false

    private fun onSdkState(state: ConnectIQManager.SdkState) {
        _sdkReady = state == ConnectIQManager.SdkState.READY
        updateStatus("SDK: ${state.name}")
        refreshEmptyView()
    }

    private fun onDevices(devices: List<ConnectIQManager.DeviceInfo>) {
        deviceAdapter.submitList(devices)
        refreshEmptyView()
    }

    private fun refreshEmptyView() {
        val show = _sdkReady && deviceAdapter.itemCount == 0
        binding.textEmptyDevices.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun onRunStats(stats: ConnectIQManager.RunStats) {
        binding.textPaceValue.text = stats.pace
        binding.textDistValue.text = stats.distKm
        binding.textTimeValue.text = stats.elapsed
        binding.textHrValue.text = stats.heartRate
    }

    private fun onMessages(messages: List<String>) {
        if (messages.isEmpty()) return
        binding.textMessages.text = messages.takeLast(20).joinToString("\n")
        binding.scrollMessages.post { binding.scrollMessages.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateStatus(text: String) {
        binding.textStatus.text = text
    }
}
