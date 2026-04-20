package com.example.myapplication2

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    val devices = mutableStateListOf<Device>()
    val deviceStatuses = mutableStateMapOf<String, Boolean?>()

    init {
        System.loadLibrary("myapplication2")
    }

    private external fun getNativeStatus(deviceId: String): Int

    var isMonitoring by mutableStateOf(false)
        private set

    var editingDevice by mutableStateOf<Device?>(null)
    var isAdding by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var pingIntervalMs by mutableLongStateOf(5000L)
        private set

    private fun getContext() = getApplication<Application>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MonitorService.ACTION_STATUS_CHANGED -> {
                    val id = intent.getStringExtra(MonitorService.EXTRA_DEVICE_ID) ?: return
                    val status = intent.getBooleanExtra(MonitorService.EXTRA_STATUS, false)
                    deviceStatuses[id] = status
                }
                MonitorService.ACTION_MONITOR_STOPPED -> isMonitoring = false
            }
        }
    }

    init {
        loadDevices()
        pingIntervalMs = DeviceStore.loadInterval(application)

        // Push initial devices to native
        DeviceStore.saveDevices(application, devices)

        val filter = IntentFilter().apply {
            addAction(MonitorService.ACTION_STATUS_CHANGED)
            addAction(MonitorService.ACTION_MONITOR_STOPPED)
        }
        ContextCompat.registerReceiver(
            application,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Poll native status for sync
        viewModelScope.launch {
            while (true) {
                if (isMonitoring) {
                    devices.forEach { device ->
                        val status = getNativeStatus(device.id)
                        if (status != -2) { // NOT_FOUND
                            deviceStatuses[device.id] = when (status) {
                                1 -> true
                                0 -> false
                                else -> null
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun loadDevices() {
        devices.clear()
        devices.addAll(DeviceStore.loadDevices(getContext()))
    }

    fun saveDevices() {
        DeviceStore.saveDevices(getContext(), devices)
        if (isMonitoring) {
            getContext().startService(Intent(getContext(), MonitorService::class.java).apply {
                action = MonitorService.ACTION_REFRESH
            })
        }
    }

    fun startAdding() {
        isAdding = true
    }

    fun startEditing(device: Device) {
        editingDevice = device
    }

    fun cancelEdit() {
        isAdding = false
        editingDevice = null
        saveDevices()
    }

    fun toggleMonitoring(shouldStart: Boolean) {
        val intent = Intent(getContext(), MonitorService::class.java)
        if (shouldStart) {
            ContextCompat.startForegroundService(getContext(), intent)
        } else {
            getContext().stopService(intent)
        }
        isMonitoring = shouldStart
    }

    fun setMonitoringState(running: Boolean) {
        isMonitoring = running
        if (running) {
            getContext().startService(Intent(getContext(), MonitorService::class.java).apply {
                action = MonitorService.ACTION_GET_ALL_STATUSES
            })
        }
    }

    fun updatePingInterval(intervalMs: Long) {
        pingIntervalMs = intervalMs
        DeviceStore.saveInterval(getContext(), intervalMs)
    }

    fun exportConfig(context: Context): String = DeviceStore.exportConfig(context)

    fun importConfig(context: Context, json: String) {
        if (DeviceStore.importConfig(context, json)) {
            loadDevices()
            pingIntervalMs = DeviceStore.loadInterval(context)
            if (isMonitoring) {
                refresh()
            }
            Toast.makeText(context, "Config imported successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to import config", Toast.LENGTH_SHORT).show()
        }
    }

    fun refresh() {
        if (!isMonitoring) {
            Toast.makeText(getContext(), "Monitoring not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        getContext().startService(Intent(getContext(), MonitorService::class.java).apply {
            action = MonitorService.ACTION_REFRESH
        })

        isRefreshing = true
        viewModelScope.launch {
            delay(1000)
            isRefreshing = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        getContext().unregisterReceiver(statusReceiver)
    }
}
