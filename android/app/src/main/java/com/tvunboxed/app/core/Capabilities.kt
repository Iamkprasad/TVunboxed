package com.tvunboxed.app.core

enum class CapabilityStatus {
    SUPPORTED, UNSUPPORTED, UNKNOWN, NOT_TESTED, REQUIRES_TV_RECEIVER, REQUIRES_ADB
}

enum class Capability(val visibleWhenSupported: Boolean = true) {
    VENDOR_CONTROL,
    AIR_MOUSE,
    APP_DISCOVERY,
    TEXT_INPUT,
    FILE_TRANSFER,
    WEBDAV,
    MEDIA_CONTROL,
    TV_SCREEN_CAPTURE,
    TOUCHBACK,
    DLNA_RENDERER,
    GOOGLE_CAST,
    MIRACAST,
    OPTIONAL_RECEIVER,
    DIAGNOSTICS,
    ADB;

    fun visibleWhen(status: CapabilityStatus): Boolean = when (status) {
        CapabilityStatus.SUPPORTED -> visibleWhenSupported
        CapabilityStatus.UNSUPPORTED -> false
        CapabilityStatus.UNKNOWN,
        CapabilityStatus.NOT_TESTED,
        CapabilityStatus.REQUIRES_TV_RECEIVER,
        CapabilityStatus.REQUIRES_ADB -> true
    }
}

class CapabilitySet(private val states: MutableMap<Capability, CapabilityStatus> = mutableMapOf()) {
    fun status(capability: Capability): CapabilityStatus =
        states[capability] ?: CapabilityStatus.NOT_TESTED

    fun mark(capability: Capability, status: CapabilityStatus) {
        states[capability] = status
    }

    fun toMap(): Map<String, String> =
        states.mapKeys { it.key.name }.mapValues { it.value.name }

    companion object {
        fun fromMap(map: Map<String, String>): CapabilitySet {
            val states = map.mapNotNull { (key, value) ->
                runCatching { Capability.valueOf(key) to CapabilityStatus.valueOf(value) }.getOrNull()
            }.toMap().toMutableMap()
            return CapabilitySet(states)
        }
    }
}
