package com.example.garminenduro3

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.connectiq.IQDevice
import com.example.garminenduro3.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onListen: (IQDevice) -> Unit,
) : ListAdapter<ConnectIQManager.DeviceInfo, DeviceAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(info: ConnectIQManager.DeviceInfo) {
            binding.textDeviceName.text = info.device.friendlyName ?: "Unknown Device"
            binding.textDeviceStatus.text = info.status.name.replace('_', ' ')
            binding.textDeviceStatus.setTextColor(statusColor(info.status))

            val connected = info.status == IQDevice.IQDeviceStatus.CONNECTED
            binding.buttonListen.isEnabled = connected
            binding.buttonListen.text = if (info.app != null) "Listening" else "Listen"
            binding.buttonListen.setOnClickListener { onListen(info.device) }
        }

        private fun statusColor(status: IQDevice.IQDeviceStatus): Int {
            val res = binding.root.context.resources
            return when (status) {
                IQDevice.IQDeviceStatus.CONNECTED -> res.getColor(R.color.status_connected, null)
                IQDevice.IQDeviceStatus.NOT_CONNECTED -> res.getColor(R.color.status_not_connected, null)
                else -> res.getColor(R.color.status_unknown, null)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConnectIQManager.DeviceInfo>() {
            override fun areItemsTheSame(a: ConnectIQManager.DeviceInfo, b: ConnectIQManager.DeviceInfo) =
                a.device.deviceIdentifier == b.device.deviceIdentifier

            override fun areContentsTheSame(a: ConnectIQManager.DeviceInfo, b: ConnectIQManager.DeviceInfo) =
                a.status == b.status && a.app?.status == b.app?.status
        }
    }
}
