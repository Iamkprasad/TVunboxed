"""Minimal WebDAV client for the TV's own built-in HTTP/WebDAV service (port 8000).

The firmware serves a subset of WebDAV without authentication. Everything
here uses the standard library only; callers get parsed device entries, not
raw XML. Verified live: browse, upload to writable folders, delete.
"""

import http.client
import os
import time as _time
import urllib.parse
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field


DAV_NS = "DAV:"
_PROP_REQUEST = (
    '<?xml version="1.0" encoding="utf-8"?>'
    '<D:propfind xmlns:D="DAV:"><D:prop>'
    "<D:resourcetype/><D:getcontentlength/><D:getlastmodified/><D:getetag/>"
    "<D:displayname/><D:quota-available-bytes/><D:quota-used-bytes/>"
    "</D:prop></D:propfind>"
).encode()


@dataclass
class DavEntry:
    name: str
    path: str
    is_dir: bool
    size: int | None
    modified: str | None
    etag: str | None
    display: str | None


@dataclass
class DavList:
    path: str
    entries: list = field(default_factory=list)
    quota_available: int | None = None
    quota_used: int | None = None


def _parse_multistatus(body: bytes, requested_path: str) -> DavList:
    root = ET.fromstring(body)
    result = DavList(path=requested_path)
    for response in root.iter(f"{{{DAV_NS}}}response"):
        href = (response.findtext(f"{{{DAV_NS}}}href") or "").strip()
        prop = response.find(f"{{{DAV_NS}}}propstat/{{{DAV_NS}}}prop")
        if prop is None:
            continue
        url_path = urllib.parse.unquote(urllib.parse.urlsplit(href).path)
        is_dir = prop.find(f"{{{DAV_NS}}}resourcetype/{{{DAV_NS}}}collection") is not None
        try:
            size = int(prop.findtext(f"{{{DAV_NS}}}getcontentlength") or "0") if not is_dir else 0
        except ValueError:
            size = None

        def _int(tag):
            raw = prop.findtext(f"{{{DAV_NS}}}{tag}")
            return int(raw) if raw and raw.strip().isdigit() else None

        if result.quota_available is None:
            result.quota_available = _int("quota-available-bytes")
        if result.quota_used is None:
            result.quota_used = _int("quota-used-bytes")
        name = os.path.basename(url_path.rstrip("/")) or url_path
        result.entries.append(DavEntry(
            name=name,
            path=url_path,
            is_dir=is_dir,
            size=size,
            modified=prop.findtext(f"{{{DAV_NS}}}getlastmodified"),
            etag=prop.findtext(f"{{{DAV_NS}}}getetag"),
            display=prop.findtext(f"{{{DAV_NS}}}displayname"),
        ))
    requested = requested_path.rstrip("/") or "/"
    result.entries = [e for e in result.entries if e.path.rstrip("/") != requested]
    result.entries.sort(key=lambda e: (not e.is_dir, e.name.lower()))
    return result


def _roundtrip(host, port, method, path, body=None, timeout=8, extra_headers=None):
    conn = http.client.HTTPConnection(host, port, timeout=timeout)
    headers = {"Host": f"{host}:{port}"}
    if extra_headers:
        headers.update(extra_headers)
    try:
        conn.request(method, urllib.parse.quote(path, safe="/?&=%"), body=body, headers=headers)
        resp = conn.getresponse()
        payload = resp.read()
        return resp.status, dict(resp.getheaders()), payload
    finally:
        conn.close()


class WebDavClient:
    def __init__(self, host, port=8000, base_path="/"):
        self.host = host
        self.port = int(port)
        self.base_path = base_path if base_path.startswith("/") else "/" + base_path

    def _abs(self, path):
        path = str(path or "/")
        if not path.startswith("/"):
            path = "/" + path
        joined = self.base_path.rstrip("/") + path
        return joined or "/"

    def list(self, path="/"):
        status, _, payload = _roundtrip(
            self.host, self.port, "PROPFIND", self._abs(path),
            body=_PROP_REQUEST,
            extra_headers={"Depth": "1", "Content-Type": "application/xml; charset=utf-8"})
        if status not in (207, 200, 404):
            raise ConnectionError(f"PROPFIND {path} -> HTTP {status}")
        if status == 404:
            raise FileNotFoundError(path)
        return _parse_multistatus(payload, self._abs(path))

    def get(self, path):
        status, headers, payload = _roundtrip(self.host, self.port, "GET", self._abs(path))
        if status != 200:
            raise ConnectionError(f"GET {path} -> HTTP {status}")
        return payload, headers.get("Content-Type", "application/octet-stream")

    def put(self, path, data, content_type=None):
        if isinstance(data, str):
            data = data.encode()
        status, _, _ = _roundtrip(
            self.host, self.port, "PUT", self._abs(path), body=data,
            extra_headers={"Content-Type": content_type or "application/octet-stream"})
        if status not in (200, 201, 204):
            raise ConnectionError(f"PUT {path} -> HTTP {status}")
        return status

    def move(self, src, dst):
        status, _, _ = _roundtrip(
            self.host, self.port, "MOVE", self._abs(src),
            extra_headers={"Destination": f"http://{self.host}:{self.port}{self._abs(dst)}"})
        if status not in (200, 201, 204):
            raise ConnectionError(f"MOVE {src} -> {dst}: HTTP {status}")
        return status

    def delete(self, path):
        status, _, _ = _roundtrip(self.host, self.port, "DELETE", self._abs(path))
        if status not in (200, 204):
            raise ConnectionError(f"DELETE {path} -> HTTP {status}")
        return status

    def mkdir(self, path):
        status, _, _ = _roundtrip(self.host, self.port, "MKCOL", self._abs(path))
        if status not in (201, 405):
            raise ConnectionError(f"MKCOL {path} -> HTTP {status}")
        return status

    def ping(self):
        t0 = _time.monotonic()
        try:
            self.list("/")
            return round((_time.monotonic() - t0) * 1000, 1)
        except Exception:
            return None
