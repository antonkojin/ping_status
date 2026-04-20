package com.example.myapplication2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MonitorService : Service() {
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val deviceStatuses = mutableMapOf<String, Boolean>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitoringJob: Job? = null
    private val pingDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    companion object {
        const val CHANNEL_ID = "DeviceMonitorChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STATUS_CHANGED = "com.example.myapplication2.STATUS_CHANGED"
        const val ACTION_MONITOR_STOPPED = "com.example.myapplication2.MONITOR_STOPPED"
        const val ACTION_STOP_SERVICE = "com.example.myapplication2.STOP_SERVICE"
        const val ACTION_REFRESH = "com.example.myapplication2.REFRESH"
        const val ACTION_GET_ALL_STATUSES = "com.example.myapplication2.GET_ALL_STATUSES"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_STATUS = "extra_status"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Device Monitor Channel",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> stopSelf()
            ACTION_REFRESH -> triggerRefresh()
            ACTION_GET_ALL_STATUSES -> broadcastAllStatuses()
            else -> {
                if (monitoringJob == null) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification("Starting...")
                    )
                    startMonitoring()
                }
            }
        }
        return START_STICKY
    }

    private fun triggerRefresh() = serviceScope.launch {
        performPingCycle(DeviceStore.loadDevices(this@MonitorService).filter { it.isEnabled })
    }

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                val devices = DeviceStore.loadDevices(this@MonitorService).filter { it.isEnabled }
                deviceStatuses.keys.retainAll(devices.map { it.id }.toSet())
                performPingCycle(devices)
                delay(DeviceStore.loadInterval(this@MonitorService))
            }
        }
    }

    private suspend fun performPingCycle(devices: List<Device>) {
        devices.map { device ->
            serviceScope.launch {
                val currentStatus = withContext(pingDispatcher) { ping(device.ip) }
                if (currentStatus != deviceStatuses[device.id]) {
                    deviceStatuses[device.id] = currentStatus
                    broadcastStatus(device.id, currentStatus)
                }
            }
        }.joinAll()
        updateNotification(devices)
    }

    private fun ping(host: String?): Boolean = try {
        if (host.isNullOrEmpty()) false
        else Runtime.getRuntime().exec("ping -c 1 -w 1 $host").waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun broadcastStatus(deviceId: String, isOn: Boolean) =
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_STATUS, isOn)
            setPackage(packageName)
        })

    private fun broadcastAllStatuses() =
        deviceStatuses.forEach { (id, status) -> broadcastStatus(id, status) }

    private fun updateNotification(devices: List<Device>) {
        val onCount = devices.count { deviceStatuses[it.id] == true }
        val offCount = devices.count { deviceStatuses[it.id] == false }
        val summary = if (devices.isEmpty()) "No active devices" else "$onCount ON, $offCount OFF"
        val details = if (devices.isEmpty()) null else devices.joinToString("\n") {
            "${it.name}: ${if (deviceStatuses[it.id] == true) "ON" else "OFF"}" 
        }
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(summary, details)
        )
    }

    private fun createNotification(content: String, bigText: String? = null): Notification {
        val intent = { action: String? ->
            val i = if (action == null) Intent(this, MainActivity::class.java) else Intent(
                this,
                MonitorService::class.java
            ).apply { this.action = action }
            val flag = PendingIntent.FLAG_IMMUTABLE
            if (action == null) PendingIntent.getActivity(
                this,
                0,
                i,
                flag
            ) else PendingIntent.getService(this, 1, i, flag)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent(null))
            .setPriority(NotificationCompat.PRIORITY_LOW).setSilent(true).setOngoing(true)
            .addAction(0, "Refresh", intent(ACTION_REFRESH))
            .addAction(0, "Stop Monitoring", intent(ACTION_STOP_SERVICE))
            .apply {
                if (bigText != null) setStyle(
                    NotificationCompat.BigTextStyle().bigText(bigText)
                )
            }
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pingDispatcher.close()
        sendBroadcast(Intent(ACTION_MONITOR_STOPPED).apply { setPackage(packageName) })
        super.onDestroy()
    }
}
