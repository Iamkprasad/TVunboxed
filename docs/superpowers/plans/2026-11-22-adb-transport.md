# ADB Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, official-ADB transport to TVunboxed — connect over Wi‑Fi, run guarded shell commands, manage packages, install APKs, screenshot, and tail logs, all from the phone UI — without bypassing authorization or touching root/system paths.

**Architecture:** A new stdlib-only `tvunboxed/adb.py` wraps the `adb` CLI via an injectable runner, with a guard denylist and a write-confirmation token. Thin HTTP endpoints in `server.py` delegate to it; a CLI and a new "ADB" tab expose the same operations. The existing zero-setup vendor path stays the default.

**Tech Stack:** Python 3.9+, standard library only, existing `tvunboxed.server` / `client.Session` patterns, vanilla HTML/CSS/JS, `unittest`.

## Global Constraints

- Python 3.9+, standard library only. No new third-party dependencies.
- ADB is official-only: no authorization bypass, no key injection, no root/exploit, no firmware flashing.
- No writes to `/system`, `/data`, `/adb_keys`, boot/recovery, vendor/super; no `su`, `flash*`, `dd`, `fastboot`, `reboot bootloader`/`recovery`.
- Every state-changing ADB operation requires `confirm == adb.WRITE_CONFIRM_TOKEN` (`"I-OWN-THIS-TV"`), verbatim.
- Never port-forward 5555/8000/8121. ADB stays on a trusted LAN and should be toggled off when unused.
- One-time physical TV setup (Developer options + accept RSA prompt) is required and must be documented in README and in the UI.
- All failures follow `{ok:false, error}`; 400 for validation/guard, 502 for transport/adapter errors.
- No commit unless the user explicitly asks. Every task ends with "No commit".
- Tests are the gate: `python3 -m unittest discover tests -v` (no lint/typecheck tool configured).

---

### Task 1: ADB core — discovery, guard, connection state

**Files:**
- Create: `tvunboxed/adb.py`
- Test: `tests/test_adb.py`

**Interfaces:**
- Consumes: nothing external.
- Produces: `WRITE_CONFIRM_TOKEN`, `AdbNotFound` (subclass of `ConnectionError`), `find_adb(explicit=None) -> str`, `guard_shell(command) -> str|None`, `AdbClient(host, port=5555, adb_path=None, runner=None, timeout=30, binary_runner=None)`, `.target` property, `.connect()`, `.disconnect()`, `.devices()`, `.state()`. Runner signature is `runner(argv, timeout, binary=False) -> proc` with `.returncode`, `.stdout`, `.stderr`.

- [ ] **Step 1: Write failing tests** in `tests/test_adb.py`:

```python
import unittest
from unittest.mock import patch

from tvunboxed import adb


class FakeProc:
    def __init__(self, stdout="", stderr="", returncode=0):
        self.stdout = stdout
        self.stderr = stderr
        self.returncode = returncode


class RecordingRunner:
    def __init__(self, stdout="", stderr="", returncode=0):
        self.stdout = stdout
        self.stderr = stderr
        self.returncode = returncode
        self.calls = []

    def __call__(self, argv, timeout, binary=False):
        self.calls.append((argv, timeout, binary))
        out = self.stdout
        if binary and isinstance(out, str):
            out = out.encode("utf-8")
        err = self.stderr
        if binary and isinstance(err, str):
            err = err.encode("utf-8")
        return FakeProc(out, err, self.returncode)


def make_client(runner):
    return adb.AdbClient("192.168.1.201", 5555, adb_path="adb", runner=runner)


class TestAdbCore(unittest.TestCase):
    def test_find_adb_prefers_explicit_path(self):
        self.assertEqual(adb.find_adb("/opt/platform-tools/adb"), "/opt/platform-tools/adb")

    def test_find_adb_missing_raises(self):
        with patch("tvunboxed.adb.shutil.which", return_value=None):
            with self.assertRaises(adb.AdbNotFound):
                adb.find_adb()

    def test_adb_not_found_is_connection_error(self):
        self.assertTrue(issubclass(adb.AdbNotFound, ConnectionError))

    def test_guard_allows_benign_commands(self):
        self.assertIsNone(adb.guard_shell("getprop ro.build.version.release"))
        self.assertIsNone(adb.guard_shell("pm list packages -3"))
        self.assertIsNone(adb.guard_shell("input keyevent 4"))

    def test_guard_rejects_dangerous_commands(self):
        for command in ("cat /system/build.prop", "echo x > /data/local/tmp/x",
                        "cat /adb_keys", "su -c id", "flashboot x",
                        "dd if=/dev/zero of=/data/x", "reboot bootloader"):
            with self.subTest(command=command):
                self.assertIsNotNone(adb.guard_shell(command))

    def test_guard_rejects_empty_and_newlines(self):
        self.assertIsNotNone(adb.guard_shell(""))
        self.assertIsNotNone(adb.guard_shell("id\necho hi"))

    def test_guard_rejects_shell_substitution(self):
        self.assertIsNotNone(adb.guard_shell("getprop $(su -c id)"))
        self.assertIsNotNone(adb.guard_shell("echo `su -c id`"))

    def test_connect_builds_expected_argv(self):
        runner = RecordingRunner(stdout="connected to 192.168.1.201:5555\n")
        result = make_client(runner).connect()
        self.assertTrue(result["ok"])
        self.assertEqual(runner.calls[0][0], ["adb", "connect", "192.168.1.201:5555"])

    def test_failed_connect_reports_error(self):
        runner = RecordingRunner(stdout="failed to connect to 192.168.1.201:5555\n", returncode=1)
        result = make_client(runner).connect()
        self.assertFalse(result["ok"])
        self.assertIn("error", result)

    def test_connect_requires_success_text_and_zero_exit(self):
        runner = RecordingRunner(stdout="connected to 192.168.1.201:5555\n", returncode=1)
        self.assertFalse(make_client(runner).connect()["ok"])

    def test_disconnect_builds_expected_argv(self):
        runner = RecordingRunner(stdout="disconnected 192.168.1.201:5555\n")
        make_client(runner).disconnect()
        self.assertEqual(runner.calls[0][0], ["adb", "disconnect", "192.168.1.201:5555"])

    def test_devices_parses_states(self):
        text = ("List of devices attached\n"
                "192.168.1.201:5555\tdevice\n"
                "192.168.1.202:5555\tunauthorized\n")
        runner = RecordingRunner(stdout=text)
        client = make_client(runner)
        devices = client.devices()["devices"]
        self.assertEqual(len(devices), 2)
        self.assertEqual(devices[1]["state"], "unauthorized")
        self.assertEqual(client.state(), "device")

    def test_state_not_connected(self):
        runner = RecordingRunner(stdout="List of devices attached\n")
        self.assertEqual(make_client(runner).state(), "not-connected")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m unittest tests.test_adb -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'tvunboxed.adb'`.

