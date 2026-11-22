# ADB Transport — design spec (P1)

Date: 2026-11-22
Status: approved (design A), implementation pending
Depends on: none. This is sub-project **P1** of the "full TV potential" work; P2 (deepen vendor channels / UI polish) gets its own spec.

## Goal

Give the TV owner an optional, phone-only ADB control channel integrated into TVunboxed: connect to the TV over the same Wi‑Fi, run shell commands, inspect and enable/disable packages, install APKs, grab screenshots, and tail logs — from the browser UI already open on the phone. ADB is the official Android Debug Bridge; the TV grants access itself through its own Developer options screen. Nothing is installed on the TV.

## Why this does not break the project's boundaries

The repo's design spec (2026-11-22-tv-care-design.md:12-15) forbids ADB *authorization bypass*, key injection, root/privilege escalation, and writes to `/system`, `/data`, `/adb_keys`, boot/recovery. This feature uses none of those:

- Access is granted by the TV's own on-screen "Allow USB debugging" prompt, accepted with the remote **once**. No key injection, no bypass, no exploit.
- Root is out of scope entirely. ADB here runs as the unprivileged `shell` user.
- A guard layer rejects commands that target `/system`, `/data`, `/adb_keys`, boot/recovery, and `su`/`flash*`/`dd`/`reboot bootloader`.
- No firmware writes, no port-forwarding of 5555/8000/8121.

The zero-setup vendor path stays the default; ADB is an explicitly opt-in, clearly labeled feature.

## One-time physical setup (unavoidable, documented in README)

Android deliberately requires the TV remote to establish trust. The UI and README must state this plainly:

1. TV remote: Settings → Device Preferences (or System) → About → tap **Build** 7× → "You are now a developer".
2. Developer options → enable **Network debugging** (or **ADB debugging** / **USB debugging**, whichever the firmware exposes). Android 8.0 class firmware listens on fixed port **5555**.
3. From the phone run `adb connect <TV_IP>:5555`, accept the RSA prompt on the TV with the remote (tick "Always allow").

After step 3 the phone is authorized and all further control is phone-only. The README must also note that network debugging often resets after a TV reboot and that ADB should be switched off when not in use.

## Architecture — `tvunboxed/adb.py` (standard library only)

The module wraps the `adb` CLI via `subprocess`; it does not reimplement the ADB wire protocol. This keeps dependencies at zero for Python and requires only the `adb` binary on the phone (Termux: `pkg install android-tools`).

### Interfaces

- `find_adb(explicit=None) -> str` — return `explicit` if given, else `shutil.which("adb")`, else raise `AdbNotFound`.
- `guard_shell(command: str) -> str | None` — return an error string if the command is denied, else `None`. Conservative regex denylist:
  - paths: `/system`, `/data`, `/adb_keys`, `/boot`, `/recovery`, `/vendor`, `super` as path segments;
  - commands: `su`, `flash*`, `dd`, `fastboot`, `reboot bootloader`, `reboot recovery`.
  - Matching is token/word-boundary based to avoid false positives inside harmless strings; ambiguous commands are denied.
- `WRITE_CONFIRM_TOKEN = "I-OWN-THIS-TV"` — exact literal a client must supply for any state-changing ADB operation. Deliberate friction/consent, not a security boundary against a determined LAN attacker; audit-logged.
- `class AdbClient(host, port=5555, adb_path=None, runner=None, timeout=30)`:
  - `runner(argv, timeout)` is injectable for tests; default wraps `subprocess.run(argv, capture_output=True, text=True)`. Returns an object with `returncode`, `stdout`, `stderr`.
  - Device-scoped commands use `adb -s <host>:<port> ...`; `connect`/`disconnect` are server-scoped.
  - `connect() / disconnect() -> Result`
  - `devices() -> list[{"target","state"}]` — parses `adb devices` (`device`, `unauthorized`, `offline`).
  - `state() -> str` — `connected | unauthorized | offline | not-connected | adb-missing`.
  - `shell(command, confirm=None)` — requires `confirm == WRITE_CONFIRM_TOKEN`; then `guard_shell`; then `adb -s ... shell <command>` (split with `shlex.split`). Returns `{"ok","stdout","stderr","error"}`.
  - `pm_list() -> {"third_party": [...], "system": [...]}` via `pm list packages -3` / `-s`. Read-only.
  - `pm_toggle(package, enabled, confirm)` — requires token; package validated against `^[A-Za-z0-9_.]+$`; `pm enable <pkg>` or `pm disable-user --user 0 <pkg>` (reversible).
  - `install(local_path, confirm)` — requires token; validates local path is a regular `.apk` file and does not escape its directory; `adb install -r <path>`.
  - `uninstall(package, confirm)` — requires token; package validation as above; `adb uninstall <pkg>`.
  - `screenshot() -> bytes` — `adb exec-out screencap -p`; served as `image/png`.
  - `logcat(lines=200) -> str` — `adb logcat -d -t <n>` with `1 <= n <= 1000`.
