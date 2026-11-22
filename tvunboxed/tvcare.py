"""Guarded TV Care: read-only audits and reversible user-storage actions.

Scope is deliberately limited to /sdcard on the TV's WebDAV service. Nothing
here writes to /system, /data, authentication files, or boot partitions, and
nothing attempts to bypass ADB authorization.
"""

import hashlib
import json
import os
import re
import secrets
import urllib.request

from .share import open_file_message
from .tvapps import fetch_app_list

APK_MIME = "application/vnd.android.package-archive"
BACKUP_ROOT = "/sdcard/backups/care/"

SYSTEM_PACKAGES = frozenset({
    "com.android.browser",
    "com.android.documentsui",
    "com.android.tv.settings",
    "com.android.vending",
    "com.cvte.miracast",
    "com.cvte.settings",
    "com.cvte.tv.appstore",
    "com.cvte.tv.launcher.fall",
    "com.cvte.tv.media",
    "com.cvte.tv.pictorial",
    "com.cvte.tv.setting",
    "com.cvte.tv.steward",
    "com.ecloud.eairplay",
    "cn.zeasn.cvte.msd358.vstoresubclient",
})

_CLEANUP_PATTERNS = (
    (re.compile(r"^/sdcard/Pictures/Screenshots/[^/]+$"), "screenshot"),
    (re.compile(r"^/sdcard/ecache/[^/]+_thumb\.png$"), "cache thumbnail"),
    (re.compile(r"^/sdcard/(?:download|Download|ecache)/[^/]+\.apk$"), "staged APK"),
    (re.compile(r"^/sdcard/[^/]+\.apk$"), "staged APK"),
    (re.compile(r"^/sdcard/Android/data/[^/]+/cache/[^/]+$"), "app cache"),
    (re.compile(r"^/sdcard/debug/log/[^/]+\.txt$"), "debug log"),
)

STORAGE_NOTE = (
    "Only user files under /sdcard can be reclaimed safely here. "
    "The roughly 4 GB device storage is mostly system/vendor partitions and "
    "installed packages; removing those needs authorized system access, which "
    "this toolkit deliberately does not attempt. Cleanup first copies files to "
    "/sdcard/backups/care/ on the TV; space is freed only after you purge those backups. "
    "Keep port 8000 on a trusted LAN."
)


def classify_apps(apps, system_packages=SYSTEM_PACKAGES):
    classified = []
    counts = {"system": 0, "user": 0, "unknown": 0}
    for app in apps:
        package = str(app.get("package", "")).strip()
        if not package:
            kind = "unknown"
        elif package in system_packages:
            kind = "system"
        else:
            kind = "user"
        counts[kind] += 1
        classified.append({
            "package": package,
            "name": app.get("name", ""),
            "versionName": app.get("versionName", ""),
            "kind": kind,
            "source": app.get("source", "control"),
        })
    classified.sort(key=lambda a: (a["kind"], a["name"].lower()))
    return {"apps": classified, "counts": counts}


def fetch_http_app_names(host, port=8000, timeout=5):
    """Fetch the TV's curated installed-app list over its HTTP service."""
    url = "http://{}:{}/apkmanager/getinstallapks".format(host, int(port))
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8", "replace"))


def audit_apps(session, webdav_port=8000):
    """Merge the control-port app list with the HTTP list, classify packages."""
    merged = {}
    try:
        for app in fetch_app_list(session.host, session.port):
            if app.get("package"):
                merged[app["package"]] = dict(app, source="control")
    except Exception:
        pass
    try:
        for app in fetch_http_app_names(session.host, webdav_port):
            package = app.get("package_name", "")
            if not package:
                continue
            record = merged.setdefault(package, {
                "package": package,
                "name": app.get("app_name", ""),
                "versionName": app.get("versionName", ""),
                "source": "http",
            })
            if not record.get("name"):
                record["name"] = app.get("app_name", "")
            if not record.get("versionName"):
                record["versionName"] = app.get("versionName", "")
    except Exception:
        pass
    return classify_apps(list(merged.values()))


def walk_user_storage(dav, root="/sdcard/", max_depth=4):
    """Walk only /sdcard, depth-capped, tolerating per-directory failures."""
    if not isinstance(root, str) or not root.startswith("/sdcard/"):
        raise ValueError("storage root must be under /sdcard")
    files = []
    errors = []
    folders = set()
    truncated = [False]

    def recurse(path, depth):
        if depth > int(max_depth):
            truncated[0] = True
            return
        try:
            listing = dav.list(path)
        except Exception as exc:
            errors.append({"path": path, "error": str(exc)})
            return
        for entry in listing.entries:
            if entry.is_dir:
                folders.add(entry.path)
                recurse(entry.path, depth + 1)
            elif entry.size:
                files.append({
                    "path": entry.path,
                    "size": int(entry.size or 0),
                    "modified": entry.modified,
                })

    recurse(root, 0)
    return {
        "files": files,
        "errors": errors,
        "folders": sorted(folders),
        "truncated": truncated[0],
    }


def cleanup_reason(path):
    """Return the reclaim category if a path is allowlisted, else None."""
    if not isinstance(path, str) or not path or "\r" in path or "\n" in path:
        return None
    if not path.startswith("/sdcard/"):
        return None
    if ".." in path.split("/"):
        return None
    for pattern, reason in _CLEANUP_PATTERNS:
        if pattern.match(path):
            return reason
    return None


