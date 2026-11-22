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


if __name__ == "__main__":
    unittest.main()
