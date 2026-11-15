"""Fetch and parse the TV's installed-app list.

The TV speaks a small binary protocol on its control port (same as the rest
of the the TV control service stack, but over a *separate* connection): the client
sends GETAPPINFOSEVNET and reads length-prefixed records until the socket
goes idle. Record layout (big-endian int32 lengths, UTF-8 strings):

  u32 total_len
  repeated until total_len bytes consumed:
    u32 name_len,  name bytes   (app label, UTF-8)
    u32 pkg_len,   pkg bytes    (package name — the STARTAPPEVNET argument)
    u32 c_len,     c bytes      (ancillary string; semantics unverified)
    u32 4,         u32 d        (int; plausibly versionCode — unverified)
    u32 icon_len,  icon bytes   (PNG/JPEG bytes)
"""

import base64
import socket
import struct

GET_APP_INFO = "GETAPPINFOSEVNET\r\n1\r\n\r\n"


def _u32(data, offset):
    return struct.unpack_from(">I", data, offset)[0]


def parse_app_blob(blob: bytes):
    apps = []
    if not blob or len(blob) < 4:
        return apps
    try:
        total = _u32(blob, 0)
    except struct.error:
        return apps
    end = min(total + 4, len(blob))
    pos = 4
    def take(n):
        nonlocal pos
        if pos + n > end:
            raise struct.error("truncated")
        chunk = blob[pos:pos + n]
        pos += n
        return chunk

    def read_len():
        return int.from_bytes(take(4), "big")

    while pos + 4 <= end:
        try:
            name = take(read_len()).decode("utf-8", "replace")
            pkg = take(read_len()).decode("utf-8", "replace")
            c_field = take(read_len()).decode("utf-8", "replace")
            take(4)                          # documented layout: fixed int 4
            d_value = int.from_bytes(take(4), "big")
            icon = take(read_len())
        except struct.error:
            break
        icon_b64 = base64.b64encode(icon).decode() if icon else ""
        apps.append({
            "name": name,
            "package": pkg,
            "c": c_field,
            "d": d_value,
            "icon": f"data:image/png;base64,{icon_b64}" if icon_b64 else "",
        })
    return apps


def fetch_app_list(host, port=8121, timeout=6.0, idle=0.6):
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
