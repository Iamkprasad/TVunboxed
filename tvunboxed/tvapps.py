"""Fetch and parse the TV's installed-app list.

The TV serves this on its control port (same endpoint as everything else),
but over a *dedicated* connection: the client sends GETAPPINFOSEVNET and
reads length-prefixed records until the socket goes idle.

Blob layout (all integers little-endian u32):
  [4 bytes: preamble]                e.g. 4e 55 02 00 — semantics unverified
  repeated until end of buffer:
    u32 name_len, name bytes         (app label, UTF-8)
    u32 pkg_len,  pkg bytes          (package — STARTAPPEVNET argument)
    u32 c_len,    c bytes            (versionName)
    u32 4,        u32 d              (fixed 4, then versionCode)
    u32 icon_len, icon bytes         (PNG/JPEG)
Verified against a live capture: 19 records consumed the buffer exactly.
"""

import base64
import socket
import struct

GET_APP_INFO = "GETAPPINFOSEVNET\r\n1\r\n\r\n"
PREAMBLE_LEN = 4


def parse_app_blob(blob: bytes):
    apps = []
    pos = PREAMBLE_LEN
    end = len(blob)

    def take(n):
        nonlocal pos
        if pos + n > end:
            raise struct.error("truncated")
        chunk = blob[pos:pos + n]
        pos += n
        return chunk

    def read_len():
        return struct.unpack("<I", take(4))[0]

    while pos + 4 <= end:
        try:
            name = take(read_len()).decode("utf-8", "replace")
            pkg = take(read_len()).decode("utf-8", "replace")
            version_name = take(read_len()).decode("utf-8", "replace")
            take(4)                       # fixed 0x00000004 in captures
            version_code = struct.unpack("<I", take(4))[0]
            icon = take(read_len())
        except struct.error:
            break
        apps.append({
            "name": name,
            "package": pkg,
            "versionName": version_name,
            "versionCode": version_code,
            "icon": "data:image/png;base64," + base64.b64encode(icon).decode() if icon else "",
        })
    return apps


def fetch_app_list(host, port=8121, timeout=8.0, idle=3.0):
    """Open a dedicated connection to the TV control port, request the app
    list, read until the socket goes idle, and parse it."""
    sock = socket.create_connection((host, port), timeout=timeout)
    try:
        sock.sendall(GET_APP_INFO.encode())
        sock.settimeout(idle)
        chunks = []
        while True:
            try:
                chunk = sock.recv(65536)
            except socket.timeout:
                break
            except OSError:
                break
            if not chunk:
                break
            chunks.append(chunk)
        blob = b"".join(chunks)
    finally:
        sock.close()
    return parse_app_blob(blob)
