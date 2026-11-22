package com.tvunboxed.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilitiesTest {
    @Test
    fun newSetStartsEveryCapabilityAsNotTested() {
        val set = CapabilitySet()
        assertEquals(CapabilityStatus.NOT_TESTED, set.status(Capability.TV_SCREEN_CAPTURE))
    }

    @Test
    fun markingCapabilityUpdatesStatusAndSerializes() {
        val set = CapabilitySet()
        set.mark(Capability.VENDOR_CONTROL, CapabilityStatus.SUPPORTED)
        set.mark(Capability.DLNA_RENDERER, CapabilityStatus.UNSUPPORTED)
        val restored = CapabilitySet.fromMap(set.toMap())
        assertEquals(CapabilityStatus.SUPPORTED, restored.status(Capability.VENDOR_CONTROL))
        assertEquals(CapabilityStatus.UNSUPPORTED, restored.status(Capability.DLNA_RENDERER))
        assertEquals(CapabilityStatus.NOT_TESTED, restored.status(Capability.GOOGLE_CAST))
    }

    @Test
    fun unsupportedFeaturesAreHiddenButUnknownAreVisible() {
        assertEquals(false, Capability.DLNA_RENDERER.visibleWhen(CapabilityStatus.UNSUPPORTED))
        assertEquals(true, Capability.TV_SCREEN_CAPTURE.visibleWhen(CapabilityStatus.UNKNOWN))
    }
}
