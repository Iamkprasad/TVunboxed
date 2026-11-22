package com.tvunboxed.app.core

import android.content.Context
import android.net.wifi.WifiManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MediaServer(private val port: Int = Protocol.DEFAULT_SHARE_PORT) : NanoHTTPD(port) {
    private val files = ConcurrentHashMap<String, File>()

    fun add(file: File): String {
        val id = UUID.randomUUID().toString()
        files[id] = file
        return "http://${localIp()}:$port/media/$id"
    }

    fun clear() = files.clear()

    fun count(): Int = files.size

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (!uri.startsWith("/media/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        val id = uri.removePrefix("/media/").substringBefore('/')
        val file = files[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unknown media")
        if (!file.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Missing file")
        return respondWithRange(session, file)
    }

    private fun respondWithRange(session: IHTTPSession, file: File): Response {
        val mime = mimeFor(file.name)
        val total = file.length()
        val range = session.headers["range"] ?: session.headers["Range"]
        if (range.isNullOrBlank() || !range.startsWith("bytes=")) {
            val stream = FileInputStream(file)
            val response = newFixedLengthResponse(Response.Status.OK, mime, stream, total)
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }
        val parts = range.removePrefix("bytes=").split("-", limit = 2)
        val start = parts[0].toLongOrNull() ?: 0L
        val end = if (parts.getOrNull(1).isNullOrBlank()) total - 1
        else parts[1].toLongOrNull()?.coerceAtMost(total - 1) ?: total - 1
        if (start >= total || start > end) {
            val response = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, mime, "")
            response.addHeader("Content-Range", "bytes */$total")
            return response
        }
        val stream = FileInputStream(file)
        stream.skip(start)
        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT, mime, stream, end - start + 1
        )
        response.addHeader("Content-Range", "bytes $start-$end/$total")
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "srt" -> "application/x-subrip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    companion object {
        fun localIp(): String = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .first { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.hostAddress?.contains(':') == false }
                .hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }

        fun wifiIp(context: Context): String {
            val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = manager?.connectionInfo?.ipAddress ?: return localIp()
            return "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        }
    }
}
