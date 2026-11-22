package com.tvunboxed.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryTest {
    @Test
    fun parsesEcloudBoxReply() {
        val reply = "ECloudBox:Living Room:8121:25123:sn=ABC123:model=CVTM358"
        val device = Discovery.parseReply(reply, "192.168.1.201")
        assertNotNull(device)
        assertEquals("192.168.1.201", device!!.ip)
        assertEquals("Living Room", device.name)
        assertEquals(8121, device.controlPort)
        assertEquals(25123, device.speakerPort)
        assertEquals("sn=ABC123", device.serial)
    }

    @Test
    fun rejectsMalformedReply() {
        assertNull(Discovery.parseReply("garbage:reply", "192.168.1.201"))
        assertNull(Discovery.parseReply("ECloudBox:TV:not-a-port:0", "192.168.1.201"))
    }
}
