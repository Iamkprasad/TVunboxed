"""Tests for the guarded TV Care toolkit (no TV required)."""

import os
import tempfile
import types
import unittest

from tvunboxed import tvcare


class FakeDav:
    def __init__(self):
        self.deleted = []
        self.copied = []
        self.puts = []

    def copy(self, src, dst):
        self.copied.append((src, dst))

    def mkdir(self, path):
        return 201

    def list(self, path):
        entries = [types.SimpleNamespace(path=dst, is_dir=False, size=1)
                   for _src, dst in self.copied]
        return types.SimpleNamespace(entries=entries)

    def delete(self, path):
        self.deleted.append(path)

    def put(self, path, data, content_type=None):
        self.puts.append((path, data, content_type))


class ClassifyTests(unittest.TestCase):
    def test_classify_apps(self):
        apps = [
            {"package": "com.cvte.tv.launcher.fall", "name": "Launcher", "versionName": "1"},
            {"package": "com.example.sideload", "name": "My App", "versionName": "2"},
        ]
        out = tvcare.classify_apps(apps)
        self.assertEqual(out["counts"], {"system": 1, "user": 1, "unknown": 0})
        self.assertEqual(out["apps"][0]["kind"], "system")
        self.assertEqual(out["apps"][1]["kind"], "user")


class PathTests(unittest.TestCase):
    def test_cleanup_reason_allows_user_paths(self):
        for p in ("/sdcard/Pictures/Screenshots/a.png",
                  "/sdcard/ecache/app_thumb.png",
                  "/sdcard/download/App.apk",
                  "/sdcard/Android/data/com.foo/cache/123",
                  "/sdcard/debug/log/x.txt"):
            self.assertIsNotNone(tvcare.cleanup_reason(p), p)

    def test_cleanup_reason_rejects_system_and_auth_paths(self):
        for p in ("/system/app/x.apk", "/data/misc/adb/adb_keys", "/adb_keys",
                  "/sdcard/Upload/x.apk", "../x", "/sdcard/../../system/x", ""):
            self.assertIsNone(tvcare.cleanup_reason(p), p)

    def test_token_is_stable_when_compared(self):
        self.assertEqual(tvcare.make_confirm_token(["a", "b"]),
                         tvcare.make_confirm_token(["b", "a"]))
        self.assertNotEqual(tvcare.make_confirm_token(["a", "b"]),
                            tvcare.make_confirm_token(["a", "c"]))


class ReclaimableTests(unittest.TestCase):
    def test_duplicate_and_pattern_detection(self):
        files = [
            {"path": "/sdcard/download/App.apk", "size": 100},
            {"path": "/sdcard/ecache/App.apk", "size": 100},
            {"path": "/sdcard/Pictures/Screenshots/s.png", "size": 50},
            {"path": "/system/app/x.apk", "size": 999},
        ]
        out = tvcare.find_reclaimable(files)
        paths = [r["path"] for r in out["items"]]
        self.assertIn("/sdcard/ecache/App.apk", paths)
        self.assertIn("/sdcard/Pictures/Screenshots/s.png", paths)
        self.assertNotIn("/system/app/x.apk", paths)
        self.assertEqual(out["bytes"], 150)


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="tvcare-test-")

    def test_wrong_token_aborts_before_any_delete(self):
        dav = FakeDav()
        paths = ["/sdcard/Pictures/Screenshots/a.png"]
        with self.assertRaises(ValueError):
            tvcare.cleanup(dav, paths, "wrong", "right")
        self.assertEqual(dav.deleted, [])

    def test_bad_path_aborts_before_any_delete(self):
        dav = FakeDav()
        paths = ["/sdcard/Pictures/Screenshots/a.png", "/system/app/x.apk"]
        token = tvcare.make_confirm_token(paths)
        with self.assertRaises(ValueError):
            tvcare.cleanup(dav, paths, token, token)
        self.assertEqual(dav.deleted, [])

    def test_cleanup_copies_to_backup_then_deletes(self):
        dav = FakeDav()
        paths = ["/sdcard/Pictures/Screenshots/a.png"]
        token = tvcare.make_confirm_token(paths)
        results = tvcare.cleanup(dav, paths, token, token)
        self.assertEqual(results[0]["status"], "deleted")
        self.assertTrue(results[0]["backup"].startswith(tvcare.BACKUP_ROOT))
        self.assertEqual(dav.copied, [(paths[0], results[0]["backup"])])
        self.assertEqual(dav.deleted, paths)


class PurgeTests(unittest.TestCase):
    def test_purge_rejects_non_backup_paths(self):
        dav = FakeDav()
        paths = ["/sdcard/Pictures/Screenshots/a.png"]
        token = tvcare.make_confirm_token(paths)
        with self.assertRaises(ValueError):
            tvcare.purge_backups(dav, paths, token, token)
        self.assertEqual(dav.deleted, [])

    def test_purge_deletes_backups(self):
        dav = FakeDav()
        paths = [tvcare.BACKUP_ROOT + "a.png"]
        token = tvcare.make_confirm_token(paths)
        results = tvcare.purge_backups(dav, paths, token, token)
        self.assertEqual(results[0]["status"], "purged")
        self.assertEqual(dav.deleted, paths)


class StageTests(unittest.TestCase):
    def test_stage_rejects_non_apk(self):
        with self.assertRaises(ValueError):
            tvcare.stage_apk(FakeDav(), "notes.txt")

    def test_stage_uploads_and_builds_openfile(self):
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as f:
            f.write(b"APKDATA")
            name = f.name
        try:
            dav = FakeDav()
            result = tvcare.stage_apk(dav, name)
            self.assertEqual(result["remote"], "/sdcard/Upload/" + os.path.basename(name))
            self.assertIn("Openfile", result["openfile"])
            self.assertEqual(dav.puts[0][1], b"APKDATA")
        finally:
            os.unlink(name)


if __name__ == "__main__":
    unittest.main()
