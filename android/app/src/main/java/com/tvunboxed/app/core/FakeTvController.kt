package com.tvunboxed.app.core

/**
 * Fake implementation of [TvController] matching the demo's behaviour:
 * Living Room / E65, 93% battery, 4G ~1.5 Mb/s, latency random 8–18 ms.
 */
class FakeTvController : TvController {
    override val connected = true
    override val tvName = "Living Room"
    override val tvModel = "E65"
    override val latencyMs = (8 + Math.floor(Math.random() * 11)).toInt()
    override val phoneBattery = 93
    override val networkLabel = "4G · 1.5 Mb/s"
    override fun pressKey(key: Int, longPress: Boolean) {}
    override fun setVolume(level: Int) {}
    override fun setMute(on: Boolean) {}
    override fun setSource(index: Int) {}
    override fun airMouse(dx: Float, dy: Float, button: Int) {}
    override fun airClick() {}
    override fun typeText(text: String) {}
    override fun launchApp(packageName: String) {}
    override fun uninstallApp(packageName: String) {}
    override fun openLink(url: String) {}
    override fun saveFile(url: String, mime: String) {}
    override fun mediaCommand(command: String) {}
    override fun seekToMs(positionMs: Long) {}
    override fun refreshApps(): List<TvApp> = emptyList()
}
