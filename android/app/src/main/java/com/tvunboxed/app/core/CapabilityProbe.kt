package com.tvunboxed.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object CapabilityProbe {
    suspend fun probe(device: TvDevice, session: VendorSession): CapabilitySet =
        withContext(Dispatchers.IO) {
            val set = CapabilitySet()

            val controlOk = session.connected || session.connect()
            set.mark(Capability.VENDOR_CONTROL, if (controlOk) SUPPORTED else UNSUPPORTED)

            if (controlOk) {
                set.mark(Capability.AIR_MOUSE, SUPPORTED)
                set.mark(Capability.TEXT_INPUT, SUPPORTED)
                set.mark(Capability.FILE_TRANSFER, SUPPORTED)
                set.mark(Capability.APP_DISCOVERY,
                    if (session.fetchApps().isNotEmpty()) SUPPORTED else UNKNOWN)
                val media = session.send(Protocol.volumeQuery(), waitReply = true, waitMs = 700)
                set.mark(Capability.MEDIA_CONTROL,
                    if (media.isNotBlank()) SUPPORTED else UNKNOWN)
                val fb = session.send(Protocol.framebufferInfo(), waitReply = true, waitMs = 700)
                set.mark(Capability.TOUCHBACK, if (fb.isNotBlank()) UNKNOWN else NOT_TESTED)
            }

            val dav = WebDavClient(device.ip, device.httpPort)
            val webdavOk = dav.reachable()
            set.mark(Capability.WEBDAV, if (webdavOk) SUPPORTED else UNSUPPORTED)
            set.mark(Capability.DIAGNOSTICS, if (webdavOk) UNKNOWN else UNSUPPORTED)

            set.mark(Capability.TV_SCREEN_CAPTURE,
                if (screencaptureAvailable(device)) SUPPORTED else UNKNOWN)
            set.mark(Capability.OPTIONAL_RECEIVER, REQUIRES_TV_RECEIVER)
            set.mark(Capability.ADB, REQUIRES_ADB)
            set.mark(Capability.DLNA_RENDERER, NOT_TESTED)
            set.mark(Capability.GOOGLE_CAST, NOT_TESTED)
            set.mark(Capability.MIRACAST, NOT_TESTED)
            set
        }

    private fun screencaptureAvailable(device: TvDevice): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        return try {
            val request = Request.Builder()
                .url("http://${device.ip}:${device.httpPort}/remote/screencapture")
                .get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private val SUPPORTED = CapabilityStatus.SUPPORTED
    private val UNSUPPORTED = CapabilityStatus.UNSUPPORTED
    private val UNKNOWN = CapabilityStatus.UNKNOWN
    private val NOT_TESTED = CapabilityStatus.NOT_TESTED
    private val REQUIRES_TV_RECEIVER = CapabilityStatus.REQUIRES_TV_RECEIVER
    private val REQUIRES_ADB = CapabilityStatus.REQUIRES_ADB
}
