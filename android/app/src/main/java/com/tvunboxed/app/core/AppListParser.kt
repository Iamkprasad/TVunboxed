package com.tvunboxed.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TvApp(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val icon: ByteArray? = null
)

object AppListParser {
    fun parse(blob: ByteArray): List<TvApp> {
        if (blob.size <= 4) return emptyList()
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        val apps = mutableListOf<TvApp>()
        while (buffer.remaining() >= 4) {
            try {
                val name = readString(buffer)
                val packageName = readString(buffer)
                val versionName = readString(buffer)
                buffer.int
                val versionCode = buffer.int
                val icon = readBytes(buffer)
                apps += TvApp(name, packageName, versionName, versionCode, icon)
            } catch (e: Exception) {
                break
            }
        }
        return apps
    }

    private fun readString(buffer: ByteBuffer): String =
        String(readBytes(buffer), Charsets.UTF_8)

    private fun readBytes(buffer: ByteBuffer): ByteArray {
        val length = buffer.int
        if (length < 0 || length > buffer.remaining()) throw IllegalArgumentException("bad length")
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }
}
