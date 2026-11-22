package com.tvunboxed.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object Discovery {
    data class Result(
        val ip: String,
        val name: String,
        val controlPort: Int,
        val speakerPort: Int?,
        val serial: String?,
        val raw: String
    )

    fun parseReply(reply: String, ip: String): Result? {
        val fields = reply.trim().split(":")
        if (fields.size < 5 || !fields[0].startsWith("ECloudBox")) return null
        val controlPort = fields[2].toIntOrNull() ?: return null
        val speaker = fields[3].toIntOrNull()
        val serial = fields.firstOrNull { it.startsWith("sn=", ignoreCase = true) }
        return Result(ip, fields[1], controlPort, speaker, serial, reply.trim())
    }

    suspend fun scan(timeoutMs: Int = 2000): List<TvDevice> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, TvDevice>()
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            val packet = DatagramPacket(
                Protocol.buildDiscoveryPacket(),
                Protocol.buildDiscoveryPacket().size,
                InetAddress.getByName("255.255.255.255"),
                Protocol.DISCOVERY_PORT
            )
            socket.send(packet)
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val reply = DatagramPacket(buffer, buffer.size)
                    socket.receive(reply)
                    val text = String(reply.data, 0, reply.length, Charsets.UTF_8)
                    val parsed = parseReply(text, reply.address.hostAddress ?: continue) ?: continue
                    found[parsed.ip] = TvDevice(
                        ip = parsed.ip,
                        name = parsed.name,
                        controlPort = parsed.controlPort,
                        speakerPort = parsed.speakerPort,
                        serial = parsed.serial
                    )
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                    continue
                }
            }
        } finally {
            socket.close()
        }
        found.values.toList()
    }
}
