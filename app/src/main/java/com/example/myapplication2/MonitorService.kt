package com.example.myapplication2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MonitorService : Service() {

    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val deviceStatuses = mutableMapOf<String, Boolean>()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var monitoringJob: Job? = null

    // Use a fixed pool for pings to avoid overwhelming the system but allow parallelism
    private val pingDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    companion object {
        const val CHANNEL_ID = "DeviceMonitorChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STATUS_CHANGED = "com.example.myapplication2.STATUS_CHANGED"
        const val ACTION_MONITOR_STOPPED = "com.example.myapplication2.MONITOR_STOPPED"
        const val ACTION_STOP_SERVICE = "com.example.myapplication2.STOP_SERVICE"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_STATUS = "extra_status"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitoringJob == null) {
            startForeground(NOTIFICATION_ID, createNotification("Ping Status", "Starting..."))
            startMonitoring()
        }

        broadcastAllStatuses()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    val devices = DeviceStore.loadDevices(this@MonitorService)
                    val enabledDevices = devices.filter { it.isEnabled }
                    val enabledIds = enabledDevices.map { it.id }.toSet()

                    // Remove statuses for devices no longer enabled/present
                    deviceStatuses.keys.retainAll(enabledIds)

                    // Ping devices in parallel
                    enabledDevices.map { device ->
                        launch {
                            val currentStatus = withContext(pingDispatcher) { ping(device.ip) }
                            if (currentStatus != deviceStatuses[device.id]) {
                                deviceStatuses[device.id] = currentStatus
                                broadcastStatus(device.id, currentStatus)
                            }
                        }
                    }.joinAll()

                    updateNotification(enabledDevices)
                } catch (e: Exception) {
                    Log.e("MonitorService", "Error in monitoring loop", e)
                }
                val interval = DeviceStore.loadInterval(this@MonitorService)
                delay(interval)
            }
        }
    }

    private fun ping(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 1 $host")
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun broadcastStatus(deviceId: String, isOn: Boolean) {
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_STATUS, isOn)
            setPackage(packageName)
        })
    }

    private fun broadcastAllStatuses() {
        deviceStatuses.forEach { (id, status) -> broadcastStatus(id, status) }
    }

    private fun updateNotification(enabledDevices: List<Device>) {
        val onCount = enabledDevices.count { deviceStatuses[it.id] == true }
        val offCount = enabledDevices.count { deviceStatuses[it.id] == false }
        val summary =
            if (enabledDevices.isEmpty()) "No active devices" else "$onCount ON, $offCount OFF"

        val details = if (enabledDevices.isEmpty()) null else {
            enabledDevices.joinToString("\n") { device ->
                val statusStr = when (deviceStatuses[device.id]) {
                    true -> "ON"
                    false -> "OFF"
                    else -> "???"
                }
                "${device.name}: $statusStr"
            }
        }

        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification("Ping Status", summary, details)
        )
    }

    private fun createNotification(title: String, content: String, bigText: String? = null): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .apply {
                if (bigText != null) setStyle(
                    NotificationCompat.BigTextStyle().bigText(bigText)
                )
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Monitor Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        pingDispatcher.close()
        sendBroadcast(Intent(ACTION_MONITOR_STOPPED).apply { setPackage(packageName) })
        super.onDestroy()
    }
}
