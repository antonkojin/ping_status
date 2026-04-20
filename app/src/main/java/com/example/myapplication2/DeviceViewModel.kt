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
    val deviceStatuses = mutableStateMapOf<String, Boolean>()

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
    }

    fun loadDevices() {
        devices.clear()
        devices.addAll(DeviceStore.loadDevices(getContext()))
    }

    fun saveDevices() = DeviceStore.saveDevices(getContext(), devices)

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
    }

    fun updatePingInterval(intervalMs: Long) {
        pingIntervalMs = intervalMs
        DeviceStore.saveInterval(getContext(), intervalMs)
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
