package com.tvunboxed.app.core

import android.content.Context

class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tvunboxed", Context.MODE_PRIVATE)

    var lastDevice: TvDevice?
        get() {
            val ip = prefs.getString("ip", null) ?: return null
            return TvDevice(
                ip = ip,
                name = prefs.getString("name", "TV") ?: "TV",
                controlPort = prefs.getInt("controlPort", Protocol.CONTROL_PORT),
                httpPort = prefs.getInt("httpPort", Protocol.HTTP_PORT),
                serial = prefs.getString("serial", null),
                model = prefs.getString("model", null)
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove("ip")
                } else {
                    putString("ip", value.ip)
                    putString("name", value.name)
                    putInt("controlPort", value.controlPort)
                    putInt("httpPort", value.httpPort)
                    putString("serial", value.serial)
                    putString("model", value.model)
                }
            }.apply()
        }

    var capabilities: CapabilitySet
        get() = CapabilitySet.fromMap(
            prefs.all.filterValues { it is String }.mapValues { it.value as String }
                .filterKeys { key -> runCatching { Capability.valueOf(key) }.isSuccess }
        )
        set(value) = prefs.edit().apply {
            Capability.entries.forEach { remove(it.name) }
            value.toMap().forEach { (key, status) -> putString(key, status) }
        }.apply()

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(packageName: String) {
        val current = favorites().toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        prefs.edit().putStringSet("favorites", current).apply()
    }

    fun playbackHistory(): List<String> =
        prefs.getString("history", "").orEmpty().split("|").filter { it.isNotBlank() }

    fun addHistory(url: String) {
        val updated = (listOf(url) + playbackHistory().filter { it != url }).take(20)
        prefs.edit().putString("history", updated.joinToString("|")).apply()
    }
}
