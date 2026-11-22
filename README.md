<p align="center">
<img src="assets/logo.svg" width="132" alt="TVunboxed — cassette-logo TV companion" />
</p>

# TVunboxed

Control the TV from your browser on the same Wi‑Fi — power, volume, channels, apps, IPTV, file transfer, a file manager for the TV's own storage, and a health dashboard. No adb, no root, nothing to install on the TV, no account, no cloud.

Built and verified on a partner smart-TV (Android 8.0, firmware with the built-in LAN control service). It talks a single specific control protocol; see **Compatibility**.

## Android app (new)
A native Kotlin/Compose client lives in `android/` with five sections — Remote, Cast, Mirror, Files and TV Care. It uses the same vendor-compatible protocol, adds device capability detection, phone-hosted HTTP media streaming, a TV screen viewer with touchback research shell, WebDAV file manager and guarded TV Care. Build with `cd android && ./gradlew assembleDebug` (JDK 17/21, Android SDK 35); the APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. See `docs/TVUNBOXED_*.md` for audit, roadmap, architecture, compatibility and test plan. The Python bridge remains the reference implementation and Termux fallback.

## What it can do

| Area | What you get | Status |
|---|---|---|
| **Remote** | Power, volume, mute, channels, D-pad, back/home/menu, media keys — as "Side A" | ✅ verified live |
| **Air mouse** | Drag pad + click; the TV enters pointer mode | ✅ verified live (2026‑11‑17: enable/move/click/disable) |
| **IPTV** | Load your own `.m3u`/`.m3u8`, play on the phone or push the stream to the TV; filter boxes narrow presets and playlists | ⚠️ built; in‑page playback works, TV ▶ not live‑verified |
| **Apps** | Reads the TV's installed-app list (binary protocol over a dedicated socket); tap to launch | ✅ verified live (2026‑11‑17: 18 apps parsed + launched TV Setting) |
| **Files (phone → TV)** | Browse this phone's share folder; "TV ▶" opens on the TV, "TV ⤒" copies into the TV's Upload folder | ✅ verified live (when TV is awake, correct auth/share path) |
| **TV file manager** | Browse the TV's own storage: upload, download (302 direct), rename, delete, mkdir, quota | ✅ verified live (2026‑11‑17: list/quota; upload→list→delete self-test OK; root read-only 403 confirmed) |
| **Dashboard** | TV link latency, model from features, WebDAV reachability, phone battery, phone network, bridge IP/uptime | ✅ renders; TV values real when TV awake |
| **Playback readout** | Position/duration from TCP MediaControl (async replies; parse what arrives); seek only enabled when the TV reports duration > 0 | ⚠️ built; read path returns idle when nothing plays; seek untested (needs active playback) |
| **Type on TV** | Push text into the TV's focused field | ✅ verified live (CONTENT into the TV browser field) |
| **Install APK on TV** | Server-side download/install trigger exists in protocol; not exposed/tested | ⚠️ not tested (can interrupt TV) |
| **Uninstall TV apps** | Destructive; double-confirmed | ⚠️ unverified (guard verified: refuses without `confirm:"uninstall"`) |
| Screen mirroring | **Impossible by design** (browser/phone can't push frames to this firmware) | not supported |
| App-store installs from a catalog | Intentionally not reproduced | not supported |

## TV Care (new tab)

The **Care** tab audits installed apps and `/sdcard` files, then lets you reclaim user storage in two guarded steps: selected files are copied to `/sdcard/backups/care/` on the TV and removed from their original location; space is freed only after you explicitly purge those backups with the second confirmation token. APKs can be staged to `/sdcard/Upload/` and opened through the TV's own installer (you still tap Install on screen).

CLI: `python3 -m tvunboxed care --tv IP --audit` or `--dry-clean`; `--stage FILE --yes stage` uploads an APK. Scope is strictly `/sdcard` — no ADB authorization bypass, root, firmware writes, or system-package removal. This firmware's WebDAV service exposes `/` (including `/system` and `/data`); keep port 8000 firewalled to your LAN.

## Compatibility — this is NOT universal

TVunboxed speaks **one specific protocol** — the control service baked into the firmware of the target TV family. It will **not** control Samsung (Tizen), LG (webOS), Sony, Roku, or other Android TVs without additional backends (each is its own reverse-engineering project). What it *does* cover is every TV running that same firmware family: discovery finds them automatically on the LAN. If your TV model is different, treat this as reference code for porting, not a drop-in solution.

## Requirements

- Any modern Linux with **Python 3.9+** (Termux on Android works well — this project is developed there)
- Phone/computer and the TV on the **same Wi‑Fi/LAN**
- The TV powered on and joined to Wi‑Fi
- Network access for the phone browser: CDN (fonts) and nothing else — the bridge itself runs fully offline
- No root, no developer options needed on the phone

## Run

```sh
git clone https://github.com/Iamkprasad/TVunboxed.git && cd TVunboxed
./start.sh                       # auto-discover the TV, serve http://127.0.0.1:8080
./start.sh --tv <tv-ip>          # or specify it
./start.sh --lan                 # also reachable from other devices on the network
```

Open `http://127.0.0.1:8080` in a browser on the same device (or `http://<this-machine-ip>:8080` with `--lan`).

Useful flags: `--http-port N`, `--bind IP`, `--share DIR` (default `/sdcard/Download` if present; read-only, exposed to the TV at `/files/`), `--no-share`, `--discover` (print TVs and exit), `--tv-webdav-port N` (default 8000).

Termux tips: `pkg install python git` · `termux-wake-lock` · disable battery optimization for Termux or Android will kill the bridge.

## What it is not

- Not a multi-vendor universal remote (see Compatibility)
- Not a server-side/cloud service — nothing leaves your LAN
- Not authenticated — anyone on your Wi‑Fi can send the same commands; **never port-forward it**, never run it on untrusted Wi‑Fi
- Not affiliated with the TV vendor. The TV's own services are unauthenticated by the vendor's design; this app merely speaks them.

## Design

"Mixtape" — cassette-forward Y2K. Dark and light mode (toggle in the app bar). See [DESIGN.md](DESIGN.md) for the full design system, [METHODOLOGY.md](METHODOLOGY.md) for provenance.

## Tests

```sh
python3 -m unittest discover tests -v
```

## License

MIT — see [LICENSE](LICENSE).

Third-party components vendored in `web/`: [`hls.js`](https://github.com/video-dev/hls.js) (Apache‑2.0), reproduced via CDN download of the official build (`web/hls.min.js`). "Free channels" listed in the UI are free/public streams; their availability and terms are separate from this project.