- [ ] **Step 3: Implement `tvunboxed/adb.py` foundation**

```python
"""Optional network-ADB transport. Standard library only.

Wraps the official `adb` CLI. Access is granted by the TV's own on-screen
authorization prompt; this module never injects keys or bypasses authorization.
State-changing operations require an explicit confirmation token.
"""

import os
import re
import shlex
import shutil
import subprocess

WRITE_CONFIRM_TOKEN = "I-OWN-THIS-TV"

_DENY_PATHS = re.compile(
    r"(?:^|[\s/])(?:system|data|adb_keys|boot|recovery|vendor|super)(?:/|\b)",
    re.IGNORECASE,
)
_DENY_COMMANDS = re.compile(
    r"(?:^|[\s;|&(`$])(?:su|flash\w*|dd|fastboot|reboot\s+(?:bootloader|recovery))\b",
    re.IGNORECASE,
)
_PACKAGE_RE = re.compile(r"^[A-Za-z0-9_.]+$")


class AdbNotFound(ConnectionError):
    pass


def find_adb(explicit=None):
    path = explicit or shutil.which("adb")
    if not path:
        raise AdbNotFound(
            "adb not found; install android-tools (Termux: pkg install android-tools) "
            "or pass an explicit adb path"
        )
    return path


def guard_shell(command):
    """Return an error string if a shell command is denied, else None."""
    if not isinstance(command, str) or not command.strip():
        return "empty command"
    if "\r" in command or "\n" in command:
        return "newlines are not allowed"
    if any(ch in command for ch in ("$", "`", "(", ")")):
        return "shell substitution is not allowed"
    if _DENY_PATHS.search(command) or _DENY_COMMANDS.search(command):
        return "command is blocked by the ADB guard"
    return None


def _default_runner(argv, timeout, binary=False):
    return subprocess.run(argv, capture_output=True, timeout=timeout, text=not binary)


def _result(proc, binary=False):
    stdout = proc.stdout or (b"" if binary else "")
    stderr = proc.stderr or (b"" if binary else "")
    ok = proc.returncode == 0
    out = {"ok": ok, "stdout": stdout, "stderr": stderr}
    if not ok:
        detail = (stderr or stdout or "adb command failed")
        out["error"] = detail.decode("utf-8", "replace") if isinstance(detail, bytes) else str(detail)
    return out


