"""HTTP bridge: serves the remote UI and translates REST calls to the
TCP control channel. Standard library only."""

import datetime
import json
import os
import re
import threading
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import protocol
from .client import Session, collect_once
from .discovery import scan
from .share import list_share, normalize_relpath, open_file_message, save_file_message
from .tvdav import WebDavClient
from .tvapps import fetch_app_list

import socket as _socket
_STARTED = time.monotonic()
_LOG_LOCK = threading.Lock()


def _log_write(server, entry):
    line = json.dumps(entry, ensure_ascii=False, separators=(",", ":"))
    path = getattr(server, "log_path", None)
    if not path:
        return
    try:
        with _LOG_LOCK:
            with open(path, "a", encoding="utf-8") as f:
                f.write(line + "\n")
    except OSError:
        pass


def log_event(server, kind, **fields):
    entry = {"ts": datetime.datetime.utcnow().isoformat(timespec="seconds") + "Z",
             "kind": kind}
    entry.update(fields)
    _log_write(server, entry)
    return entry


WEB_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "web"))

_CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".ico": "image/x-icon",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".gif": "image/gif",
    ".mp4": "video/mp4",
    ".mkv": "video/x-matroska",
    ".ts": "video/mp2t",
    ".mp3": "audio/mpeg",
    ".m3u": "audio/x-mpegurl",
    ".m3u8": "application/vnd.apple.mpegurl",
    ".pdf": "application/pdf",
}


