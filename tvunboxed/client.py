"""TCP session to the TV's control port.

The TV keeps one connection per remote client; there is no per-message
framing on input beyond the CRLF records the firmware recognises, and key
presses are deliberately fire-and-forget.
"""

import socket
import threading
import time

from . import protocol


class Session:
    def __init__(self, host, port=protocol.CONTROL_PORT_DEFAULT, model="TVunboxed", http_port=8888):
        self.host = host
        self.port = int(port)
        self.model = model
        self.http_port = int(http_port)
        self._sock = None
        self._lock = threading.Lock()

    def open(self):
        self.close()
        sock = socket.create_connection((self.host, self.port), timeout=4)
        sock.settimeout(3)
        sock.sendall(protocol.probe_message().encode())
        sock.sendall(protocol.handshake_message("/storage/emulated/0", self.http_port, "onelong").encode())
        self._sock = sock

    def close(self):
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def send(self, payload, wait_reply=False):
        with self._lock:
            last_error = None
            for _ in range(2):
                try:
                    self._ensure_open()
                    self._sock.sendall(payload.encode())
                    if not wait_reply:
                        return ""
                    return self._sock.recv(8192).decode("utf-8", errors="replace").strip()
                except OSError as exc:
                    last_error = exc
                    self.close()
            raise ConnectionError(f"TV {self.host}:{self.port} unreachable: {last_error}")

    def _ensure_open(self):
        if self._sock is None:
            self.open()


def collect_once(host, port, payloads, auth_path="/storage/emulated/0",
                 http_port=8080, wait=1.2):
    """One-shot command batch on a dedicated socket; returns everything the TV
    emitted within `wait` seconds. Used for MediaControl whose replies arrive
    asynchronously and may coalesce or lag the command that triggered them."""
    sock = socket.create_connection((host, int(port)), timeout=4)
    try:
        sock.settimeout(2)
        sock.sendall(protocol.probe_message().encode())
        sock.sendall(protocol.handshake_message(auth_path, http_port, "onelong").encode())
        for p in payloads:
            sock.sendall(p.encode())
        time.sleep(0.05)
        sock.settimeout(wait)
        buf = b""
        while len(buf) < 65536:
            try:
                chunk = sock.recv(4096)
            except (socket.timeout, TimeoutError):
                break
            if not chunk:
                break
            buf += chunk
        return buf.decode("utf-8", errors="replace")
    finally:
        sock.close()
