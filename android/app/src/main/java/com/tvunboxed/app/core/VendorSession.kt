package com.tvunboxed.app.core

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class VendorSession(private var device: TvDevice) {
    private var socket: Socket? = null
    private val lock = Any()

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun setDevice(newDevice: TvDevice) {
        if (newDevice.ip != device.ip || newDevice.controlPort != device.controlPort) {
            close()
            device = newDevice
        }
    }

    fun connect(): Boolean = synchronized(lock) {
        try {
            closeLocked()
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(device.ip, device.controlPort), 4000)
            newSocket.soTimeout = 1200
            val out = newSocket.getOutputStream()
            out.write(Protocol.probe().toByteArray())
            out.write(
                Protocol.handshake("/storage/emulated/0", Protocol.DEFAULT_SHARE_PORT).toByteArray()
            )
            out.flush()
            socket = newSocket
            connected = true
            lastError = null
            true
        } catch (e: IOException) {
            lastError = e.message ?: "connection failed"
            connected = false
            false
        }
    }

    fun send(payload: String, waitReply: Boolean = false, waitMs: Int = 800): String = synchronized(lock) {
        for (attempt in 0..1) {
            val active = socket
            if (active == null || !connected) {
                if (!connect()) continue
            }
            try {
                val current = socket ?: throw IOException("no socket")
                val out = current.getOutputStream()
                out.write(payload.toByteArray())
                out.flush()
                if (!waitReply) return ""
                current.soTimeout = waitMs
                val bytes = ByteArray(8192)
                val read = current.getInputStream().read(bytes)
                current.soTimeout = 1200
                return if (read > 0) String(bytes, 0, read, Charsets.UTF_8).trim() else ""
            } catch (e: SocketTimeoutException) {
                lastError = "TV did not reply"
                return ""
            } catch (e: IOException) {
                lastError = e.message ?: "send failed"
                connected = false
                closeLocked()
            }
        }
        ""
    }

    fun collect(payloads: List<String>, waitMs: Int = 1200): String {
        send(Protocol.probe())
        send(Protocol.handshake("/storage/emulated/0", Protocol.DEFAULT_SHARE_PORT))
        payloads.forEach { send(it) }
        val current = synchronized(lock) { socket }
        if (current == null) return ""
        val result = StringBuilder()
        try {
            current.soTimeout = waitMs
            val bytes = ByteArray(4096)
            val deadline = System.currentTimeMillis() + waitMs + 500
            while (System.currentTimeMillis() < deadline) {
                val read = current.getInputStream().read(bytes)
                if (read <= 0) break
                result.append(String(bytes, 0, read, Charsets.UTF_8))
                if (result.length > 65536) break
            }
        } catch (_: SocketTimeoutException) {
        } catch (_: IOException) {
        } finally {
            try { current.soTimeout = 1200 } catch (_: Exception) {}
        }
        return result.toString()
    }

    fun fetchApps(): List<TvApp> {
        var dedicated: Socket? = null
        return try {
            dedicated = Socket()
            dedicated.connect(InetSocketAddress(device.ip, device.controlPort), 4000)
            dedicated.soTimeout = 500
            val out = dedicated.getOutputStream()
            out.write(Protocol.probe().toByteArray())
            out.write(Protocol.handshake("/storage/emulated/0", Protocol.DEFAULT_SHARE_PORT).toByteArray())
            out.write(Protocol.appList().toByteArray())
            out.flush()
            val buffer = ByteArray(65536)
            val blob = java.io.ByteArrayOutputStream()
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val read = dedicated.getInputStream().read(buffer)
                    if (read <= 0) break
                    blob.write(buffer, 0, read)
                } catch (_: SocketTimeoutException) {
                    if (blob.size() > 0) break
                }
            }
            AppListParser.parse(blob.toByteArray())
        } catch (e: IOException) {
            lastError = e.message ?: "app list failed"
            emptyList()
        } finally {
            try { dedicated?.close() } catch (_: Exception) {}
        }
    }

    fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}

