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
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ip", ip)
        put("isEnabled", isEnabled)
    }

    companion object {
        const val DEFAULT_IP = "192.168.1.1"

        fun fromJsonObject(obj: JSONObject): Device = Device(
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

    fun saveDevices(context: Context, devices: List<Device>) {
        val array = JSONArray()
        devices.forEach { array.put(it.toJsonObject()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_DEVICES, array.toString()) }
    }

    fun loadDevices(context: Context): List<Device> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                Device.fromJsonObject(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
