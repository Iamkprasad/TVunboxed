package com.tvunboxed.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvunboxed.app.core.AppListParser
import com.tvunboxed.app.core.Capability
import com.tvunboxed.app.core.CapabilityProbe
import com.tvunboxed.app.core.CapabilitySet
import com.tvunboxed.app.core.CapabilityStatus
import com.tvunboxed.app.core.DavEntry
import com.tvunboxed.app.core.DeviceStore
import com.tvunboxed.app.core.Discovery
import com.tvunboxed.app.core.VendorSession
import com.tvunboxed.app.core.MediaServer
import com.tvunboxed.app.core.Protocol
import com.tvunboxed.app.core.TvApp
import com.tvunboxed.app.core.TvDevice
import com.tvunboxed.app.core.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class MediaQueueItem(val name: String, val url: String, val mime: String)

data class AppUiState(
    val device: TvDevice? = null,
    val discovered: List<TvDevice> = emptyList(),
    val connected: Boolean = false,
    val capabilities: CapabilitySet = CapabilitySet(),
    val apps: List<TvApp> = emptyList(),
    val davPath: String = "/sdcard/",
    val davEntries: List<DavEntry> = emptyList(),
    val queue: List<MediaQueueItem> = emptyList(),
    val history: List<String> = emptyList(),
    val mediaStatus: String = "",
    val framebuffer: Pair<Int, Int>? = null,
    val tvFrame: ByteArray? = null,
    val diagnostics: String = "",
    val status: String = "Ready",
    val busy: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DeviceStore(application)
    private var session: VendorSession? = null
    private val mediaServer = MediaServer()
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private var keepaliveJob: Job? = null
    private var frameJob: Job? = null
    private var frameIntervalMs = 1000L

    private val _state = MutableStateFlow(
        AppUiState(
            device = store.lastDevice,
            capabilities = store.capabilities,
            history = store.playbackHistory()
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun setStatus(message: String) = _state.update { it.copy(status = message) }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Scanning network…") }
            val devices = Discovery.scan(2200)
            _state.update {
                it.copy(
                    discovered = devices,
                    busy = false,
                    status = if (devices.isEmpty()) "No TVs found — enter the IP manually" else "${devices.size} TV(s) found"
                )
            }
        }
    }

    fun connect(device: TvDevice) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Connecting to ${device.name}…") }
            val active = VendorSession(device)
            val ok = withContext(Dispatchers.IO) { active.connect() }
            session?.close()
            session = active
            store.lastDevice = device
            if (ok) {
                startKeepalive()
                _state.update { it.copy(device = device, connected = true, busy = false, status = "Connected to ${device.name}") }
                refreshCapabilities()
            } else {
                _state.update {
                    it.copy(
                        device = device,
                        connected = false,
                        busy = false,
                        status = "Could not reach ${device.ip}:${device.controlPort}"
                    )
                }
            }
        }
    }

    fun connectManual(ip: String, controlPort: Int, httpPort: Int) {
        connect(TvDevice(ip = ip.trim(), name = "TV ($ip)", controlPort = controlPort, httpPort = httpPort))
    }

    fun disconnect() {
        keepaliveJob?.cancel()
        frameJob?.cancel()
        session?.close()
        session = null
        _state.update { it.copy(connected = false, tvFrame = null, status = "Disconnected") }
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                withContext(Dispatchers.IO) { session?.send(Protocol.keepalive()) }
            }
        }
    }

    fun refreshCapabilities() {
        val current = session ?: return
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Detecting TV capabilities…") }
            val detected = withContext(Dispatchers.IO) {
                val s = CapabilityProbe.probe(device, current)
                val fb = current.send(Protocol.framebufferInfo(), waitReply = true, waitMs = 700)
                s to fb
            }
            store.capabilities = detected.first
            _state.update {
                it.copy(
                    capabilities = detected.first,
                    framebuffer = parseFramebuffer(detected.second),
                    busy = false,
                    status = "Capabilities updated"
                )
            }
        }
    }

    private fun parseFramebuffer(reply: String): Pair<Int, Int>? {
        val numbers = Regex("""\d+""").findAll(reply).map { it.value.toIntOrNull() }.filterNotNull().toList()
        return if (numbers.size >= 2) numbers[0] to numbers[1] else null
    }

    private fun send(payload: String, status: String? = null) {
        val active = session
        if (active == null) {
            setStatus("Connect to a TV first")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { active.send(payload) }
            if (status != null) setStatus(status)
        }
    }

    fun key(code: Int, longPress: Boolean = false) = send(
        Protocol.key(code, longPress),
        if (longPress) "Long key $code" else "Key $code"
    )

    fun volume(level: Int) = send(Protocol.volume(level), "Volume $level")
    fun mute() = send(Protocol.mute(), "Muted")
    fun source(index: Int) = send(Protocol.source(index), "Source $index")
    fun airmouse(dx: Float, dy: Float, button: Int) = send(Protocol.airmouse(dx, dy, button))
    fun airClick() = send(Protocol.airClick())
    fun mouseEnable(on: Boolean) = send(Protocol.mouseEnable(on), "Mouse ${if (on) "on" else "off"}")
    fun typeText(text: String) {
        try {
            send(Protocol.typeText(text), "Text sent")
        } catch (e: IllegalArgumentException) {
            setStatus(e.message ?: "Invalid text")
        }
    }
    fun launch(packageName: String) = send(Protocol.launch(packageName), "Launching $packageName")
    fun uninstall(packageName: String) = send(Protocol.uninstall(packageName), "Uninstall requested")
    fun openLink(url: String) = send(Protocol.openLink(url), "Opening $url on TV")
    fun saveFile(url: String, mime: String) = send(Protocol.saveFile(url, mime), "Save requested")
    fun media(command: String) = send(Protocol.mediaCommand(command), "Media: $command")
    fun seek(ms: Long) = send(Protocol.seek(ms), "Seeking to ${ms}ms")

    fun queryMedia(command: String) {
        val active = session ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) { active.send(Protocol.mediaCommand(command), true, 900) }
            _state.update {
                it.copy(mediaStatus = reply.ifBlank { "No reply from TV" }, status = "Media query complete")
            }
        }
    }

    fun refreshApps() {
        val active = session ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Reading TV apps…") }
            val apps = withContext(Dispatchers.IO) { active.fetchApps() }
            _state.update { it.copy(apps = apps, busy = false, status = "${apps.size} apps found") }
        }
    }

    fun toggleFavorite(packageName: String) {
        store.toggleFavorite(packageName)
        setStatus(if (packageName in store.favorites()) "Added to favorites" else "Removed from favorites")
    }

    fun favorites(): Set<String> = store.favorites()

    fun ensureMediaServer() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!mediaServer.isAlive) {
                    try {
                        mediaServer.start(Protocol.DEFAULT_SHARE_PORT, false)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun addToQueue(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!mediaServer.isAlive) {
                    try { mediaServer.start(Protocol.DEFAULT_SHARE_PORT, false) } catch (_: Exception) {}
                }
                val url = mediaServer.add(file)
                val mime = guessMime(file.name)
                val item = MediaQueueItem(file.name, url, mime)
                _state.update { it.copy(queue = it.queue + item) }
                store.addHistory(url)
                withContext(Dispatchers.Main) {
                    session?.send(Protocol.openLink(url))
                }
                setStatus("Streaming ${file.name} to TV")
            }
        }
    }

    fun sendUrl(url: String) {
        if (url.isBlank()) return setStatus("Enter a URL")
        store.addHistory(url)
        openLink(url)
    }

    fun copyUriToFile(uri: Uri, displayName: String): File? {
        val context = getApplication<Application>()
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
            val file = File(context.cacheDir, "share_${System.currentTimeMillis()}_$safeName")
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    fun browse(path: String) {
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Browsing $path") }
            val entries = withContext(Dispatchers.IO) { WebDavClient(device.ip, device.httpPort).list(path) }
            _state.update { it.copy(davPath = path, davEntries = entries, busy = false, status = "${entries.size} items") }
        }
    }

    fun uploadToTv(path: String, bytes: ByteArray, mime: String) {
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                WebDavClient(device.ip, device.httpPort).upload(path, bytes, mime)
            }
            setStatus(if (ok) "Uploaded $path" else "Upload failed")
            browse(_state.value.davPath)
        }
    }

    fun downloadFromTv(path: String) {
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bytes = WebDavClient(device.ip, device.httpPort).download(path) ?: return@withContext null
                val name = path.trimEnd('/').substringAfterLast('/').ifBlank { "download.bin" }
                val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: getApplication<Application>().cacheDir
                val target = File(dir, name)
                target.writeBytes(bytes)
                target.absolutePath
            }
            setStatus(if (result != null) "Downloaded to $result" else "Download failed")
        }
    }

    fun deleteOnTv(path: String) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { WebDavClient(device.ip, device.httpPort).delete(path) }
            setStatus(if (ok) "Deleted $path" else "Delete failed")
            browse(_state.value.davPath)
        }
    }

    fun captureFrame() {
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("http://${device.ip}:${device.httpPort}/remote/screencapture")
                        .get().build()
                    http.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.bytes() else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _state.update {
                it.copy(
                    tvFrame = bytes,
                    status = if (bytes != null) "TV frame ${bytes.size} bytes" else "Screencapture unavailable"
                )
            }
        }
    }

    fun setFrameInterval(ms: Long) {
        frameIntervalMs = ms.coerceIn(300, 10_000)
    }

    fun startFrameLoop() {
        frameJob?.cancel()
        frameJob = viewModelScope.launch {
            while (isActive) {
                captureFrame()
                delay(frameIntervalMs)
            }
        }
    }

    fun stopFrameLoop() {
        frameJob?.cancel()
        frameJob = null
    }

    fun touchAt(x: Float, y: Float, mirror: Boolean = false) {
        val fb = _state.value.framebuffer
        val tx = if (fb != null) x * fb.first / 1000f else x
        val ty = if (fb != null) y * fb.second / 1000f else y
        send(Protocol.touchEvent(0, listOf(tx to ty), mirror), "Tap sent")
        send(Protocol.touchEvent(1, listOf(tx to ty), mirror))
    }

    fun runDiagnostics() {
        val device = _state.value.device ?: return setStatus("Connect to a TV first")
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = "Collecting diagnostics…") }
            val report = withContext(Dispatchers.IO) {
                val dav = WebDavClient(device.ip, device.httpPort)
                val root = dav.list("/")
                val sdcard = dav.list("/sdcard/")
                val caps = _state.value.capabilities.toMap().toSortedMap()
                buildString {
                    appendLine("TVunboxed diagnostics")
                    appendLine("Device: ${device.name} (${device.ip})")
                    appendLine("Control: ${device.controlPort} / HTTP: ${device.httpPort}")
                    appendLine("Serial: ${device.serial ?: "Unavailable"}")
                    appendLine("Model: ${device.model ?: "Unavailable"}")
                    appendLine("Framebuffer: ${_state.value.framebuffer ?: "Unavailable"}")
                    appendLine("Root entries: ${root.size}")
                    appendLine("SD card entries: ${sdcard.size}")
                    appendLine("Storage: ${sdcard.sumOf { it.size }} bytes visible")
                    appendLine("Capabilities:")
                    caps.forEach { (key, value) -> appendLine("  $key = $value") }
                    appendLine("CPU/RAM: Unavailable (no ADB shell)")
                }
            }
            _state.update { it.copy(diagnostics = report, busy = false, status = "Diagnostics ready") }
        }
    }

    override fun onCleared() {
        stopFrameLoop()
        keepaliveJob?.cancel()
        session?.close()
        try { mediaServer.stop() } catch (_: Exception) {}
        super.onCleared()
    }
}