class RemoteHandler(BaseHTTPRequestHandler):
    server_version = "TVunboxed/2"

    # -- reads -----------------------------------------------------------------
    def do_GET(self):
        t0 = time.monotonic()
        route = self.path.split("?", 1)[0]
        try:
            self._do_GET_route(route)
        finally:
            if route.startswith("/api/"):
                log_event(self.server, "http", method="GET", path=self.path,
                          ms=round((time.monotonic() - t0) * 1000, 2))

    def _do_GET_route(self, route):
        if route == "/api/features":
            try:
                reply = self.server.session.send(protocol.identify_message(), wait_reply=True)
                return self._send_json({"ok": True, "features": reply})
            except ConnectionError as exc:
                return self._send_json({"ok": False, "error": str(exc)}, 502)
        if route == "/api/status":
            tv = self.server.session
            return self._send_json({"ok": True, "tv": f"{tv.host}:{tv.port}",
                                    "share": self.server.share is not None})
        if route == "/api/files":
            return self._cmd_list_files()
        if route == "/api/health":
            return self._cmd_health()
        if route == "/api/dav/list":
            return self._cmd_dav_list()
        if route == "/api/tvapps":
            return self._cmd_tvapps()
        if route == "/api/dav/get":
            return self._cmd_dav_get()
        if route == "/api/tv/info/volume":
            return self._cmd_tv_http("get_volume", "/remote/get_volume")
        if route == "/api/tv/info/media":
            return self._cmd_tv_http("get_media_info", "/remote/get_media_info")
        if route == "/api/tv/info/position":
            return self._cmd_tv_http("get_position_info", "/remote/get_position_info")
        if route == "/api/tv/info/wifi":
            return self._cmd_tv_http("get_wifi_info", "/remote/get_wifi_info")
        if route == "/api/tv/info/apps":
            return self._cmd_tv_http("getinstallapks", "/apkmanager/getinstallapks", json_body=True, cache_ttl=300)
        if route.startswith("/files/"):
            return self._serve_shared(route[len("/files/"):])
        if route == "/api/media":
            return self._cmd_media_info()
        for prefix in ("/sdcard/Download/", "/mnt/sdcard/Download/",
                       "/storage/emulated/0/Download/",
                       "sdcard/Download/", "mnt/sdcard/Download/",
                       "storage/emulated/0/Download/"):
            if route.startswith(prefix):
                return self._serve_shared(route[len(prefix):])
        return self._serve_static(route)

    # -- writes ----------------------------------------------------------------
    def do_POST(self):
        t0 = time.monotonic()
        try:
            self._do_POST_route()
        finally:
            if self.path.startswith("/api/"):
                log_event(self.server, "http", method="POST", path=self.path,
                          ms=round((time.monotonic() - t0) * 1000, 2))

    def _do_POST_route(self):
        if self.path == "/api/dav/put":
            return self._cmd_dav_put()

        try:
            body = self._read_body()
        except ValueError:
            return self._send_json({"ok": False, "error": "bad json"}, 400)

        handler = {
            "/api/key": self._cmd_key,
            "/api/volume": self._cmd_volume,
            "/api/mute": self._cmd_mute,
            "/api/source": self._cmd_source,
            "/api/app": self._cmd_app,
            "/api/openfile": self._cmd_openfile,
            "/api/savefile": self._cmd_savefile,
            "/api/open-url": self._cmd_open_url,
            "/api/browser": self._cmd_browser,
            "/api/airmouse": self._cmd_airmouse,
            "/api/media/seek": self._cmd_media_seek,
            "/api/airclick": self._cmd_airclick,
            "/api/mouse-mode": self._cmd_mouse_mode,
            "/api/type": self._cmd_type,
            "/api/uninstall": self._cmd_uninstall,
            "/api/dav/delete": self._cmd_dav_delete,
            "/api/dav/mkcol": self._cmd_dav_mkcol,
            "/api/dav/move": self._cmd_dav_move,
        }.get(self.path)
        if handler is None:
            return self._send_json({"ok": False, "error": "unknown route"}, 404)
        try:
            handler(body)
        except (ValueError, KeyError) as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 400)
        except ConnectionError as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        return self._send_json({"ok": True})

    # advanced commands ----------------------------------------------------------
    def _cmd_health(self):
        import time as _time
        tv = self.server.session
        try:
            probe = _socket.socket(_socket.AF_INET, _socket.SOCK_DGRAM)
            probe.connect((tv.host, 9))
            local_ip = probe.getsockname()[0]
            probe.close()
        except OSError:
            local_ip = "unknown"
        health = {"ok": True,
                  "tv": {"host": tv.host, "port": tv.port},
                  "webdav": {"host": tv.host, "port": self.server.tvdav.port},
                  "bridge": {"uptime_s": int(_time.monotonic() - _STARTED),
                             "local_ip": local_ip,
                             "share": self.server.share}}
        start = _time.monotonic()
        try:
            health["tv"]["features"] = tv.send(protocol.identify_message(), wait_reply=True)
            health["tv"]["reachable"] = True
        except ConnectionError as exc:
            health["tv"]["reachable"] = False
            health["tv"]["error"] = str(exc)
        health["tv"]["latency_ms"] = round((_time.monotonic() - start) * 1000, 1)
        latency = self.server.tvdav.ping()
        health["webdav"]["reachable"] = latency is not None
        health["webdav"]["latency_ms"] = latency
        return self._send_json(health)

    def _cmd_tvapps(self):
        import time as _time
        cached = getattr(self.server, "tvapps_cache", None)
        if cached and _time.monotonic() - cached["at"] < 600:
            return self._send_json({"ok": True, "apps": cached["apps"], "cached": True})
        try:
            apps = fetch_app_list(self.server.session.host, self.server.session.port)
        except (ConnectionError, OSError) as exc:
            return self._send_json({"ok": False, "error": f"tv app list unavailable: {exc}"}, 502)
        self.server.tvapps_cache = {"at": _time.monotonic(), "apps": apps}
        return self._send_json({"ok": True, "apps": apps, "cached": False})

    def _cmd_tv_http(self, action, path, json_body=False, cache_ttl=0):
        """Read-only proxy for the TV's built-in HTTP endpoints (no TV UI impact)."""
        import time as _time
        cache = getattr(self.server, "tvhttp_cache", {})
        if cache_ttl:
            hit = cache.get(path)
            if hit and _time.monotonic() - hit["at"] < cache_ttl:
                return self._send_json(hit["out"])
        host = self.server.tvdav.host
        port = self.server.tvdav.port
        url = f"http://{host}:{port}{path}"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "TVunboxed"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, OSError, TimeoutError, ValueError) as exc:
            return self._send_json({"ok": False, "action": action, "error": str(exc)}, 502)
        out = {"ok": True, "action": action, "body": raw}
        if json_body:
            try:
                out["data"] = json.loads(raw) if raw.strip() else None
            except json.JSONDecodeError:
                out["data"] = None
        if cache_ttl:
            cache[path] = {"at": _time.monotonic(), "out": dict(out)}
            self.server.tvhttp_cache = cache
        return self._send_json(out)

    def _dav(self):
        return self.server.tvdav

    def _dav_path(self, body):
        path = str(body.get("path", "/"))
        if "\r" in path or "\n" in path:
            raise ValueError("bad path")
        return path

    def _cmd_dav_list(self):
        query = {}
        if "?" in self.path:
            for piece in self.path.split("?", 1)[1].split("&"):
                if "=" in piece:
                    k, v = piece.split("=", 1)
                    query[k] = v
        target = query.get("dir", "/")
        try:
            listing = self._dav().list(target)
        except FileNotFoundError:
            return self._send_json({"ok": False, "error": "not found on TV"}, 404)
        except (ConnectionError, OSError) as exc:
            return self._send_json({"ok": False, "error": f"tv webdav unavailable: {exc}"}, 502)
        entries = [{"name": e.name, "path": e.path, "dir": e.is_dir,
                    "size": e.size, "modified": e.modified} for e in listing.entries]
        return self._send_json({"ok": True, "dir": listing.path,
                                "entries": entries,
                                "quota_available": listing.quota_available,
                                "quota_used": listing.quota_used})

    def _cmd_dav_get(self):
        query = {}
        if "?" in self.path:
            for piece in self.path.split("?", 1)[1].split("&"):
                if "=" in piece:
                    k, v = piece.split("=", 1)
                    query[k] = v
        import urllib.parse
        target = urllib.parse.unquote(query.get("path", "/"))
        if "\r" in target or "\n" in target:
            return self._send_json({"ok": False, "error": "bad path"}, 400)
        dest = self._dav()._abs(target)
        url = f"http://{self.server.tvdav.host}:{self.server.tvdav.port}{dest}"
        self.send_response(302)
        self.send_header("Location", url)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _cmd_dav_put(self):
        rel = self.headers.get("X-File-Path", "").strip().lstrip("/")
        if not rel or "\r" in rel or "\n" in rel or ".." in rel.split("/"):
            return self._send_json({"ok": False, "error": "bad X-File-Path"}, 400)
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return self._send_json({"ok": False, "error": "bad Content-Length"}, 400)
        data = self.rfile.read(length) if length else b""
        try:
            self._dav().put("/" + rel, data)
        except (ConnectionError, OSError) as exc:
            return self._send_json({"ok": False, "error": f"tv webdav unavailable: {exc}"}, 502)
        return self._send_json({"ok": True})

    def _cmd_dav_delete(self, body):
        self._dav().delete(self._dav_path(body))

    def _cmd_dav_mkcol(self, body):
        self._dav().mkdir(self._dav_path(body))

    def _cmd_dav_move(self, body):
        self._dav().move(str(body.get("from", "")).strip(), str(body.get("to", "")).strip())

    def _cmd_uninstall(self, body):
        if str(body.get("confirm", "")).lower() != "uninstall":
            raise ValueError("send confirm='uninstall' to proceed")
        self.server.session.send(protocol.uninstall_app(body.get("package", "")))

    def _cmd_media_info(self):
        tv = self.server.session
        # MediaControl replies arrive asynchronously and may coalesce; a fresh
        # dedicated socket keeps late answers from contaminating the key channel.
        payloads = [protocol.media_query("getCurrentPosition"),
                    protocol.media_query("getMediaDuration"),
                    protocol.media_query("getVolume"),
                    protocol.media_query("getCurrentVolume")]  # trailing flush
        try:
            raw = collect_once(tv.host, tv.port, payloads,
                               http_port=getattr(tv, "http_port", 8080), wait=1.2)
        except (ConnectionError, OSError) as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 502)
        pos = protocol.parse_media_int(raw, "CurrentPosition")
        dur = protocol.parse_media_int(raw, "MediaDuration")
        vol = maxv = None
        m = re.search(r"CurrentVolume[\s=:]*(\d+)/(\d+)", raw)
        if m:
            vol, maxv = int(m.group(1)), int(m.group(2))
        http_media = self._tv_http_body("/remote/get_media_info")
        http_pos = self._tv_http_body("/remote/get_position_info")
        return self._send_json({"ok": True,
                                "position_ms": pos, "duration_ms": dur,
                                "volume": vol, "max_volume": maxv,
                                "playing": http_media or None,
                                "position_http": http_pos or None,
                                "raw": raw})

    def _tv_http_body(self, path):
        try:
            with urllib.request.urlopen(f"http://{self.server.tvdav.host}:{self.server.tvdav.port}{path}", timeout=3) as resp:
                return resp.read().decode("utf-8", errors="replace").strip()
        except Exception:
            return ""

    def _cmd_media_seek(self, body):
        self.server.session.send(protocol.media_seek(body.get("position_ms", 0)))

    def _cmd_open_url(self, body):
        url = str(body.get("url", "")).strip()
        if not url.startswith(("http://", "https://")):
            raise ValueError("absolute http(s) url required")
        if "\r" in url or "\n" in url:
            raise ValueError("bad url")
        self.server.session.send(open_file_message(url))

    def _cmd_browser(self, _body):
        self.server.session.send(protocol.start_browser())

    def _cmd_airmouse(self, body):
        self.server.session.send(protocol.airmouse(body.get("dx", 0), body.get("dy", 0), int(body.get("button", 0))))

    def _cmd_airclick(self, _body):
        self.server.session.send(protocol.airclick())

    def _cmd_mouse_mode(self, body):
        self.server.session.send(protocol.mouse_enable(bool(body.get("on", True))))

    def _cmd_type(self, body):
        self.server.session.send(protocol.type_text(body.get("text", ""), int(body.get("mode", 1))))

    # remote commands -----------------------------------------------------------
    def _cmd_key(self, body):
        self.server.session.send(protocol.key(body["code"], bool(body.get("long", False))))

    def _cmd_volume(self, body):
        self.server.session.send(protocol.volume(body["level"]))

    def _cmd_mute(self, _body):
        self.server.session.send(protocol.mute())

    def _cmd_source(self, body):
        self.server.session.send(protocol.switch_source(body["index"]))

    def _cmd_app(self, body):
        self.server.session.send(protocol.launch(body["package"]))

    def _share_target(self, body):
        """Resolve a request path to a file inside the share root."""
        target = str(body.get("path", "")).strip()
        if not target:
            raise ValueError("missing path")
        if self.server.share is None:
            raise ValueError("file share not enabled (start with --share DIR)")
        # accept absolute Android paths under known download roots, map into share
        for prefix in ("/sdcard/Download/", "/mnt/sdcard/Download/",
                       "/storage/emulated/0/Download/",
                       "sdcard/Download/", "mnt/sdcard/Download/",
                       "storage/emulated/0/Download/",
                       "/sdcard/", "/mnt/sdcard/", "/storage/emulated/0/",
                       "sdcard/", "mnt/sdcard/", "storage/emulated/0/"):
            if target.startswith(prefix):
                target = target[len(prefix):]
                break
        rel = normalize_relpath(target)
        dest = self._share_path(rel)
        if not dest or not os.path.isfile(dest):
            raise ValueError("no such file in share")
        return "files/" + rel

    def _cmd_openfile(self, body):
        target = str(body.get("path", "")).strip()
        if target.startswith(("http://", "https://")) or target.startswith("/"):
            # absolute path: TV-side location (after a DAV upload) — pass through
            self.server.session.send(open_file_message(target))
            return
        self.server.session.send(open_file_message(self._share_target(body)))

    def _cmd_savefile(self, body):
        self.server.session.send(save_file_message(self._share_target(body)))

    def _cmd_list_files(self):
        if self.server.share is None:
            return self._send_json({"ok": False, "error": "share disabled"}, 400)
        query = {}
        if "?" in self.path:
            for piece in self.path.split("?", 1)[1].split("&"):
                if "=" in piece:
                    key, value = piece.split("=", 1)
                    query[key] = value
        try:
            result = list_share(self.server.share, query.get("dir", "."))
        except (ValueError, OSError) as exc:
            return self._send_json({"ok": False, "error": str(exc)}, 400)
        parent = os.path.dirname(result["dir"].strip("/"))
        result["parent"] = parent if parent != result["dir"] else None
        return self._send_json({"ok": True, **result})

    # static + share serving ----------------------------------------------------
    def _share_path(self, relpath):
        base = os.path.realpath(self.server.share)
        target = os.path.realpath(os.path.join(base, normalize_relpath(relpath)))
        if target == base or target.startswith(base + os.sep):
            return target
        return None

    def _serve_shared(self, relpath):
        if self.server.share is None:
            return self._send_json({"ok": False, "error": "share disabled"}, 404)
        norm = relpath.strip()
        for prefix in ("/sdcard/Download/", "/mnt/sdcard/Download/",
                       "/storage/emulated/0/Download/",
                       "sdcard/Download/", "mnt/sdcard/Download/",
                       "storage/emulated/0/Download/",
                       "/sdcard/", "/mnt/sdcard/", "/storage/emulated/0/",
                       "sdcard/", "mnt/sdcard/", "storage/emulated/0/"):
            if norm.startswith(prefix):
                norm = norm[len(prefix):]
                break
        target = self._share_path(norm)
        if not target or not os.path.isfile(target):
            return self._send_json({"ok": False, "error": "not found"}, 404)
        return self._send_file(target)

    def _serve_static(self, route):
        rel = "index.html" if route in ("/", "") else route.lstrip("/")
        target = os.path.normpath(os.path.join(WEB_ROOT, rel))
        if not target.startswith(WEB_ROOT) or not os.path.isfile(target):
            return self._send_json({"ok": False, "error": "not found"}, 404)
        return self._send_file(target)

    def _send_file(self, target):
        ctype = _CONTENT_TYPES.get(os.path.splitext(target)[1].lower(), "application/octet-stream")
        size = os.path.getsize(target)
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(size))
        self.end_headers()
        with open(target, "rb") as handle:
            while True:
                chunk = handle.read(256 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    # plumbing -------------------------------------------------------------------
    def _read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("bad json") from exc
        if not isinstance(data, dict):
            raise ValueError("json object expected")
        return data

    def _send_json(self, obj, status=200):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        import sys
        msg = fmt % args
        sys.stderr.write("%s - %s\n" % (self.address_string(), msg))
        log_event(self.server, "access", client=self.address_string(), message=msg)


def run(session_host, tv_port=protocol.CONTROL_PORT_DEFAULT, bind="127.0.0.1",
        http_port=8080, share_dir=None, tv_webdav_port=8000, log_path="tvunboxed.log"):
    if share_dir and not os.path.isdir(share_dir):
        raise SystemExit(f"share directory not found: {share_dir}")
    httpd = ThreadingHTTPServer((bind, http_port), RemoteHandler)
    httpd.session = Session(session_host, tv_port, http_port=http_port)
    httpd.share = share_dir
    httpd.tvdav = WebDavClient(session_host, tv_webdav_port, "/")
    httpd.log_path = log_path
    print(f"TVunboxed http://{bind}:{http_port}/  ->  TV {session_host}:{tv_port}")
    print(f"tv webdav: {session_host}:{tv_webdav_port}  (unauthenticated, read/write — trusted Wi-Fi only)")
    if share_dir:
        print(f"file share: {share_dir}  (visible on the LAN as /files/)")
    if log_path:
        print(f"log file: {log_path}")
        log_event(httpd, "start", tv=f"{session_host}:{tv_port}", bind=bind, http_port=http_port)
    try:
        httpd.serve_forever()
    finally:
        httpd.session.close()
