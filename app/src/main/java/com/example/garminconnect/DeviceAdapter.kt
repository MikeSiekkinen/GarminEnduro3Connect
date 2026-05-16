package com.example.garminconnect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.garminconnect.databinding.ItemDeviceBinding
import com.garmin.android.connectiq.IQDevice

class DeviceAdapter(
    private val onSendClick: (IQDevice) -> Unit,
    private val onOpenClick: (IQDevice) -> Unit,
) : ListAdapter<DeviceAdapter.DeviceItem, DeviceAdapter.ViewHolder>(DIFF) {

    data class DeviceItem(
        val device: IQDevice,
        val status: IQDevice.IQDeviceStatus?,
    )

    inner class ViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceItem) {
            val ctx = binding.root.context
            binding.tvDeviceName.text = item.device.friendlyName
                ?: "Device ${item.device.deviceIdentifier}"
            binding.tvDeviceId.text = "ID: ${item.device.deviceIdentifier}"

            val connected = item.status == IQDevice.IQDeviceStatus.CONNECTED
            binding.tvDeviceStatus.text = item.status?.name ?: "UNKNOWN"
            binding.tvDeviceStatus.setBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    if (connected) R.color.status_connected else R.color.status_disconnected
                )
            )

            binding.btnSend.isEnabled = connected
            binding.btnOpen.isEnabled = connected
            binding.btnSend.setOnClickListener { onSendClick(item.device) }
            binding.btnOpen.setOnClickListener { onOpenClick(item.device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DeviceItem>() {
            override fun areItemsTheSame(old: DeviceItem, new: DeviceItem) =
                old.device.deviceIdentifier == new.device.deviceIdentifier

            override fun areContentsTheSame(old: DeviceItem, new: DeviceItem) =
                old.status == new.status
        }
    }
}
