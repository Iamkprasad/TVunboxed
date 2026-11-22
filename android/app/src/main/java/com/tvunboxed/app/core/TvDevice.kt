package com.tvunboxed.app.core

data class TvDevice(
    val ip: String,
    val name: String,
    val controlPort: Int = Protocol.CONTROL_PORT,
    val httpPort: Int = Protocol.HTTP_PORT,
    val speakerPort: Int? = null,
    val serial: String? = null,
    val model: String? = null
)