- All results follow the existing `{ok:false, error}` convention on failure.
- `Result` = plain dict; no new third-party types.

### Error handling

- Missing `adb` binary → `AdbNotFound`, surfaced as 502 with an actionable message ("install android-tools / adb on this device").
- `adb connect` failure, `unauthorized`, `offline` → `ok:false` with the captured stderr; UI shows the state and reproduces the one-time setup hint.
- Guard rejection or bad confirm token → 400 with the specific reason; nothing is executed.
- Every ADB call is audit-logged via `log_event(server, "adb_*", op=..., target=..., confirm=bool)`.

## HTTP endpoints in `tvunboxed/server.py`

Thin handlers delegate to `AdbClient` built from `self.server.session.host` and `self.server.adb_port`. The port is plumbed like the existing `tv_webdav_port`: a `--adb-port` argument in `__main__.py` (default from env `TVUNBOXED_ADBPORT`, else 5555) passed into `run(...)` and stored on the server. GET routes:

- `GET /api/adb/status` — `{ok, available, adb_path, target, state, write_confirm_token}`. Read-only; does not initiate a connection.
- `GET /api/adb/apps` — package classification from `pm_list()`.
- `GET /api/adb/screenshot` — streams PNG bytes (`image/png`).
- `GET /api/adb/logcat?lines=N` — `text/plain`, bounded.

POST routes (JSON body unless noted):

- `POST /api/adb/connect` — `{host?, port?}`; defaults to the bridge's TV IP and 5555.
- `POST /api/adb/disconnect` — `{}`.
- `POST /api/adb/shell` — `{command, confirm}`; token required for every shell call (shell is inherently state-capable).
- `POST /api/adb/toggle` — `{package, enabled, confirm}`.
- `POST /api/adb/install` — `{path, confirm}`.
- `POST /api/adb/uninstall` — `{package, confirm}`.

Conventions: 502 on transport/adapter failures, 400 on validation/guard failures, `log_event` on each call. POST handlers must use the response-sent guard (`_response_sent` / `_send_json`) established by the TV Care work so an error path cannot emit two responses.

## CLI — `tvunboxed/adb_cli.py` + dispatch

`python3 -m tvunboxed adb --tv IP [--adb-port N] [--adb-path PATH] <command> [args...]`

Commands: `status`, `connect`, `disconnect`, `shell <command>`, `apps`, `toggle <pkg> on|off`, `install <path>`, `uninstall <pkg>`, `screenshot <out.png>`, `logcat [--lines N]`. State-changing commands require `--yes I-OWN-THIS-TV`. Add an `adb` dispatch at the top of `main()` in `__main__.py`, mirroring the existing `care` dispatch.

## UI (`web/index.html`, new "ADB" tab)

- Setup card with the four one-time remote steps and the "network debugging may reset after reboot / turn ADB off when done / never port-forward" reminder.
- Connection card: target field, state badge, Connect / Disconnect buttons.
- Command card: textarea + Run button + token input; output preformatted.
- Apps card: third-party/system package list with Enable/Disable buttons (token input shared).
- Install card: local APK path + Install button.
- Screenshot image viewer and a logcat tail panel.
- Follow the existing design system: 2px borders, no radius, mono labels, dark/light variables, no emoji; use the `call()` helper.

## Tests (`tests/test_adb.py`)

Inject a `FakeRunner` mapping argv tuples to result objects — no real device or `adb` binary needed.

- `find_adb` honors an explicit path and falls back to PATH.
- `connect`/`disconnect` build correct argv; `devices()` parses `device`/`unauthorized`/`offline` and `state()` maps them.
- `guard_shell` rejects `/system`, `/data`, `/adb_keys`, `su`, `flash*`, `dd`, `reboot bootloader`; allows benign commands (e.g. `getprop ro.build.version.release`, `pm list packages -3`).
- `shell` refuses to run without the exact token and refuses a guarded command even with the token.
- `pm_toggle` builds `pm disable-user --user 0 <pkg>` / `pm enable <pkg>`; rejects package names with shell metacharacters.
- `install` rejects non-`.apk` and traversal/non-regular paths.
- `screenshot` returns raw bytes; `logcat` clamps line count.
- Endpoint-layer tests assert token enforcement and 400/502 codes.

## Verification

- `python3 -m unittest discover tests -v` — gate (no lint/typecheck configured in this repo).
- Live, after the user performs the one-time remote setup: `python3 -m tvunboxed adb --tv 192.168.1.201 status`, then `connect`, then the read-only `shell "getprop ro.build.version.release"`. No destructive live action without an explicit token from the user.

## Non-goals

- No root, exploit, bootloader unlock, or Magisk.
- No ADB authorization bypass or key injection; the TV's own prompt is the only trust path.
- No writes to `/system`, `/data`, `/adb_keys`, boot/recovery; no firmware flashing; no port-forwarding.
- No changes to the vendor control/WebDAV channels — that is P2.
- No commit beyond the spec doc until implementation is explicitly requested.
