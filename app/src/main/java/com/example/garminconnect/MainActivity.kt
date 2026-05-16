package com.example.garminconnect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garminconnect.databinding.ActivityMainBinding
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Replace with the UUID of the Connect IQ app you want to communicate with.
    // Find it in your app's manifest.xml on the watch side, or leave as-is to test
    // device discovery without app-level messaging.
    private val TARGET_APP_UUID = "a3421fee-d289-106a-538c-b9547ab12095"

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.initialize()
        } else {
            binding.tvStatus.text = "Bluetooth permissions denied — grant in Settings to continue"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        adapter = DeviceAdapter(
            onSendClick = { device -> viewModel.sendTestMessage(device, TARGET_APP_UUID) },
            onOpenClick = { device -> viewModel.openApp(device, TARGET_APP_UUID) },
        )

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnClearLog.setOnClickListener { viewModel.clearLog() }

        observeState()
        checkPermissionsAndInit()
    }

    private fun checkPermissionsAndInit() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

        val allGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) viewModel.initialize() else permissionLauncher.launch(required)
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.sdkState.collect { state ->
                binding.tvStatus.text = "SDK: ${state.name}"
            }
        }

        lifecycleScope.launch {
            viewModel.devices.collect { refreshList() }
        }

        lifecycleScope.launch {
            viewModel.deviceStatuses.collect { refreshList() }
        }

        lifecycleScope.launch {
            viewModel.log.collect { entries ->
                binding.tvMessages.text = entries.joinToString("\n")
            }
        }
    }

    private fun refreshList() {
        val statuses = viewModel.deviceStatuses.value
        val items = viewModel.devices.value.map { device ->
            DeviceAdapter.DeviceItem(device, statuses[device.deviceIdentifier])
        }
        adapter.submitList(items)
        binding.tvNoDevices.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}