class AdbClient:
    def __init__(self, host, port=5555, adb_path=None, runner=None, timeout=30,
                 binary_runner=None):
        self.host = host
        self.port = int(port)
        self.adb_path = find_adb(adb_path)
        self.runner = runner or _default_runner
        self.binary_runner = binary_runner or runner or _default_runner
        self.timeout = timeout

    @property
    def target(self):
        return "{}:{}".format(self.host, self.port)

    def _run(self, args):
        argv = [self.adb_path] + list(args)
        try:
            proc = self.runner(argv, self.timeout, False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            return {"ok": False, "stdout": "", "stderr": str(exc), "error": str(exc)}
        return _result(proc)

    def _device(self, args):
        return self._run(["-s", self.target] + list(args))

    def connect(self):
        out = self._run(["connect", self.target])
        text = (str(out.get("stdout", "")) + " " + str(out.get("stderr", ""))).lower()
        if "connected to" not in text:
            out["ok"] = False
            out.setdefault("error", "adb connect did not report a connection")
        return out

    def disconnect(self):
        return self._run(["disconnect", self.target])

    def devices(self):
        out = self._run(["devices"])
        items = []
        for line in str(out.get("stdout", "")).splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2:
                items.append({"target": parts[0], "state": parts[1]})
        out["devices"] = items
        return out

    def state(self):
        for item in self.devices().get("devices", []):
            if item["target"] == self.target:
                return item["state"]
        return "not-connected"
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m unittest tests.test_adb -v`
Expected: PASS (13 tests).

- [ ] **Step 5: No commit** (user has not requested one).

---

### Task 2: ADB operations — shell, packages, install, screenshot, logs

**Files:**
- Modify: `tvunboxed/adb.py` (append methods to `AdbClient`)
- Test: `tests/test_adb.py` (append a second test class)

**Interfaces:**
- Consumes: Task 1's `AdbClient`, `guard_shell`, `WRITE_CONFIRM_TOKEN`.
- Produces: `.shell(command, confirm=None)`, `.pm_list()`, `.pm_toggle(package, enabled, confirm=None)`, `.install(local_path, confirm=None)`, `.uninstall(package, confirm=None)`, `.screenshot()`, `.logcat(lines=200)`. All return dicts with `ok` and, on failure, `error`.

- [ ] **Step 1: Write failing tests** (append to `tests/test_adb.py`, before the `if __name__` block):

```python
import os
import tempfile


class TestAdbOperations(unittest.TestCase):
    def test_shell_requires_token(self):
        runner = RecordingRunner(stdout="root\n")
        out = make_client(runner).shell("id")
        self.assertFalse(out["ok"])
        self.assertEqual(runner.calls, [])

    def test_shell_with_token_builds_argv(self):
        runner = RecordingRunner(stdout="8.0.0\n")
        out = make_client(runner).shell("getprop ro.build.version.release",
                                        adb.WRITE_CONFIRM_TOKEN)
        self.assertTrue(out["ok"])
        self.assertEqual(out["stdout"], "8.0.0\n")
        self.assertEqual(
            runner.calls[0][0],
            ["adb", "-s", "192.168.1.201:5555", "shell", "getprop", "ro.build.version.release"],
        )

    def test_shell_guard_applies_even_with_token(self):
        runner = RecordingRunner(stdout="uid=0(root)\n")
        out = make_client(runner).shell("su -c id", adb.WRITE_CONFIRM_TOKEN)
        self.assertFalse(out["ok"])
        self.assertEqual(runner.calls, [])

    def test_pm_list_parses_packages(self):
        runner = RecordingRunner(
            stdout="package:com.example.player\npackage:org.foo\n"
        )
        out = make_client(runner).pm_list()
        self.assertEqual(out["third_party"], ["com.example.player", "org.foo"])
        self.assertTrue(out["ok"])

    def test_pm_toggle_builds_disable_command(self):
        runner = RecordingRunner(stdout="Packages disabled for user 0: [com.example.player]\n")
        out = make_client(runner).pm_toggle("com.example.player", False,
                                            adb.WRITE_CONFIRM_TOKEN)
        self.assertTrue(out["ok"])
        self.assertEqual(
            runner.calls[0][0],
            ["adb", "-s", "192.168.1.201:5555", "shell", "pm", "disable-user",
             "--user", "0", "com.example.player"],
        )

    def test_pm_toggle_enables(self):
        runner = RecordingRunner(stdout="Packages enabled\n")
        make_client(runner).pm_toggle("com.example.player", True, adb.WRITE_CONFIRM_TOKEN)
        self.assertEqual(
            runner.calls[0][0][-2:], ["enable", "com.example.player"]
        )

    def test_pm_toggle_rejects_bad_package(self):
        runner = RecordingRunner()
        out = make_client(runner).pm_toggle("com.x; rm -rf /", True, adb.WRITE_CONFIRM_TOKEN)
        self.assertFalse(out["ok"])
        self.assertEqual(runner.calls, [])

    def test_install_rejects_non_apk(self):
        runner = RecordingRunner()
        with tempfile.NamedTemporaryFile(suffix=".txt") as handle:
            out = make_client(runner).install(handle.name, adb.WRITE_CONFIRM_TOKEN)
        self.assertFalse(out["ok"])
        self.assertEqual(runner.calls, [])

    def test_install_rejects_traversal(self):
        runner = RecordingRunner()
        out = make_client(runner).install("../secret.apk", adb.WRITE_CONFIRM_TOKEN)
        self.assertFalse(out["ok"])
        self.assertEqual(runner.calls, [])

    def test_install_builds_argv(self):
        runner = RecordingRunner(stdout="Success\n")
        with tempfile.NamedTemporaryFile(suffix=".apk") as handle:
            out = make_client(runner).install(handle.name, adb.WRITE_CONFIRM_TOKEN)
            self.assertTrue(out["ok"])
            self.assertEqual(runner.calls[0][0][-3:], ["install", "-r", handle.name])

    def test_uninstall_builds_argv(self):
        runner = RecordingRunner(stdout="Success\n")
        out = make_client(runner).uninstall("com.example.player", adb.WRITE_CONFIRM_TOKEN)
        self.assertTrue(out["ok"])
        self.assertEqual(runner.calls[0][0][-2:], ["uninstall", "com.example.player"])

    def test_screenshot_returns_png_bytes(self):
        png = b"\x89PNG\r\n\x1a\n" + b"payload"
        runner = RecordingRunner(stdout=png)
        out = make_client(runner).screenshot()
        self.assertTrue(out["ok"])
        self.assertEqual(out["png"], png)
        self.assertTrue(runner.calls[0][2])

    def test_screenshot_rejects_non_png(self):
        runner = RecordingRunner(stdout=b"not a png")
        out = make_client(runner).screenshot()
        self.assertFalse(out["ok"])

    def test_logcat_clamps_line_count(self):
        runner = RecordingRunner(stdout="line\n")
        make_client(runner).logcat(lines=99999)
        self.assertEqual(runner.calls[0][0][-1], "1000")
        make_client(RecordingRunner(stdout="line\n")).logcat(lines=0)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m unittest tests.test_adb -v`
Expected: FAIL with `AttributeError: 'AdbClient' object has no attribute 'shell'`.

- [ ] **Step 3: Append operations to `AdbClient` in `tvunboxed/adb.py`**

```python
    def shell(self, command, confirm=None):
        if confirm != WRITE_CONFIRM_TOKEN:
            return {"ok": False, "stdout": "", "stderr": "",
                    "error": "confirmation token required for ADB shell"}
        denial = guard_shell(command)
        if denial:
            return {"ok": False, "stdout": "", "stderr": "", "error": denial}
        try:
            pieces = shlex.split(command)
        except ValueError as exc:
            return {"ok": False, "stdout": "", "stderr": "",
                    "error": "bad command: {}".format(exc)}
        return self._device(["shell"] + pieces)

    def pm_list(self):
        third = self._device(["shell", "pm", "list", "packages", "-3"])
        system = self._device(["shell", "pm", "list", "packages", "-s"])

        def names(out):
            return sorted(
                line.split(":", 1)[1]
                for line in str(out.get("stdout", "")).splitlines()
                if line.startswith("package:")
            )

        return {
            "ok": bool(third.get("ok") and system.get("ok")),
            "third_party": names(third),
            "system": names(system),
            "error": third.get("error") or system.get("error"),
        }

    def pm_toggle(self, package, enabled, confirm=None):
        if confirm != WRITE_CONFIRM_TOKEN:
            return {"ok": False, "error": "confirmation token required"}
        if not _PACKAGE_RE.match(str(package)):
            return {"ok": False, "error": "invalid package name"}
        args = (["pm", "enable", str(package)] if enabled else
                ["pm", "disable-user", "--user", "0", str(package)])
        return self._device(["shell"] + args)

    def install(self, local_path, confirm=None):
        if confirm != WRITE_CONFIRM_TOKEN:
            return {"ok": False, "error": "confirmation token required"}
        if not isinstance(local_path, str) or ".." in local_path.replace("\\", "/").split("/"):
            return {"ok": False, "error": "invalid local path"}
        if not os.path.isfile(local_path):
            return {"ok": False, "error": "local APK not found"}
        if not local_path.lower().endswith(".apk"):
            return {"ok": False, "error": "only .apk files can be installed"}
        return self._device(["install", "-r", local_path])

    def uninstall(self, package, confirm=None):
        if confirm != WRITE_CONFIRM_TOKEN:
            return {"ok": False, "error": "confirmation token required"}
        if not _PACKAGE_RE.match(str(package)):
            return {"ok": False, "error": "invalid package name"}
        return self._device(["uninstall", str(package)])

    def screenshot(self):
        argv = [self.adb_path, "-s", self.target, "exec-out", "screencap", "-p"]
        try:
            proc = self.binary_runner(argv, self.timeout, True)
        except (OSError, subprocess.TimeoutExpired) as exc:
            return {"ok": False, "error": str(exc), "png": b""}
        data = proc.stdout or b""
        if proc.returncode != 0 or not data.startswith(b"\x89PNG"):
            detail = proc.stderr or b"screenshot failed"
            message = detail.decode("utf-8", "replace") if isinstance(detail, bytes) else str(detail)
            return {"ok": False, "error": message, "png": b""}
        return {"ok": True, "png": data}

    def logcat(self, lines=200):
        try:
            count = max(1, min(1000, int(lines)))
        except (TypeError, ValueError):
            count = 200
        return self._device(["logcat", "-d", "-t", str(count)])
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m unittest tests.test_adb -v`
Expected: PASS (27 tests).

- [ ] **Step 5: No commit.**

---

### Task 3: HTTP endpoints and ADB port configuration

**Files:**
- Modify: `tvunboxed/server.py` (imports, GET/POST route maps, handlers, `run()` signature)
- Modify: `tvunboxed/__main__.py` (`--adb-port`, pass to `run`, `adb` CLI dispatch)
- Test: append `TestAdbEndpointHelpers` to `tests/test_adb.py`

**Interfaces:**
- Consumes: all of Task 1 + Task 2; `server.RemoteHandler`, `server.run`.
- Produces: `server.adb_require_token(body)` (raises `ValueError` unless `confirm` matches), `RemoteHandler._adb()`, `httpd.adb_port`, CLI flag `--adb-port`, `python -m tvunboxed adb ...` dispatch to `adb_cli.main`.

- [ ] **Step 1: Write failing tests** (append to `tests/test_adb.py`):

```python
class TestAdbEndpointHelpers(unittest.TestCase):
    def test_token_helper_accepts_exact_token(self):
        from tvunboxed.server import adb_require_token
        adb_require_token({"confirm": adb.WRITE_CONFIRM_TOKEN})

    def test_token_helper_rejects_bad_or_missing_token(self):
        from tvunboxed.server import adb_require_token
        for body in ({}, {"confirm": "no"}, {"confirm": adb.WRITE_CONFIRM_TOKEN.lower()}):
            with self.subTest(body=body):
                with self.assertRaises(ValueError):
                    adb_require_token(body)

    def test_cli_dispatch_exists(self):
        import ast
        path = os.path.join(os.path.dirname(adb.__file__), "__main__.py")
        with open(path, encoding="utf-8") as handle:
            source = handle.read()
        self.assertIn('sys.argv[1] == "adb"', source)
        self.assertIn("--adb-port", source)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m unittest tests.test_adb -v`
Expected: FAIL with `ImportError: cannot import name 'adb_require_token'`.

- [ ] **Step 3: Add imports, helper, route maps, and handlers to `tvunboxed/server.py`**

Add to the import block (after `from . import protocol`):

```python
from . import adb
```

Add the helper just below `_LOG_LOCK`:

```python
def adb_require_token(body):
    if str(body.get("confirm", "")) != adb.WRITE_CONFIRM_TOKEN:
        raise ValueError("write operations require confirm: " + adb.WRITE_CONFIRM_TOKEN)
```

In `_do_GET_route`, add before `if route == "/api/dav/list":`:

```python
        if route == "/api/adb/status":
            return self._cmd_adb_status()
        if route == "/api/adb/apps":
            return self._cmd_adb_apps()
        if route == "/api/adb/screenshot":
            return self._cmd_adb_screenshot()
        if route.startswith("/api/adb/logcat"):
            return self._cmd_adb_logcat()
```

In the POST `handler = { ... }` map, add these entries before the closing brace:

```python
            "/api/adb/connect": self._cmd_adb_connect,
            "/api/adb/disconnect": self._cmd_adb_disconnect,
            "/api/adb/shell": self._cmd_adb_shell,
            "/api/adb/toggle": self._cmd_adb_toggle,
            "/api/adb/install": self._cmd_adb_install,
            "/api/adb/uninstall": self._cmd_adb_uninstall,
```

Add this block just before the `# care toolkit` section:

```python
    # adb transport -------------------------------------------------------------
    def _adb(self):
        return adb.AdbClient(self.server.session.host, self.server.adb_port)

    def _cmd_adb_status(self):
        try:
            client = self._adb()
        except adb.AdbNotFound as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        return self._send_json({
            "ok": True,
            "available": True,
            "adb_path": client.adb_path,
            "target": client.target,
            "state": client.state(),
            "write_confirm_token": adb.WRITE_CONFIRM_TOKEN,
        })

    def _cmd_adb_apps(self):
        try:
            result = self._adb().pm_list()
        except adb.AdbNotFound as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        if not result.get("ok"):
            return self._send_json({"ok": False, "error": result.get("error") or "adb unavailable"}, 502)
        return self._send_json(result)

    def _cmd_adb_screenshot(self):
        try:
            result = self._adb().screenshot()
        except adb.AdbNotFound as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        if not result.get("ok"):
            return self._send_json({"ok": False, "error": result.get("error")}, 502)
        self._response_sent = True
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(result["png"])))
        self.end_headers()
        self.wfile.write(result["png"])

    def _cmd_adb_logcat(self):
        query = {}
        if "?" in self.path:
            for piece in self.path.split("?", 1)[1].split("&"):
                if "=" in piece:
                    key, value = piece.split("=", 1)
                    query[key] = value
        try:
            result = self._adb().logcat(lines=query.get("lines", 200))
        except adb.AdbNotFound as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        if not result.get("ok"):
            return self._send_json({"ok": False, "error": result.get("error") or "adb unavailable"}, 502)
        self._send_json({"ok": True, "text": result.get("stdout", "")})

    def _cmd_adb_connect(self, body):
        base = self._adb()
        host = str(body.get("host") or base.host)
        port = int(body.get("port") or self.server.adb_port)
        client = adb.AdbClient(host, port, adb_path=base.adb_path)
        out = client.connect()
        if not out.get("ok"):
            raise ConnectionError(out.get("error") or "adb connect failed")
        log_event(self.server, "adb_connect", ok=True, target=client.target)
        return self._send_json({"ok": True, "target": client.target, "state": client.state()})

    def _cmd_adb_disconnect(self, body):
        client = self._adb()
        client.disconnect()
        log_event(self.server, "adb_disconnect", target=client.target)
        return self._send_json({"ok": True})

    def _cmd_adb_shell(self, body):
        adb_require_token(body)
        command = str(body.get("command", ""))
        denial = adb.guard_shell(command)
        if denial:
            raise ValueError(denial)
        client = self._adb()
        out = client.shell(command, confirm=str(body.get("confirm", "")))
        if not out.get("ok"):
            raise ConnectionError(out.get("error") or "adb shell failed")
        log_event(self.server, "adb_shell", ok=True, target=client.target, command=command)
        return self._send_json(out)

    def _cmd_adb_toggle(self, body):
        adb_require_token(body)
        client = self._adb()
        out = client.pm_toggle(body.get("package"), bool(body.get("enabled")),
                               confirm=str(body.get("confirm", "")))
        if not out.get("ok"):
            raise ConnectionError(out.get("error") or "adb toggle failed")
        log_event(self.server, "adb_toggle", ok=True,
                  package=body.get("package"), enabled=bool(body.get("enabled")))
        return self._send_json(out)

    def _cmd_adb_install(self, body):
        adb_require_token(body)
        client = self._adb()
        out = client.install(body.get("path"), confirm=str(body.get("confirm", "")))
        if not out.get("ok"):
            raise ConnectionError(out.get("error") or "adb install failed")
        log_event(self.server, "adb_install", ok=True, path=body.get("path"))
        return self._send_json(out)

    def _cmd_adb_uninstall(self, body):
        adb_require_token(body)
        client = self._adb()
        out = client.uninstall(body.get("package"), confirm=str(body.get("confirm", "")))
        if not out.get("ok"):
            raise ConnectionError(out.get("error") or "adb uninstall failed")
        log_event(self.server, "adb_uninstall", ok=True, package=body.get("package"))
        return self._send_json(out)
```

Note: `adb.AdbNotFound` subclasses `ConnectionError`, so the existing `except ConnectionError` in `_do_POST_route` already turns a missing binary into 502 on POST. GET handlers catch it explicitly.

Modify `run()` in `server.py`:

```python
def run(session_host, tv_port=protocol.CONTROL_PORT_DEFAULT, bind="127.0.0.1",
        http_port=8080, share_dir=None, tv_webdav_port=8000, log_path="tvunboxed.log",
        adb_port=5555):
```

Add inside `run()` after `httpd.log_path = log_path`:

```python
    httpd.adb_port = adb_port
```

- [ ] **Step 4: Wire `__main__.py`**

Add after the existing `care` dispatch:

```python
    if len(sys.argv) > 1 and sys.argv[1] == "adb":
        from .adb_cli import main as adb_main
        return adb_main(sys.argv[2:])
```

Add before `--tv-webdav-port`:

```python
    parser.add_argument("--adb-port", type=int,
                        default=int(os.environ.get("TVUNBOXED_ADBPORT", 5555)),
                        help="ADB network port on the TV (default: %(default)s)")
```

Change the final `run(...)` call to:

```python
    run(tv_ip, tv_port, bind, args.http_port, share, args.tv_webdav_port,
        adb_port=args.adb_port)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `python3 -m unittest tests.test_adb -v`
Expected: PASS (27 tests).

- [ ] **Step 6: No commit.**

---

### Task 4: CLI — `python -m tvunboxed adb ...`

**Files:**
- Create: `tvunboxed/adb_cli.py`
- Test: create `tests/test_adb_cli.py`

**Interfaces:**
- Consumes: `adb.AdbClient`, `adb.WRITE_CONFIRM_TOKEN`.
- Produces: `adb_cli.main(argv=None) -> int`.

- [ ] **Step 1: Write failing tests** in `tests/test_adb_cli.py`:

```python
import unittest
from unittest.mock import patch

from tvunboxed import adb, adb_cli


class TestAdbCli(unittest.TestCase):
    def test_shell_requires_yes_before_any_client_is_built(self):
        with patch("tvunboxed.adb_cli.AdbClient") as client:
            code = adb_cli.main(["--tv", "192.168.1.201", "--adb-path", "adb",
                                 "shell", "id"])
            self.assertEqual(code, 2)
            client.assert_not_called()

    def test_status_builds_client_and_returns_zero(self):
        with patch("tvunboxed.adb_cli.AdbClient") as client:
            client.return_value.state.return_value = "device"
            code = adb_cli.main(["--tv", "192.168.1.201", "--adb-path", "adb",
                                 "status"])
            self.assertEqual(code, 0)
            client.assert_called_once_with("192.168.1.201", 5555, adb_path="adb")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m unittest tests.test_adb_cli -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'tvunboxed.adb_cli'`.

- [ ] **Step 3: Implement `tvunboxed/adb_cli.py`**

```python
"""CLI for the optional ADB transport: python3 -m tvunboxed adb --tv IP status"""

import argparse
import sys

from . import adb


def main(argv=None):
    parser = argparse.ArgumentParser(prog="tvunboxed adb", description=__doc__)
    parser.add_argument("--tv", required=True, help="TV IP address")
    parser.add_argument("--adb-port", type=int, default=5555)
    parser.add_argument("--adb-path", default=None, help="path to the adb binary")
    parser.add_argument("--yes", default=None,
                        help="required literal '{}' for write commands".format(adb.WRITE_CONFIRM_TOKEN))
    parser.add_argument("command", choices=["status", "connect", "disconnect", "shell",
                                            "apps", "toggle", "install", "uninstall",
                                            "screenshot", "logcat"])
    parser.add_argument("args", nargs="*")
    parsed = parser.parse_args(argv)

    destructive = parsed.command in ("shell", "toggle", "install", "uninstall")
    if destructive and parsed.yes != adb.WRITE_CONFIRM_TOKEN:
        print("refusing: pass --yes {} to confirm".format(adb.WRITE_CONFIRM_TOKEN),
              file=sys.stderr)
        return 2

    try:
        client = adb.AdbClient(parsed.tv, parsed.adb_port, adb_path=parsed.adb_path)
    except adb.AdbNotFound as exc:
        print("error: {}".format(exc), file=sys.stderr)
        return 1

    command, args = parsed.command, parsed.args
    if command == "status":
        print("target: {}".format(client.target))
        print("state:  {}".format(client.state()))
    elif command == "connect":
        result = client.connect()
    elif command == "disconnect":
        result = client.disconnect()
        print("disconnected" if result.get("ok") else result.get("error"))
    elif command == "shell":
        result = client.shell(" ".join(args), confirm=parsed.yes)
        if result.get("ok"):
            print(result.get("stdout", ""), end="")
    elif command == "apps":
        result = client.pm_list()
        print("third-party:")
        for package in result.get("third_party", []):
            print("  " + package)
        print("system:")
        for package in result.get("system", []):
            print("  " + package)
    elif command == "toggle":
        if len(args) != 2 or args[1] not in ("on", "off"):
            print("usage: toggle PACKAGE on|off", file=sys.stderr)
            return 2
        result = client.pm_toggle(args[0], args[1] == "on", confirm=parsed.yes)
    elif command == "install":
        if not args:
            print("usage: install LOCAL.apk", file=sys.stderr)
            return 2
        result = client.install(args[0], confirm=parsed.yes)
    elif command == "uninstall":
        if not args:
            print("usage: uninstall PACKAGE", file=sys.stderr)
            return 2
        result = client.uninstall(args[0], confirm=parsed.yes)
    elif command == "screenshot":
        if not args:
            print("usage: screenshot OUT.png", file=sys.stderr)
            return 2
        result = client.screenshot()
        if result.get("ok"):
            with open(args[0], "wb") as handle:
                handle.write(result["png"])
    elif command == "logcat":
        lines = int(args[0]) if args else 200
        result = client.logcat(lines=lines)
        if result.get("ok"):
            print(result.get("stdout", ""), end="")

    if command in ("connect", "shell", "toggle", "install", "uninstall",
                   "screenshot", "logcat"):
        if not result.get("ok"):
            print("error: {}".format(result.get("error")), file=sys.stderr)
            return 1
        if command == "connect":
            print("connected: {}".format(client.target))
        if command in ("toggle", "install", "uninstall", "screenshot", "logcat"):
            print("ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m unittest tests.test_adb_cli -v`
Expected: PASS (2 tests).

- [ ] **Step 5: No commit.**

---

### Task 5: "ADB" tab in the web UI

**Files:**
- Modify: `web/index.html`

**Interfaces:**
- Consumes: the endpoints from Task 3.
- Produces: `#tab-adb`, a `data-tab="adb"` nav item, and JS `loadAdb()`. No new JS dependencies.

- [ ] **Step 1: Change the navbar to seven columns and add the tab button**

Replace the CSS line:

```css
.navbar{grid-template-columns:repeat(6,minmax(0,1fr))}
```

with:

```css
.navbar{grid-template-columns:repeat(7,minmax(0,1fr))}
```

Add this nav item after the `care` button (line with `data-tab="care"`):

```html
  <button class="nav-item" data-tab="adb" role="tab"><svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="0"/><path d="M9 9l6 6M15 9l-6 6"/></svg>ADB</button>
```

- [ ] **Step 2: Add the ADB tab section**

Insert just before `<!-- ════════ bottom-sheet remote tray (all tabs) ════════ -->`:

```html
  <section class="tab" id="tab-adb" role="tabpanel">
    <div class="card">
      <div class="card-title"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>One-time setup (needs the TV remote)</div>
      <p class="cap">1 · Settings → Device Preferences → About → tap Build 7×.
        2 · Developer options → Network debugging (or ADB/USB debugging).
        3 · From here press Connect, then accept the RSA prompt on the TV with the remote (tick “Always allow”).
        4 · After that this phone is authorized — no remote needed. Network debugging may reset after a reboot; turn it off when you are done. Never port-forward port 5555.</p>
    </div>

    <div class="card">
      <div class="card-title"><svg viewBox="0 0 24 24"><path d="M4 14a8 8 0 1 1 16 0"/><path d="M12 14l4-4"/></svg>Connection</div>
      <p class="status-line" id="adbState">checking…</p>
      <div class="btn-row"><button class="btn btn-blue" id="adbConnect" style="flex:1">Connect</button><button class="btn" id="adbDisconnect" style="flex:1">Disconnect</button></div>
    </div>

    <div class="card">
      <div class="card-title"><svg viewBox="0 0 24 24"><rect x="3" y="7" width="18" height="10" rx="0"/><path d="M7 12h.01M11 12h.01M15 12h.01"/></svg>Shell <span class="hint">state-changing commands are guarded and logged</span></div>
      <div class="field" style="margin-top:10px"><input id="adbCommand" placeholder="getprop ro.build.version.release" spellcheck="false" autocomplete="off"><button class="btn btn-amber" id="adbRun">Run</button></div>
      <pre id="adbOutput" class="status-line" style="white-space:pre-wrap;max-height:34vh;overflow:auto"></pre>
    </div>

    <div class="card">
      <div class="card-title"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>Packages</div>
      <div class="btn-row"><button class="btn" id="adbAppsRefresh" style="flex:1">Refresh list</button></div>
      <div class="list" id="adbApps" style="margin-top:10px"><div class="empty">Refresh to load packages.</div></div>
    </div>

    <div class="card">
      <div class="card-title"><svg viewBox="0 0 24 24"><path d="M12 3v12M7 10l5 5 5-5M4 21h16"/></svg>Install APK / Screenshot / Logs</div>
      <div class="field" style="margin-top:10px"><input id="adbApkPath" placeholder="/absolute/path/app.apk on this device" spellcheck="false"><button class="btn btn-blue" id="adbInstall">Install</button></div>
      <div class="btn-row" style="margin-top:10px"><button class="btn" id="adbShot" style="flex:1">Screenshot</button><button class="btn" id="adbLogs" style="flex:1">Logcat tail</button></div>
      <img id="adbShotImg" alt="TV screenshot" style="display:none;width:100%;margin-top:10px;border:2px solid var(--bord)">
      <pre id="adbLogText" class="status-line" style="white-space:pre-wrap;max-height:34vh;overflow:auto"></pre>
      <p class="status-line" id="adbResult"></p>
      <p class="cap">Write token: <code id="adbToken">I-OWN-THIS-TV</code> — type it below to authorize writes.</p>
      <div class="field" style="margin-top:8px"><input id="adbConfirm" placeholder="write token" autocomplete="off" spellcheck="false"></div>
    </div>
  </section>
```

- [ ] **Step 3: Add JS — a JSON GET helper, `loadAdb()`, and button wiring**

Insert before the `/* ---------- navigation ---------- */` section:

```javascript
async function getJson(path) {
  const res = await fetch(path);
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.ok === false) throw new Error(data.error || ('HTTP ' + res.status));
  return data;
}
const adbConfirm = () => q('#adbConfirm').value.trim();
const adbMsg = (el, text) => { el.textContent = text; };

async function loadAdb() {
  const el = q('#adbState');
  try {
    const s = await getJson('/api/adb/status');
    adbMsg(el, `target ${s.target} · state: ${s.state}`);
    if (s.write_confirm_token) q('#adbToken').textContent = s.write_confirm_token;
  } catch (e) {
    adbMsg(el, 'ADB unavailable: ' + e.message + ' — install android-tools on this device.');
  }
}

q('#adbConnect').addEventListener('click', async () => {
  try { const r = await call('/api/adb/connect', {}); adbMsg(q('#adbState'), `connected: ${r.target}`); toast('Connected — accept the prompt on the TV if asked'); }
  catch (e) { adbMsg(q('#adbState'), 'Connect failed: ' + e.message); }
});
q('#adbDisconnect').addEventListener('click', () => post('/api/adb/disconnect', {}).then(() => loadAdb()));

q('#adbRun').addEventListener('click', async () => {
  const command = q('#adbCommand').value.trim();
  if (!command) return;
  try {
    const r = await call('/api/adb/shell', {command, confirm: adbConfirm()});
    q('#adbOutput').textContent = (r.stdout || '') + (r.stderr || '');
  } catch (e) { q('#adbOutput').textContent = 'Error: ' + e.message; }
});

q('#adbAppsRefresh').addEventListener('click', async () => {
  try {
    const r = await getJson('/api/adb/apps');
    const render = (title, list) => `<div class="cap">${title}</div>` +
      list.map(p => `<div class="row"><code>${p}</code></div>`).join('');
    q('#adbApps').innerHTML = render('third-party', r.third_party || []) + render('system', r.system || []);
  } catch (e) { q('#adbApps').innerHTML = '<div class="empty">Error: ' + e.message + '</div>'; }
});

q('#adbInstall').addEventListener('click', () => post('/api/adb/install', {path: q('#adbApkPath').value.trim(), confirm: adbConfirm()}).then(r => adbMsg(q('#adbResult'), 'Installed on TV: ' + (r.stdout || 'ok'))));

q('#adbShot').addEventListener('click', async () => {
  try {
    const res = await fetch('/api/adb/screenshot');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const blob = await res.blob();
    const img = q('#adbShotImg');
    img.src = URL.createObjectURL(blob);
    img.style.display = 'block';
  } catch (e) { adbMsg(q('#adbResult'), 'Screenshot failed: ' + e.message); }
});

q('#adbLogs').addEventListener('click', async () => {
  try { const r = await getJson('/api/adb/logcat?lines=200'); q('#adbLogText').textContent = r.text || ''; }
  catch (e) { q('#adbLogText').textContent = 'Error: ' + e.message; }
});
```

Update the navigation handler (the `qa('.nav-item')...` block) to also call `loadAdb()` for the ADB tab:

```javascript
  if (id === 'adb') loadAdb();
```

- [ ] **Step 4: Verify the UI**

Run: `python3 -m tvunboxed --tv 192.168.1.201 --lan`
Open `http://<phone-ip>:8080`, switch theme, switch to the ADB tab, confirm it renders, the setup card is visible, and the status line reports "ADB unavailable" when `adb` is not installed on the host.

- [ ] **Step 5: No commit.**

---

### Task 6: README documentation and full verification

**Files:**
- Modify: `README.md`
- Test: full suite + live read-only checks

- [ ] **Step 1: Document ADB in README**

Add a section after "TV Care (new tab)":

```markdown
## Optional ADB transport (phone, same Wi-Fi)

TVunboxed can drive the TV through the official Android Debug Bridge — no root, no authorization bypass, nothing installed on the TV. One time only, with the TV remote: Settings → Device Preferences → About → tap Build 7×; Developer options → Network debugging (or ADB/USB debugging); then press Connect in this app's ADB tab and accept the RSA prompt on the TV (tick "Always allow"). After that the phone is authorized and control is phone-only. Network debugging often resets after a reboot.

Requires `adb` on this device (Termux: `pkg install android-tools`). Run `python3 -m tvunboxed --adb-port 5555 ...` (default 5555). The ADB tab offers guarded shell, package enable/disable, APK install, screenshot and logcat; every write needs the exact token `I-OWN-THIS-TV` and is logged. Dangerous paths (`/system`, `/data`, `/adb_keys`, boot/recovery) and commands (`su`, `flash*`, `dd`, `reboot bootloader`) are blocked. Keep this on a trusted LAN: anyone on the Wi-Fi can control a debugging-enabled TV. Turn ADB off when done and never port-forward 5555/8000/8121.
```

Also add `python3 -m tvunboxed adb --tv IP status` to the "Tests"/usage area as a CLI example.

- [ ] **Step 2: Run the full test suite**

Run: `python3 -m unittest discover tests -v`
Expected: all tests PASS (existing suite + `test_adb.py` + `test_adb_cli.py`).

- [ ] **Step 3: Live read-only checks (after the one-time TV setup)**

Run:
```sh
python3 -m tvunboxed adb --tv 192.168.1.201 status
python3 -m tvunboxed adb --tv 192.168.1.201 connect
python3 -m tvunboxed adb --tv 192.168.1.201 shell "getprop ro.build.version.release" --yes I-OWN-THIS-TV
```
Expected: state `device`; connect prints `connected`; shell prints the Android version. No destructive command is run.

- [ ] **Step 4: Confirm scope**

Run: `git status`
Expected: only intended files (`tvunboxed/adb.py`, `tvunboxed/adb_cli.py`, modified `tvunboxed/server.py`, `tvunboxed/__main__.py`, `web/index.html`, `README.md`, `tests/test_adb.py`, `tests/test_adb_cli.py`, plus the two spec/plan docs). No commit.

---

## Self-review

- **Spec coverage:** discovery/guard/connection (T1), operations (T2), endpoints + config + CLI dispatch (T3), CLI (T4), UI (T5), README + verification (T6). One-time setup, boundaries, token rule, logging, and no-port-forward appear in Global Constraints and the UI/README.
- **Placeholder scan:** none — every implementation step includes complete code or exact edits.
- **Type consistency:** `runner(argv, timeout, binary=False)` is used identically in Task 1 and Task 2; `AdbClient` constructor kwargs match in server/CLI/tests; token constant is `adb.WRITE_CONFIRM_TOKEN` throughout; endpoint names match between Task 3 and Task 5.
