package com.tvunboxed.app.core

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class DavEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: String? = null
)

class WebDavClient(private val host: String, private val port: Int = Protocol.HTTP_PORT) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "http://$host:$port$normalized"
    }

    fun reachable(): Boolean = try {
        val request = Request.Builder().url(url("/")).method("OPTIONS", null).build()
        http.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    fun list(path: String): List<DavEntry> = try {
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/><D:getcontentlength/>
            <D:getlastmodified/><D:displayname/></D:prop></D:propfind>""".trimIndent()
        val request = Request.Builder()
            .url(url(path))
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
            .header("Depth", "1")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            parsePropfind(response.body?.string().orEmpty(), path)
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun download(path: String): ByteArray? = try {
        val request = Request.Builder().url(url(path)).get().build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (_: Exception) {
        null
    }

    fun upload(path: String, bytes: ByteArray, mime: String = "application/octet-stream"): Boolean = try {
        val request = Request.Builder()
            .url(url(path))
            .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .build()
        http.newCall(request).execute().use { it.isSuccessful || it.code in 200..299 }
    } catch (_: Exception) {
        false
    }

    fun delete(path: String): Boolean = try {
        http.newCall(Request.Builder().url(url(path)).delete().build()).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    fun copy(from: String, to: String): Boolean = remoteAction("COPY", from, to)

    fun move(from: String, to: String): Boolean = remoteAction("MOVE", from, to)

    fun mkdir(path: String): Boolean = try {
        val request = Request.Builder()
            .url(url(path.trimEnd('/') + "/"))
            .method("MKCOL", null)
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    private fun remoteAction(method: String, from: String, to: String): Boolean = try {
        val request = Request.Builder()
            .url(url(from))
            .method(method, null)
            .header("Destination", url(to))
            .header("Overwrite", "T")
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    private fun parsePropfind(xml: String, requestedPath: String): List<DavEntry> {
        val entries = mutableListOf<DavEntry>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
            val nodes = document.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node !is Element || !node.nodeName.endsWith("response")) continue
                val children = node.childNodes
                var href = ""
                var size = 0L
                var modified: String? = null
                var isDirectory = false
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child !is Element) continue
                    when {
                        child.nodeName.endsWith("href") -> href = child.textContent.trim()
                        child.nodeName.endsWith("getcontentlength") ->
                            size = child.textContent.trim().toLongOrNull() ?: 0L
                        child.nodeName.endsWith("getlastmodified") -> modified = child.textContent.trim()
                        child.nodeName.endsWith("resourcetype") &&
                            child.getElementsByTagName("*").length > 0 -> isDirectory = true
                    }
                }
                if (href.isEmpty()) continue
                val path = href.substringAfter("://").substringAfter('/').let { "/$it" }
                if (path == requestedPath || path == "$requestedPath/") continue
                entries += DavEntry(
                    path = path,
                    name = path.trimEnd('/').substringAfterLast('/'),
                    isDirectory = isDirectory,
                    size = size,
                    modified = modified
                )
            }
        } catch (_: Exception) {
        }
        return entries
    }
}