def find_reclaimable(files):
    """Flag safe-to-review files: pattern matches plus duplicate APKs."""
    candidates = {}
    for record in files:
        path = record.get("path", "")
        if cleanup_reason(path):
            candidates[path] = record

    duplicates = {}
    for record in candidates.values():
        path = record["path"]
        if not path.lower().endswith(".apk"):
            continue
        key = (os.path.basename(path), int(record.get("size", 0)))
        duplicates.setdefault(key, []).append(path)

    duplicate_paths = set()
    duplicate_keepers = set()
    for paths in duplicates.values():
        if len(paths) > 1:
            ordered = sorted(paths)
            duplicate_keepers.add(ordered[0])
            duplicate_paths.update(ordered[1:])

    items = []
    seen = set()
    for path, record in candidates.items():
        if path in duplicate_keepers:
            continue
        reason = cleanup_reason(path)
        if path in duplicate_paths:
            reason = "duplicate APK"
        if reason not in ("duplicate APK",) and cleanup_reason(path) is None:
            continue
        if path in seen:
            continue
        seen.add(path)
        items.append({
            "path": path,
            "size": int(record.get("size", 0)),
            "reason": reason,
        })
    items.sort(key=lambda item: (-item["size"], item["path"]))
    return {"items": items, "bytes": sum(item["size"] for item in items)}


def make_confirm_token(paths=None):
    """Deterministic token for a path set, or a fresh token for a session."""
    if paths is None:
        return secrets.token_hex(6)
    joined = "\n".join(sorted(str(path) for path in paths))
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()[:12]


def cleanup(dav, paths, token, expected_token):
    """COPY allowlisted user files into TV-side backup, then delete originals."""
    if not token or not expected_token or token != expected_token:
        raise ValueError("confirmation token mismatch")
    if not isinstance(paths, (list, tuple)) or not paths:
        raise ValueError("no paths supplied")

    checked = []
    for path in paths:
        reason = cleanup_reason(path)
        if reason is None:
            raise ValueError("path is not allowed for cleanup: {}".format(path))
        checked.append((path, reason))

    try:
        dav.mkdir(BACKUP_ROOT)
    except Exception:
        pass

    results = []
    for path, reason in checked:
        backup = BACKUP_ROOT + path.lstrip("/").replace("/", "__")
        try:
            dav.copy(path, backup)
            listing = dav.list(BACKUP_ROOT)
            if backup not in [entry.path for entry in listing.entries]:
                raise ConnectionError("backup verification failed for " + path)
            dav.delete(path)
            results.append({
                "path": path,
                "status": "deleted",
                "reason": reason,
                "backup": backup,
            })
        except Exception as exc:
            results.append({
                "path": path,
                "status": "error",
                "reason": reason,
                "error": str(exc),
            })
    return results


def list_backups(dav):
    """List files in the TV-side care backup folder."""
    try:
        listing = dav.list(BACKUP_ROOT)
    except Exception:
        return []
    return [{"path": e.path, "size": int(e.size or 0)}
            for e in listing.entries if not e.is_dir]


def purge_backups(dav, paths, token, expected_token):
    """Permanently delete selected TV-side backups; this frees the space."""
    if not token or not expected_token or token != expected_token:
        raise ValueError("confirmation token mismatch")
    if not isinstance(paths, (list, tuple)) or not paths:
        raise ValueError("no paths supplied")
    for path in paths:
        if not isinstance(path, str) or not path.startswith(BACKUP_ROOT):
            raise ValueError("path is not a care backup: {}".format(path))
        if ".." in path.split("/") or "\r" in path or "\n" in path:
            raise ValueError("bad backup path")
    results = []
    for path in paths:
        try:
            dav.delete(path)
            results.append({"path": path, "status": "purged"})
        except Exception as exc:
            results.append({"path": path, "status": "error", "error": str(exc)})
    return results


def stage_apk(dav, local_path, remote_dir="/sdcard/Upload/"):
    """Upload an APK to user storage and return the TV-installer open message."""
    if not isinstance(remote_dir, str) or not remote_dir.startswith("/sdcard/"):
        raise ValueError("remote directory must be under /sdcard")
    if ".." in remote_dir.split("/"):
        raise ValueError("bad remote directory")
    if not isinstance(local_path, str) or not os.path.isfile(local_path):
        raise ValueError("local APK not found")
    name = os.path.basename(local_path)
    if not name.lower().endswith(".apk"):
        raise ValueError("only .apk files can be staged")
    with open(local_path, "rb") as handle:
        payload = handle.read()
    remote = remote_dir.rstrip("/") + "/" + name
    dav.put(remote, payload, APK_MIME)
    return {
        "remote": remote,
        "bytes": len(payload),
        "openfile": open_file_message(remote, APK_MIME),
        "instruction": "Tap Install on the TV when its package installer opens.",
    }


def audit(session, dav, webdav_port=8000, max_depth=4):
    """Full read-only report plus the token required for cleanup."""
    apps = audit_apps(session, webdav_port)
    storage = walk_user_storage(dav, "/sdcard/", max_depth)
    reclaimable = find_reclaimable(storage["files"])
    token = make_confirm_token([item["path"] for item in reclaimable["items"]])
    backups = list_backups(dav)
    backup_token = make_confirm_token([item["path"] for item in backups])
    return {
        "ok": True,
        "apps": apps["apps"],
        "app_counts": apps["counts"],
        "storage": {
            "file_count": len(storage["files"]),
            "folder_count": len(storage["folders"]),
            "truncated": storage["truncated"],
            "errors": storage["errors"],
        },
        "reclaimable": reclaimable,
        "token": token,
        "backups": backups,
        "backup_token": backup_token,
        "note": STORAGE_NOTE,
    }
