package com.tvunboxed.app.web

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.tvunboxed.app.MainViewModel
import com.tvunboxed.app.core.DavEntry
import com.tvunboxed.app.core.Discovery
import com.tvunboxed.app.core.Protocol
import com.tvunboxed.app.core.TvDevice
import com.tvunboxed.app.core.WebDavClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Controlled JS bridge for the locally packaged demo2.html.
 * Only "invoke" is exposed; operations are whitelisted in dispatch().
 */
class TvBridge(
    private val vm: MainViewModel,
    private val webView: () -> WebView?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JavascriptInterface
    fun invoke(requestId: String, op: String, argsJson: String) {
        val args = try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }
        scope.launch {
            val result = try {
                dispatch(op, args)
            } catch (e: Exception) {
                err(e.message ?: "bridge error")
            }
            Log.d("TvBridge", "$op -> ok=${result.optBoolean("ok")} len=${result.length()} error=${result.optString("error", "")}")
            withContext(Dispatchers.Main) {
                webView()?.evaluateJavascript(
                    "window.TvCallback(${JSONObject.quote(requestId)}, ${result.toString()})",
                    null
                )
            }
        }
    }

    private suspend fun dispatch(op: String, a: JSONObject): JSONObject = when (op) {
        "scan" -> scan()
        "connect" -> connect(a)
        "disconnect" -> { vm.disconnect(); ok() }
        "getStatus" -> status()
        "key" -> { vm.key(a.optInt("code"), a.optBoolean("long", false)); ok() }
        "volume" -> { vm.volume(a.optInt("level")); ok() }
        "mute" -> { vm.mute(); ok() }
        "source" -> { vm.source(a.optInt("index")); ok() }
        "airmouse" -> { vm.airmouse(a.optDouble("dx", 0.0).toFloat(), a.optDouble("dy", 0.0).toFloat(), a.optInt("button", 0)); ok() }
        "airclick" -> { vm.airClick(); ok() }
        "mouse" -> { vm.mouseEnable(a.optBoolean("on")); ok() }
        "type" -> { vm.typeText(a.optString("text", "")); ok() }
        "apps" -> apps()
        "launch" -> { vm.launch(a.optString("package", "")); ok() }
        "uninstall" -> { vm.uninstall(a.optString("package", "")); ok() }
        "openLink" -> { vm.openLink(a.optString("url", "")); ok() }
        "saveFile" -> { vm.saveFile(a.optString("url", ""), a.optString("mime", "application/octet-stream")); ok() }
        "openFile" -> { vm.openFile(a.optString("path", ""), a.optString("mime", "application/octet-stream")); ok() }
        "media" -> { vm.media(a.optString("command", "")); ok() }
        "seek" -> { vm.seek(a.optLong("ms", 0)); ok() }
        "queryMedia" -> queryMedia(a.optString("command", ""))
        "browse" -> browse(a.optString("path", "/sdcard/"))
        "upload" -> upload(a)
        "download" -> download(a.optString("path", ""))
        "delete" -> { vm.deleteOnTv(a.optString("path", "")); ok() }
        "listApps" -> apps()
        "capabilities" -> capabilities()
        "diagnostics" -> diagnostics()
        "captureFrame" -> captureFrame()
        "touch" -> { vm.touchAt(a.optDouble("x", 0.0).toFloat(), a.optDouble("y", 0.0).toFloat(), a.optBoolean("mirror", false)); ok() }
        "frameInterval" -> { vm.setFrameInterval(a.optLong("ms", 1000)); ok() }
        "frameLoop" -> { if (a.optBoolean("on")) vm.startFrameLoop() else vm.stopFrameLoop(); ok() }
        "storageInfo" -> err("unsupported: no TV storage endpoint")
        "back" -> { withContext(Dispatchers.Main) { webView()?.goBack() }; ok() }
        else -> err("unsupported operation: $op")
    }

    private suspend fun scan(): JSONObject {
        val devices = Discovery.scan(2200)
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().apply {
                put("n", d.name)
                put("m", d.model ?: "TV")
                put("ip", d.ip)
                put("on", 1)
                put("controlPort", d.controlPort)
                put("httpPort", d.httpPort)
                put("serial", d.serial ?: "")
            })
        }
        return ok(JSONObject().put("devices", arr))
    }

    private suspend fun connect(a: JSONObject): JSONObject {
        val ip = a.optString("ip", "").trim()
        if (ip.isBlank()) return err("missing ip")
        val device = TvDevice(
            ip = ip,
            name = a.optString("name", "").ifBlank { "TV ($ip)" },
            controlPort = a.optInt("controlPort", Protocol.CONTROL_PORT),
            httpPort = a.optInt("httpPort", Protocol.HTTP_PORT)
        )
        vm.connect(device)
        delay(150) // let the ViewModel enter its busy state before polling
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (!s.busy) {
                return if (s.connected && s.device?.ip == ip) ok(statusData(s))
                else err(s.status.ifBlank { "connect failed" })
            }
            delay(100)
        }
        return err("connect timeout")
    }

    private fun status(): JSONObject = ok(statusData(vm.state.value))

    private fun statusData(s: com.tvunboxed.app.AppUiState): JSONObject = JSONObject().apply {
        put("connected", s.connected)
        put("status", s.status)
        put("device", s.device?.name ?: "")
        put("model", s.device?.model ?: "")
        put("ip", s.device?.ip ?: "")
        put("mediaStatus", s.mediaStatus)
        put("frameWidth", s.framebuffer?.first ?: 0)
        put("frameHeight", s.framebuffer?.second ?: 0)
    }

    private suspend fun apps(): JSONObject {
        vm.refreshApps()
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (!s.busy) {
                val arr = JSONArray()
                s.apps.forEach { app ->
                    arr.put(JSONObject().apply {
                        put("name", app.name)
                        put("pkg", app.packageName)
                        put("version", app.versionName ?: "")
                        put("icon", iconDataUrl(app.icon))
                    })
                }
                return ok(JSONObject().put("apps", arr))
            }
            delay(100)
        }
        return err("app list timeout")
    }

    private suspend fun queryMedia(command: String): JSONObject {
        vm.queryMedia(command)
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s.mediaStatus.isNotBlank() && !s.busy) return ok(JSONObject().put("value", s.mediaStatus))
            delay(100)
        }
        return ok(JSONObject().put("value", vm.state.value.mediaStatus))
    }

    private suspend fun browse(path: String): JSONObject {
        vm.browse(path)
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (!s.busy) {
                val arr = JSONArray()
                s.davEntries.forEach { arr.put(entryJson(it)) }
                return ok(JSONObject().put("path", s.davPath).put("entries", arr))
            }
            delay(100)
        }
        return err("browse timeout")
    }

    private suspend fun upload(a: JSONObject): JSONObject {
        val path = a.optString("path", "")
        val b64 = a.optString("data", "")
        if (path.isBlank() || b64.isBlank()) return err("missing path or data")
        val bytes = try { Base64.decode(b64, Base64.DEFAULT) } catch (e: Exception) { return err("bad base64: ${e.message}") }
        vm.uploadToTv(path, bytes, a.optString("mime", "application/octet-stream"))
        return ok()
    }

    private suspend fun download(path: String): JSONObject {
        val device = vm.state.value.device ?: return err("connect first")
        val bytes = WebDavClient(device.ip, device.httpPort).download(path)
            ?: return err("download failed")
        return ok(JSONObject().put("data", Base64.encodeToString(bytes, Base64.DEFAULT)))
    }

    private fun capabilities(): JSONObject {
        val o = JSONObject()
        vm.state.value.capabilities.toMap().forEach { (k, v) -> o.put(k, v.toString()) }
        return ok(o)
    }

    private suspend fun diagnostics(): JSONObject {
        vm.runDiagnostics()
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (!s.busy && s.diagnostics.isNotBlank()) return ok(JSONObject().put("report", s.diagnostics))
            delay(100)
        }
        return ok(JSONObject().put("report", vm.state.value.diagnostics))
    }

    private suspend fun captureFrame(): JSONObject {
        vm.captureFrame()
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val frame = vm.state.value.tvFrame
            if (frame != null) {
                return ok(JSONObject().put("data", Base64.encodeToString(frame, Base64.DEFAULT)))
            }
            delay(100)
        }
        return err("screencapture unavailable")
    }

    private fun entryJson(e: DavEntry): JSONObject = JSONObject().apply {
        put("name", e.name)
        put("path", e.path)
        put("dir", e.isDirectory)
        put("size", e.size)
    }

    private fun iconDataUrl(icon: ByteArray?): String {
        if (icon == null || icon.isEmpty()) return ""
        val mime = when {
            icon.size > 3 && icon[0] == 0x89.toByte() && icon[1] == 0x50.toByte() -> "image/png"
            icon.size > 2 && icon[0] == 0xFF.toByte() && icon[1] == 0xD8.toByte() -> "image/jpeg"
            else -> "image/png"
        }
        return "data:$mime;base64," + Base64.encodeToString(icon, Base64.DEFAULT)
    }

    private fun ok(): JSONObject = JSONObject().put("ok", true)
    private fun ok(data: JSONObject): JSONObject = JSONObject().put("ok", true).put("data", data)
    private fun err(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)
}
