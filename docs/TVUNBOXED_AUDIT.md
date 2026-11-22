# TVUNBOXED_AUDIT

Audit date: 2026-11-22. Scope: existing Python bridge, vendor client protocol research, and the new Android client under `android/`.

## Feature audit

| Feature | Existing code | Actual verification | Recommended action |
|---|---|---|---|
| UDP discovery (48689, vendor discovery magic) | `tvunboxed/discovery.py`, `protocol.py`; Android `core/Discovery.kt` | Verified live on AKAI; Android parser covered by unit test | Keep, harden multicast handling |
| 8121 control session + auth handshake | `tvunboxed/client.py`; Android `core/VendorSession.kt` | Verified live | Keep, add explicit session tokens |
| Key events / long press | `protocol.key`, Android `Protocol.key` | Verified live | Keep |
| Volume, mute, source | `protocol.volume/mute/switch_source` | Verified live | Keep |
| Keepalive | `protocol.keepalive_message` | Verified live | Keep; Android 15s loop |
| Air mouse / click / mouse enable | `protocol.airmouse/airclick` | Verified live 2026-11-17 | Keep |
| App list + launch | `tvapps.py`; Android `AppListParser.kt` | Verified (18 apps), unit-tested parser | Keep, add icon cache |
| Text input (`CONTENT`) | `protocol.type_text` | Verified into TV browser | Keep |
| OpenLink / media queries / seek | `protocol.open_link`, `media_*` | Partial: replies async, seek untested | Modify: better reply correlation |
| File share phone→TV, OpenFile/Savefile | `share.py`, `server.py`; Android `Protocol.openFile/saveFile` | Verified OpenFile; Savefile research-only | Verify Savefile live |
| WebDAV file manager 8000 | `tvdav.py`; Android `WebDavClient.kt` | Verified list/upload/delete | Keep, add path allowlists |
| TV Care audit/clean/stage | `tvcare.py`, `care_cli.py` | Implemented with two-step confirmations | Keep, add export |
| Uninstall app | `protocol.uninstall_app` | Command present, unverified/destructive | Keep guarded |
| ADB transport | `tvunboxed/adb.py`, spec/plan docs | Blocked: TV authorization dialog absent, key injection rejected | Separate optional track |
| TV screencapture `/remote/screencapture` | Research finding; Android `MainViewModel.captureFrame` | Untested on AKAI | Investigate: format, auth, rate |
| Touchback `GETFBINFO`/`TOUCHEVENT` | Research finding; Android stub (`touchAt`) | Untested; coordinates unconfirmed | Investigate |
| Annotation commands | Research finding; Android `Protocol` builders | Untested | Do not expose as supported |
| Phone→TV mirroring | Vendor screen-capture service finding | Flow/encoding/transport unknown | Requires separate receiver |
| DLNA / Cast / Miracast / AirPlay | Web docs only | Not tested on this TV | Capability detection only |

## Verified vs claimed
- **Verified**: remote keys, air mouse, app list/launch, text input, WebDAV file operations, Care guards.
- **Partial**: media readout/seek, OpenLink reliability, APK staging.
- **Research only**: screencapture, touchback, annotation, Savefile, vendor voice input, vendor mirror service.
- **Not supported**: root/firmware writes, ADB authorization bypass, universal multi-vendor control.

## Risks and limitations
- Vendor services on 8000/8121/48689 are unauthenticated; Android client adds no WAN protection — keep on trusted LAN.
- WebDAV exposes `/system` and `/data` read-only on this firmware; writes under `/sdcard` only in the new client until policy is tightened.
- Android 8 TV hardware is slow: no 1080p60 assumption, no aggressive polling.
- Compose UI capability chips are evidence-driven but some capabilities default to `NOT_TESTED`; they must not be advertised as working.
- Build requires JDK 17/21 (not JDK 25 — Kotlin 2.0.21 cannot parse its version string).
