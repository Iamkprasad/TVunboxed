"""Entry point: python3 -m tvunboxed [--tv IP] [--port N] [--lan] [--http-port N] [--discover]"""

import argparse
import os
import sys

from . import protocol
from .discovery import scan
from .server import run

_DEFAULT_SHARE = os.environ.get("TVUNBOXED_SHARE")
if _DEFAULT_SHARE is None and os.path.isdir("/sdcard/Download"):
    _DEFAULT_SHARE = "/sdcard/Download"


def main() -> int:
    parser = argparse.ArgumentParser(prog="tvunboxed", description=__doc__)
    parser.add_argument("--tv", help="TV IP address; without it, discovery on the LAN is used")
    parser.add_argument("--port", type=int, default=protocol.CONTROL_PORT_DEFAULT)
    parser.add_argument("--bind", default="127.0.0.1", help="listen address (default: localhost only)")
    parser.add_argument("--http-port", type=int, default=8080)
    parser.add_argument("--lan", action="store_true", help="listen on all interfaces (trusted Wi-Fi only)")
    parser.add_argument("--discover", action="store_true", help="print discovered TVs and exit")
    parser.add_argument("--share", default=_DEFAULT_SHARE,
                        help="directory to serve to the TV at /files/ (default: %(default)s)")
    parser.add_argument("--no-share", action="store_true", help="disable the file share")
    parser.add_argument("--tv-webdav-port", type=int, default=int(os.environ.get("TVUNBOXED_TVWEBDAV_PORT", 8000)),
                        help="TV HTTP/WebDAV port (default: 8000)")
    args = parser.parse_args()

    found = [] if args.tv else scan()
    if args.discover:
        if not found:
            print("no the TV control service found on this LAN")
            return 1
        for ip, info in found:
            print(f"{ip}\t{info.name}\tport={info.control_port}\t{info.serial or ''}")
        return 0

    tv_ip = args.tv
    tv_port = args.port
    if not tv_ip:
        if len(found) == 1:
            tv_ip, info = found[0]
            tv_port = info.control_port
            print(f"using discovered TV {info.name} at {tv_ip}:{tv_port}")
        elif len(found) > 1:
            print("several TVs found, pick one with --tv:", file=sys.stderr)
            for ip, info in found:
                print(f"  {ip}\t{info.name}")
            return 1
        else:
            print("no TV found on the LAN; pass --tv IP", file=sys.stderr)
            return 1

    bind = "0.0.0.0" if args.lan else args.bind
    if bind == "0.0.0.0":
        print("warning: no authentication; keep this on a trusted network only", file=sys.stderr)
    share = None if args.no_share else args.share
    run(tv_ip, tv_port, bind, args.http_port, share, args.tv_webdav_port)
    return 0


if __name__ == "__main__":
    sys.exit(main())
