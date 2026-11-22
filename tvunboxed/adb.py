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
