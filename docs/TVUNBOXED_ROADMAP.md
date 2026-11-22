# TVUNBOXED_ROADMAP

## Vision
TVunboxed is a lightweight, modular Android-to-TV control platform. Compatibility with the target TV's LAN control protocol is the first adapter — not the foundation. The Python bridge remains the reference implementation and Termux fallback; the Android app is the primary family-facing client.

## Keep / Modify / Add

**Keep unchanged**: vendor wire protocol, discovery packet, control handshake, keepalive, key/volume/source, app-list binary parser, CONTENT text input, WebDAV workflow, Care two-step cleanup guards, Python CLI/backward compatibility.

**Modify**: media reply correlation/seek, transfer queue UX, capability detection persistence, security policy for WebDAV writes and destructive actions, keyboard/IME ergonomics.

**Add in Android v1**: 5-section UI (Remote/Cast/Mirror/Files/TV Care), capability registry, local HTTP media streaming with Range, Share Sheet intake, TV screen viewer with touchback research shell, diagnostics report, favorites/history, app manager.

## Priorities

- **P0 (done in v0.1)**: Android project builds; protocol unit tests green; discovery/session/app parser/WebDav/media server; 5-section UI; capability-gated feature display.
- **P1**: live validation of screencapture + GETFBINFO + Savefile on AKAI; local HTTP streaming confirmation via OpenLink; reliability/keepalive polish; guarded Care parity.
- **P2**: DLNA/UPnP investigation, annotations if DRAW commands verified, TV Care diagnostics enrichment, macros/profiles, Quick Settings tile.
- **P3**: optional TV receiver APK for MediaProjection/WebRTC mirroring, camera streaming, multi-TV/Home Assistant.

## Phases and acceptance criteria

1. **Existing functionality completion** — Android app connects to the AKAI, discovers apps, sends keys/text, browses WebDAV; all unit tests and `gradle assembleDebug` pass. ✅
2. **Media streaming** — selecting a local video creates a phone URL, TV opens it, play/pause/seek work where supported; SEND TO TV distinct from COPY TO TV.
3. **TV view / touchback** — screencapture returns a decodable image at a configurable rate; taps map correctly using framebuffer dimensions; polling stops when the tab is inactive.
4. **Management/automation** — diagnostics report contains only collected values; favorites/macros configurable; destructive actions remain double-confirmed.
5. **Advanced** — receiver-based mirroring/camera only after receiver POC meets latency and CPU targets (720p30 baseline).

No phase starts before the previous one's acceptance checks pass on the physical AKAI TV.
