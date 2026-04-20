package com.example.myapplication2

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class Device(
    val id: String,
    val name: String,
    val ip: String,
    val isEnabled: Boolean = true
) {
    fun toJsonObject() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ip", ip)
        put("isEnabled", isEnabled)
    }

    companion object {
        const val DEFAULT_IP = "192.168.1.1"

        fun fromJsonObject(obj: JSONObject) = Device(
            id = obj.getString("id"),
            name = obj.getString("name"),
            ip = obj.getString("ip"),
            isEnabled = obj.optBoolean("isEnabled", true)
        )
    }
}

object DeviceStore {
    private const val PREFS_NAME = "device_monitor_prefs"
    private const val KEY_DEVICES = "devices"
    private const val KEY_INTERVAL = "ping_interval"
    private const val DEFAULT_INTERVAL = 5000L

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDevices(context: Context, devices: List<Device>) {
        val array = JSONArray().apply { devices.forEach { put(it.toJsonObject()) } }
        getPrefs(context).edit { putString(KEY_DEVICES, array.toString()) }
    }

    fun loadDevices(context: Context): List<Device> {
        val json = getPrefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> Device.fromJsonObject(array.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveInterval(context: Context, intervalMs: Long) =
        getPrefs(context).edit { putLong(KEY_INTERVAL, intervalMs) }

    fun loadInterval(context: Context) =
        getPrefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL)
}
