# TVUNBOXED_TEST_PLAN

## Automated (fast, no TV)
- Android unit tests: `./gradlew testDebugUnitTest` in `android/`.
  - `ProtocolTest`: discovery packet layout, CRLF records, handshake order, volume clamping, Save/Open file, touch encoding.
  - `DiscoveryTest`: discovery reply parsing and malformed input rejection.
  - `AppListParserTest`: two-record parsing, truncation tolerance.
  - `CapabilitiesTest`: default statuses, persistence mapping, visibility rules.
- Python bridge: `python3 -m unittest discover tests -v` from repo root.
- Build gate: `./gradlew assembleDebug` must produce `android/app/build/outputs/apk/debug/app-debug.apk`.

## Device tests (manual, on AKAI)
1. Discovery finds `192.168.1.201`; manual connect works when broadcast is blocked.
2. Remote: power/home/back/menu, D-pad, volume/mute, long-press OK, source switching.
3. Air mouse: enable → move → click → disable; pointer visually moves on TV.
4. Apps: list loads; favorite toggles persist; launch works; uninstall stays behind confirmation.
5. Keyboard: `CONTENT` appears in focused TV field; multiline is rejected locally.
6. Media: position/duration/volume queries while something plays; seek accuracy; play/pause.
7. Cast: local HTTP URL opens in TV player; Range requests observed; pause/seek from phone; queue and history persist.
8. TV view: `/remote/screencapture` content type, decode result, refresh rate, CPU load; touchback coordinate calibration with `GETFBINFO`.
9. Files: browse `/sdcard/`, upload, download, rename/delete with confirm; root stays read-only.
10. Care: diagnostics shows `Unavailable` for CPU/RAM; no invented numbers; APK staging still requires on-screen Install.

## Network / performance
- Same-SSID only; verify no WAN exposure of 8000/8121/8888.
- Measure frame loop at 300/1000/2000 ms; stop loop when Mirror tab inactive.
- Media server: verify Range/206 and seek; monitor phone battery/CPU during 10-minute playback.
- Reconnect after TV sleeps/wakes; heartbeat keeps control socket usable.

## Security
- No hardcoded credentials, no root/ADB bypass, no firmware writes.
- Destructive actions (delete, uninstall, Care purge) require explicit confirmation dialogs.
- Share Sheet accepts only explicit user action; streamed files stay in app cache and are cleared on demand.
- WebDAV write path restricted to `/sdcard/` in the Android client; report any successful write elsewhere as a defect.
