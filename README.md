<p align="center">
<img src="assets/logo.svg" width="132" alt="TVunboxed — cassette-logo TV companion" />
</p>

# TVunboxed

Control the TV from your browser on the same Wi‑Fi — power, volume, channels, apps, IPTV, file transfer, a file manager for the TV's own storage, and a health dashboard. No adb, no root, nothing to install on the TV, no account, no cloud.

Built and verified on a partner smart-TV (Android 8.0, firmware with the built-in LAN control service). It talks a single specific control protocol; see **Compatibility**.

## What it can do

| Area | What you get | Status |
|---|---|---|
| **Remote** | Power, volume, mute, channels, D-pad, back/home/menu, media keys — as "Side A" | ✅ verified live |
| **Air mouse** | Drag pad + click; the TV enters pointer mode | ⚠️ built, unverified on hardware |
| **IPTV** | Load your own `.m3u`/`.m3u8`, play on the phone or push the stream to the TV | ⚠️ built, unverified on hardware |
| **Apps** | Reads the TV's installed-app list (binary protocol over a dedicated socket); tap to launch | ⚠️ parsing unit-tested, unverified on hardware |
| **Files (phone → TV)** | Browse this phone's share folder; "TV ▶" opens/plays the file on screen | ✅ verified live (protocol-level) |
| **TV file manager** | Browse the TV's own storage: upload, download (302 direct), rename, delete, mkdir, quota | ⚠️ request format recovered, unverified on hardware |
| **Dashboard** | TV link latency, model from features, WebDAV reachability, phone battery, phone network, bridge IP/uptime | ✅ renders; TV values real when TV awake |
| **Type on TV** | Push text into the TV's focused field | ⚠️ unverified |
| **Uninstall TV apps** | Destructive; double-confirmed | ⚠️ unverified |
| Screen mirroring | **Impossible by design** (browser/phone can't push frames to this firmware) | not supported |
| App-store installs from a catalog | Intentionally not reproduced | not supported |

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
