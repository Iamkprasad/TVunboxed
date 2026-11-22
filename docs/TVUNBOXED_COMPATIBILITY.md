# TVUNBOXED_COMPATIBILITY

Target: AKAI ME-TV358 / CVTM358, Android 8.0, LAN address used during development `192.168.1.201`.

| Protocol / feature | Status on this TV | Evidence / requirement |
|---|---|---|
| Vendor control 8121 (`KEYEVENT`, `AIRMOUSEEVNET`, `CONTENT`, `GETAPPINFOSEVNET`) | Supported | Live probes + Python bridge history |
| Discovery UDP 48689 (vendor discovery magic) | Supported | Live scan replies |
| HTTP/WebDAV 8000 | Supported | Live PROPFIND/PUT/COPY/DELETE; `/system`,`/data` readable |
| MediaControl queries | Partial | Replies arrive asynchronously; duration/seek not verified during playback |
| `Openfile` / `OpenLink` | Partial | OpenLink used for analysis; local HTTP playback not yet live-verified |
| `Savefile` | Untested | Decompiled vendor client only |
| `GETFBINFO`, `TOUCHEVENT`, `MIRRORTOUCHEVENT`, `SLIDEEVENT`, `GYROSCOPEEVENT` | Untested | Vendor client research findings |
| DRAW/PAINT/PEN/POINTS/ERASER/CLEAR | Untested | Research findings; do not expose as functional |
| `/remote/screencapture` | Untested | Vendor client constant; format/auth unknown |
| iFlytek voice commands | Untested | Vendor-service dependent; prefer Android speech API |
| DLNA / UPnP renderer | Not tested | Android 8 ≠ DLNA support; must probe |
| Google Cast / Miracast / AirPlay | Not tested / likely absent on this firmware | Marketing docs cover other products |
| Phone→TV screen mirroring | Requires optional receiver | Vendor mirror service transport unknown; browser WebRTC not viable on old TV without test |
| ADB shell/root | Unavailable | Authorization dialog broken; key injection rejected; no root |
| Multi-vendor support | Not supported | Separate adapters required for Tizen/webOS/Roku/etc. |

Android client compatibility: minSdk 23, target/compile 35; developed for Motorola Edge 20 Pro (SDK 33). Cleartext HTTP to LAN is enabled by `network_security_config.xml` — do not expose the app or TV services to the internet.
