# WebView bridge status

UI source of truth: `design/demo2.html` (copied to `android/app/src/main/assets/demo2.html`).
Bridge: `android/app/src/main/java/com/tvunboxed/app/web/TvBridge.kt` (single `invoke` entry point, whitelisted ops).
Adapter: `android/app/src/main/assets/tvbridge.js`.

## Wired and hardware-tested
- Device discovery: real UDP scan; found `EShare-2660` at `192.168.1.201`.
- Connect/disconnect toggle: app-bar power button toggles `connect`/`disconnect`; both `ok=true` (adb verified).
- Remote keys: 5 volume-up key commands sent after connect; TV visibly responded (user confirmed).
- Air mouse: `mouse -> ok=true` on entering the tab; batched `airmouse` deltas sent on swipe (adb verified; TV cursor needs user confirmation).
- Keyboard: buffered `type` calls sent on key taps (adb verified; TV text entry needs user confirmation).

## Wired, build verified, not yet hardware-tested per action
- Volume slider / mute, power tap & hold, D-pad, back/home/menu, channel, transport (play/pause/stop/rew/ff).
- Air mouse touch pad: batched pointer deltas (33 ms), tap/right-click, pointer on/off, scroll strip.
- Air mouse motion: Android accelerometer bridged into the HTML pad.
- Keyboard: `typeText` sent live via `CONTENT`.
- Apps: real app list via `fetchApps()`; launch by package.
- Files: WebDAV browse, open on TV, download, delete.
- Tools: open link, launch package, uninstall (confirm dialog).
- Screen share (TV screen on phone): `/remote/screencapture` frames + touchback, opened from Mirror quick action. On this TV `captureFrame -> ok=false, screencapture unavailable`; Home falls back to the gradient preview.
- Home: polls `getPlayingInfo` and `captureFrame` to replace demo `holiday.mp4` when the TV provides data.
- Apps: real icon bytes from the app-list parser are rendered; list is cached and only refreshes on the explicit Refresh hint.
- Android back navigation: closes dialog → sheet → remote before exiting.

## Unsupported / not implemented (reported, not simulated)
- Phone screen → TV mirroring: needs receiver APK or vendor mirror protocol RE.
- TV storage totals / Care storage bar: no endpoint; demo values remain.
- Long-running media transfer progress: UI progress is still the demo simulation.

## Modified files
- `MainActivity.kt`, `MainViewModel.kt` (added `openFile` helper)
- `web/WebViewScreen.kt`, `web/TvBridge.kt`
- `assets/demo2.html` (script tag + bridge hooks only; no CSS/markup redesign)
- `assets/tvbridge.js` (new)
- `app/build.gradle.kts` (`androidx.webkit`, `buildConfig = true`)
