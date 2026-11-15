"""Broadcast discovery: who on this LAN runs the TV control service?"""

import select
import socket
import time

from . import protocol


def scan(timeout: float = 2.0):
    found = {}
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(("", 0))
    try:
        sock.sendto(protocol.build_discovery_packet(), ("255.255.255.255", protocol.DISCOVERY_PORT))
    except OSError:
        pass
    deadline = time.monotonic() + timeout
    sock.setblocking(False)
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        ready, _, _ = select.select([sock], [], [], remaining)
        if not ready:
            continue
        try:
            data, addr = sock.recvfrom(2048)
        except BlockingIOError:
            continue
        info = protocol.parse_discovery_reply(data)
        if info:
            found[addr[0]] = info
    sock.close()
    return [(ip, info) for ip, info in sorted(found.items())]
