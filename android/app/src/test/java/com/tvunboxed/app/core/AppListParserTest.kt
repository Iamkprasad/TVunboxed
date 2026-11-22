package com.tvunboxed.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AppListParserTest {
    @Test
    fun parsesTwoAppRecordsAndIgnoresTrailingBytes() {
        val blob = appBlob(
            Triple("Settings", "com.android.settings", "8.0"),
            Triple("TV", "com.tv.app", "1.2")
        ) + byteArrayOf(0x01, 0x02)
        val apps = AppListParser.parse(blob)
        assertEquals(2, apps.size)
        assertEquals("Settings", apps[0].name)
        assertEquals("com.android.settings", apps[0].packageName)
        assertEquals("8.0", apps[0].versionName)
        assertEquals(42, apps[0].versionCode)
        assertEquals("TV", apps[1].name)
    }

    @Test
    fun stopsOnTruncatedRecord() {
        val blob = appBlob(Triple("One", "com.one", "1.0")) + byteArrayOf(9, 0, 0)
        assertEquals(1, AppListParser.parse(blob).size)
    }

    private fun appBlob(vararg apps: Triple<String, String, String>): ByteArray {
        val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(0x4e, 0x55, 0x02, 0x00))
        apps.forEach { app ->
            writeString(buffer, app.first)
            writeString(buffer, app.second)
            writeString(buffer, app.third)
            buffer.putInt(4)
            buffer.putInt(42)
            writeString(buffer, "")
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun writeString(buffer: ByteBuffer, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
    }
}
