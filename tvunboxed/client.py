"""TCP session to the TV's control port.

The TV keeps one connection per remote client; there is no per-message
framing on input beyond the CRLF records the firmware recognises, and key
presses are deliberately fire-and-forget.
"""

import socket
import threading

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
        sock.sendall(protocol.handshake_message("/mnt/sdcard", self.http_port, "onelong").encode())
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
