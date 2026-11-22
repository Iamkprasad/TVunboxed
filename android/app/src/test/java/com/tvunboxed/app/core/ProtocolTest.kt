package com.tvunboxed.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun discoveryPacketHasMagicAtRequiredOffset() {
        val packet = Protocol.buildDiscoveryPacket()
        assertEquals(50, packet.size)
        val magic = "FindECloudBox".toByteArray()
        for (i in magic.indices) {
            assertEquals(magic[i], packet[0x14 + i])
        }
        assertEquals(magic.size, ((packet[0x0C].toInt() and 0xff) shl 24) or
            ((packet[0x0D].toInt() and 0xff) shl 16) or
            ((packet[0x0E].toInt() and 0xff) shl 8) or
            (packet[0x0F].toInt() and 0xff))
    }

    @Test
    fun keyEventUsesCrLfRecordFormat() {
        assertEquals("KEYEVENT\r\n23\r\n\r\n", Protocol.key(23))
        assertEquals("KEYEVENT\r\n23\r\nlongpress\r\n\r\n", Protocol.key(23, longPress = true))
    }

    @Test
    fun handshakeUsesPathPortMarkerOrder() {
        assertEquals(
            "auth\r\n/storage/emulated/0 8888 onelong\r\n\r\n",
            Protocol.handshake("/storage/emulated/0", 8888, "onelong")
        )
    }

    @Test
    fun volumeIsClampedToRange() {
        assertEquals("MediaControl\r\nsetVolume 0\r\n\r\n", Protocol.volume(-5))
        assertEquals("MediaControl\r\nsetVolume 100\r\n\r\n", Protocol.volume(500))
    }

    @Test(expected = IllegalArgumentException::class)
    fun textInputRejectsMultiLinePayloads() {
        assertEquals("CONTENT\r\n1\r\n0 hello\r\n\r\n", Protocol.typeText("hello"))
        Protocol.typeText("a\nb")
    }

    @Test
    fun mediaIntegerParsingFindsFirstValueAfterKey() {
        assertEquals(1234, Protocol.parseMediaInt("position=1234ms", "position"))
        assertNull(Protocol.parseMediaInt("nothing here", "position"))
    }

    @Test
    fun saveAndOpenFileCommandsUseUrlAndMime() {
        assertEquals(
            "Savefile\r\nSave http://192.168.1.5:8888/a.apk application/vnd.android.package-archive\r\n\r\n",
            Protocol.saveFile("http://192.168.1.5:8888/a.apk", "application/vnd.android.package-archive")
        )
        assertEquals(
            "Openfile\r\nOpen http://192.168.1.5:8888/a.jpg image/jpeg\r\n\r\n",
            Protocol.openFile("http://192.168.1.5:8888/a.jpg", "image/jpeg")
        )
    }

    @Test
    fun touchEventEncodesActionAndCoordinates() {
        val message = Protocol.touchEvent(action = 0, points = listOf(10.5f to 20.25f), mirror = false)
        assertEquals("TOUCHEVENT\r\n0\r\n[={10.5,20.25}]\r\n\r\n", message)
        assertTrue(Protocol.touchEvent(2, listOf(1f to 2f), mirror = true).startsWith("MIRRORTOUCHEVENT\r\n"))
    }
}
