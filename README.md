<p align="center">
<img src="assets/logo.svg" width="132" alt="TVunboxed logo — a cassette tape" />
</p>

# TVunboxed

Control the TV from your browser on the same Wi‑Fi — no adb, no root, no apps on the TV. Original implementation; see [METHODOLOGY.md](METHODOLOGY.md) and [DESIGN.md](DESIGN.md).

Design system ("Mixtape"): cassette-forward Y2K. Dark and light mode (toggle in the app bar). See [DESIGN.md](DESIGN.md).

## Quick start (Termux)

```sh
pkg install python git
git clone https://github.com/Iamkprasad/TVunboxed.git && cd TVunboxed
./start.sh
```

That auto-discovers the TV and serves `http://127.0.0.1:8080` — open it in the phone's browser. For access from other devices on the network add `--lan` (trusted Wi‑Fi only). Or pass `--tv <ip>` if discovery fails. Use `termux-wake-lock` and disable battery optimizations for Termux.

> `--lan` binds `0.0.0.0`. The control protocol and this bridge are unauthenticated plaintext. Trusted Wi‑Fi only. For phone-only browsing, omit `--lan` (binds localhost).

## Tabs

- **Home** — device health: TV link + model + latency, WebDAV, battery and network of this phone, bridge IP/uptime
- **Files** — browse this phone's share folder; tap **TV ▶** to play on the TV
- **Remote** — D-pad or Air Mouse (Side A / Side B), power, volume, media keys
- **TV** — installed apps (tap to launch), IPTV playlists (.m3u), and the TV's built-in file manager (read/write/rename/delete/mkdir/quota)
- **Tools** — type into the focused field, open any URL on the TV, launch apps, danger zone

## CLI

`python3 -m tvunboxed [--tv IP] [--port N] [--bind IP] [--http-port N] [--lan] [--discover] [--share DIR] [--no-share]`

Without `--tv`, LAN discovery is used; exactly one TV must be found. `--share DIR` (default: `/sdcard/Download` if it exists) serves that directory read-only to the TV at `/files/`. Use `--no-share` to disable.

## REST (POST JSON)

| Route | Body | Action |
|---|---|---|
| `/api/key` | `{"code":24,"long":false}` | Key code; `long` = long-press |
| `/api/volume` | `{"level":30}` | absolute volume 0–100 |
| `/api/mute` | `{}` | toggle mute |
| `/api/source` | `{"index":0}` | switch input source |
| `/api/app` | `{"package":"com.example"}` | launch app by package |
| `/api/tvapps` | — | list installed apps on the TV |
| `/api/openfile` | `{"path":"a/b.mkv"}` or `{"path":"https://x/s.m3u8"}` | open on TV (share-relative or absolute URL) |
| `/api/open-url` | `{"url":"https://…"}` | same, absolute URL |
| `/api/browser` | `{}` | open the TV's browser |
| `/api/airmouse` | `{"dx":2.5,"dy":-1}` | relative pointer deltas |
| `/api/airclick` | `{}` | pointer click |
| `/api/mouse-mode` | `{"on":true}` | enter/leave TV pointer mode |
| `/api/type` | `{"text":"hello"}` | type into the focused field |
| `/api/uninstall` | `{"package":"x","confirm":"uninstall"}` | **uninstall a TV app** |
| `/api/dav/list?dir=/` | — | list a TV directory |
| `/api/dav/get?path=/a/b` | — | 302 redirect to the file URL |
| `/api/dav/put` | raw body, `X-File-Path: a/b.mkv` | upload to TV |
| `/api/dav/delete` `/api/dav/mkcol` `/api/dav/move` | `{"path":…}` / `{"from","to"}` | TV file management |
| `GET /api/files?dir=.` `/files/<path>` | — | phone share listing / raw file |
| `GET /api/features` `/api/status` `/api/health` | — | TV identity, bridge status, latency |

Keys are platform key codes: 24/25 vol, 164 mute, 19–23 d-pad/OK, 4 back, 3 home, 82 menu, 26 power, 66 enter, 85 play/pause, 86 stop, 87 next, 88 rewind, 90 ff, 166–167 channel −/+.

## Tests

```sh
python3 -m unittest discover tests -v
```

## Security

- The TV's control port performs **no authentication**. Anyone on your LAN can send the same commands as this app — that's inherent to the firmware.
- The TV also exposes an unauthenticated file/WebDAV service on port 8000 — used by the TV tab. Treat your Wi‑Fi as trusted.
- Never port-forward this bridge. Never run it on untrusted networks.

## Protocol (observed, black-box)

- **Discover**: UDP broadcast to port 48689; reply is a colon-separated record `<ver>:<name>:<controlPort>:<speakerPort>:<block>:sn=0x…:<flags>`.
- **Control**: TCP to control port. Handshake: `sayHello\r\n<model>\r\n\r\n`, then `auth\r\n<path> <sourcePort> <marker>\r\n\r\n` (`sourcePort` = this bridge's `--http-port`). Features: `getFeatures\r\n<model>\r\n\r\n` → `server_features:…`. Keepalive: `HeartBeat\r\nlive\r\n\r\n`.
- **Keys**: `KEYEVENT\r\n<code>\r\n\r\n` (`longpress` variant exists). Volume: `MediaControl\r\nsetVolume <0..100>\r\n\r\n`.
- **Open on TV**: `Openfile\r\nOpen <url-encoded path> <mime>\r\n\r\n` — the TV fetches that path from the `http://<phone>:<sourcePort>` origin named in `auth`. Absolute `http(s)` URLs also work (streams).
- **Apps**: `STARTAPPEVNET\r\n<pkg>`; list: `GETAPPINFOSEVNET` on a dedicated socket (binary, length-prefixed records).
- **Air mouse**: `AIRMOUSEEVNET\r\n<float x>\r\n<float y>\r\n<button>\r\n\r\n`; pointer mode `MOUSEENABLEEVENT\r\n0|1\r\n\r\n`; click `AIRCLICKEVENT\r\n1\r\n\r\n`.

## License

MIT — see [LICENSE](LICENSE). Not affiliated with the TV vendor. Third-party trademarks belong to their owners.
