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
    private const val PREFS = "monitor_prefs"
    private const val KEY_DEVICES = "devices"
    private const val KEY_INTERVAL = "interval"
    private const val DEFAULT_INTERVAL = 5000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveDevices(context: Context, devices: List<Device>) {
        val array = JSONArray()
        devices.forEach { array.put(it.toJsonObject()) }
        prefs(context).edit { putString(KEY_DEVICES, array.toString()) }
    }

    fun loadDevices(context: Context): List<Device> {
        val json = prefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { Device.fromJsonObject(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveInterval(context: Context, ms: Long) = prefs(context).edit { putLong(KEY_INTERVAL, ms) }
    fun loadInterval(context: Context) = prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun exportConfig(context: Context) = JSONObject().apply {
        put(
            "devices",
            JSONArray().apply { loadDevices(context).forEach { put(it.toJsonObject()) } })
        put("interval", loadInterval(context))
    }.toString(4)

    fun importConfig(context: Context, json: String) = try {
        val obj = JSONObject(json)
        val devices = mutableListOf<Device>()
        val array = obj.getJSONArray("devices")
        for (i in 0 until array.length()) devices.add(Device.fromJsonObject(array.getJSONObject(i)))
        saveDevices(context, devices)
        saveInterval(context, obj.optLong("interval", DEFAULT_INTERVAL))
        true
    } catch (_: Exception) {
        false
    }
}
