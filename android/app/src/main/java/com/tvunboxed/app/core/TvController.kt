package com.tvunboxed.app.core

/**
 * TV layer interface. The app talks to the TV through this only, so the
 * vendor session can be swapped in later. FakeTvController implements it for
 * now (demo data: Living Room / E65 / 93% battery / 4G).
 */
interface TvController {
    val connected: Boolean
    val tvName: String
    val tvModel: String
    val latencyMs: Int
    val phoneBattery: Int
    val networkLabel: String
    fun pressKey(key: Int, longPress: Boolean = false)
    fun setVolume(level: Int)
    fun setMute(on: Boolean)
    fun setSource(index: Int)
    fun airMouse(dx: Float, dy: Float, button: Int = 0)
    fun airClick()
    fun typeText(text: String)
    fun launchApp(packageName: String)
    fun uninstallApp(packageName: String)
    fun openLink(url: String)
    fun saveFile(url: String, mime: String)
    fun mediaCommand(command: String)
    fun seekToMs(positionMs: Long)
    fun refreshApps(): List<TvApp>
}
