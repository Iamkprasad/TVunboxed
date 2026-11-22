package com.tvunboxed.app.core

object Protocol {
    const val DISCOVERY_PORT = 48689
    const val CONTROL_PORT = 8121
    const val HTTP_PORT = 8000
    const val DEFAULT_SHARE_PORT = 8888
    const val DISCOVERY_MAGIC = "FindECloudBox"
    const val END = "\r\n\r\n"

    fun buildDiscoveryPacket(): ByteArray {
        val packet = ByteArray(50)
        val magic = DISCOVERY_MAGIC.toByteArray()
        val length = magic.size
        packet[0x0C] = (length ushr 24).toByte()
        packet[0x0D] = (length ushr 16).toByte()
        packet[0x0E] = (length ushr 8).toByte()
        packet[0x0F] = length.toByte()
        magic.copyInto(packet, 0x14)
        return packet
    }

    fun probe(): String = record("sayHello", "TVunboxed")

    fun handshake(path: String, port: Int, marker: String = "onelong"): String =
        record("auth", "$path $port $marker")

    fun identify(model: String = "TVunboxed"): String = record("getFeatures", model)

    fun keepalive(): String = "HeartBeat\r\nlive\r\n\r\n"

    fun key(code: Int, longPress: Boolean = false): String =
        if (longPress) "KEYEVENT\r\n$code\r\nlongpress\r\n\r\n"
        else "KEYEVENT\r\n$code\r\n\r\n"

    fun volume(level: Int): String = record("MediaControl", "setVolume ${level.coerceIn(0, 100)}")

    fun mute(): String = record("SETMUTEEVENT", "1")

    fun source(index: Int): String = record("SWICHEVENT", "$index")

    fun airmouse(dx: Float, dy: Float, button: Int = 0): String =
        "AIRMOUSEEVNET\r\n$dx\r\n$dy\r\n$button\r\n\r\n"

    fun mouseEnable(on: Boolean): String = record("MOUSEENABLEEVENT", if (on) "1" else "0")

    fun airClick(): String = "AIRCLICKEVENT\r\n1\r\n\r\n"

    fun typeText(text: String, mode: Int = 1): String {
        require(!text.contains('\r') && !text.contains('\n')) { "single-line text only" }
        return record("CONTENT", "$mode", "0 $text")
    }

    fun openLink(url: String): String {
        require(url.isNotBlank() && !url.contains('\r') && !url.contains('\n')) { "bad url" }
        return record("OpenLink", url.trim())
    }

    fun launch(packageName: String): String {
        require(packageName.isNotBlank()) { "package must not be empty" }
        return record("STARTAPPEVNET", packageName.trim())
    }

    fun uninstall(packageName: String): String = record("uninstallApp", packageName.trim())

    fun startBrowser(): String = "STARTBROWSEREVENT\r\n1\r\n\r\n"

    fun appList(): String = "GETAPPINFOSEVNET\r\n1\r\n\r\n"

    fun framebufferInfo(): String = "GETFBINFO\r\n1\r\n\r\n"

    fun mediaCommand(command: String): String = record("MediaControl", command.trim())

    fun volumeQuery(): String = mediaCommand("getCurrentVolume")

    fun playingInfo(): String = mediaCommand("getPlayingInfo")

    fun seek(positionMs: Long): String = mediaCommand("seekTo $positionMs")

    fun saveFile(url: String, mime: String): String = record("Savefile", "Save $url $mime")

    fun openFile(url: String, mime: String): String = record("Openfile", "Open $url $mime")

    fun touchEvent(action: Int, points: List<Pair<Float, Float>>, mirror: Boolean = false): String {
        val body = points.joinToString("") { "={${it.first},${it.second}}" }
        return "${if (mirror) "MIRRORTOUCHEVENT" else "TOUCHEVENT"}\r\n$action\r\n[$body]\r\n\r\n"
    }

    fun slideEvent(slot: Int, action: Int, points: List<Pair<Float, Float>>): String {
        val body = points.joinToString("") { "={${it.first},${it.second}}" }
        return "SLIDEEVENT\r\n$slot\r\n$action\r\n[$body]\r\n\r\n"
    }

    fun gyroscope(x: Float, y: Float, z: Int): String = "GYROSCOPEEVENT\r\n$x\r\n$y\r\n$z\r\n\r\n"

    fun draw(mode: Int): String = record("DRAW", "$mode")

    fun paint(action: Int, x: Float, y: Float): String = record("PAINT_MSG", "$action $x $y")

    fun penColor(color: Int): String = record("PEN_COLOR", "$color")

    fun penSize(size: Int): String = "PEN_SIZE\r\n$size\r\n\r\n\r\n"

    fun points(values: List<Float>): String = record("POINTS", values.joinToString(", "))

    fun eraser(enabled: Boolean): String = record("ERASER", enabled.toString())

    fun clear(enabled: Boolean = true): String = record("CLEAR", enabled.toString())


    fun parseMediaInt(text: String, key: String): Int? {
        val regex = Regex(Regex.escape(key) + """[\s=:]*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun record(vararg lines: String): String =
        lines.joinToString("\r\n", postfix = END)
}
