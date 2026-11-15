"""Wire protocol primitives for the TV control service.

All messages are CRLF-terminated line records. Binary discovery packets use
big-endian 32-bit fields. Everything here is a pure function so it can be
unit-tested without a television.
"""

from dataclasses import dataclass

DISCOVERY_PORT = 48689          # 0xBE31
DISCOVERY_PACKET_LEN = 50
DISCOVERY_MAGIC = b"FindECloudBox"
DISCOVERY_MAGIC_OFFSET = 0x14
CONTROL_PORT_DEFAULT = 8121
SPEAKER_PORT_DEFAULT = 25123

CRLF = "\r\n"
END = CRLF + CRLF


@dataclass(frozen=True)
class DeviceInfo:
    name: str
    control_port: int
    speaker_port: int | None
    serial: str | None
    raw: str


def build_discovery_packet() -> bytes:
    pkt = bytearray(DISCOVERY_PACKET_LEN)
    # The server keys off total length, the magic bytes and its own offset;
    # other fields are padding the firmware tolerates as zero.
    _write_be32(pkt, 0x0C, len(DISCOVERY_MAGIC))
    pkt[DISCOVERY_MAGIC_OFFSET:DISCOVERY_MAGIC_OFFSET + len(DISCOVERY_MAGIC)] = DISCOVERY_MAGIC
    return bytes(pkt)


def parse_discovery_reply(data: bytes) -> DeviceInfo | None:
    text = data.decode("utf-8", errors="replace").strip()
    fields = text.split(":")
    if len(fields) < 5 or not fields[0].startswith("ECloudBox"):
        return None
    try:
        control_port = int(fields[2])
    except ValueError:
        return None
    speaker = int(fields[3]) if fields[3].isdigit() else None
    serial = next((f for f in fields if f.lower().startswith("sn=")), None)
    return DeviceInfo(name=fields[1], control_port=control_port,
                      speaker_port=speaker, serial=serial, raw=text)


def probe_message() -> str:
    return _record("sayHello", "TVunboxed")


def handshake_message(path: str = "/", source_port: int = 0, marker: str = "onelong") -> str:
    # The firmware expects exactly this space-separated argument order.
    return _record("auth", f"{path} {source_port} {marker}")


def identify_message(model: str = "TVunboxed") -> str:
    return _record("getFeatures", model)


def keepalive_message() -> str:
    return "HeartBeat\r\nlive\r\n\r\n"


def channel_check_message() -> str:
    return "CheckACK\r\nlive\r\n\r\n"


def key(code: int, long_press: bool = False) -> str:
    code = int(code)
    return f"KEYEVENT\r\n{code}\r\nlongpress\r\n\r\n" if long_press else f"KEYEVENT\r\n{code}\r\n\r\n"


def volume(level: int) -> str:
    level = max(0, min(100, int(level)))
    return _record(f"MediaControl\r\nsetVolume {level}")


def mute() -> str:
    return _record("SETMUTEEVENT\r\n1")


def switch_source(index: int) -> str:
    return _record(f"SWICHEVENT\r\n{int(index)}")


def airmouse(dx: float, dy: float, button: int = 0) -> str:
    return f"AIRMOUSEEVNET\r\n{float(dx)}\r\n{float(dy)}\r\n{int(button)}\r\n\r\n"


def mouse_enable(on: bool) -> str:
    return _record(f"MOUSEENABLEEVENT\r\n{1 if on else 0}")


def airclick() -> str:
    return "AIRCLICKEVENT\r\n1\r\n\r\n"


def type_text(text: str, mode: int = 1) -> str:
    """Deliver text to the TV's focused input (as sent by the app's keyboard).

    mode=1 sends the text as an edit payload ('0 <text>'); mode=0 is the
    before-change channel observed in the app (unverified semantics)."""
    text = str(text)
    if "\r" in text or "\n" in text:
        raise ValueError("single-line text only")
    return _record(f"CONTENT\r\n{int(mode)}\r\n0 {text}")


def uninstall_app(package: str) -> str:
    package = str(package).strip()
    if not package or "\r" in package or "\n" in package:
        raise ValueError("bad package name")
    return _record(f"uninstallApp\r\n{package}")


def start_browser() -> str:
    return "STARTBROWSEREVENT\r\n1\r\n\r\n"


def open_link(url: str) -> str:
    """Ask the TV to open a link/URL (as sent by the client)."""
    url = str(url).strip()
    if not url or "\r" in url or "\n" in url:
        raise ValueError("bad url")
    return _record(f"OpenLink\r\n{url}")


def media_control(command: str) -> str:
    """Raw media-control command line, e.g. 'setVolume 30', 'getCurrentPosition'."""
    command = str(command).strip()
    if not command or "\r" in command or "\n" in command:
        raise ValueError("bad media command")
    return _record(f"MediaControl\r\n{command}")


def launch(package: str) -> str:
    package = str(package).strip()
    if not package or "\r" in package or "\n" in package:
        raise ValueError("package must not be empty")
    return _record(f"STARTAPPEVNET\r\n{package}")


def _record(*lines: str) -> str:
    return "".join(part + CRLF for part in lines) + CRLF


def _write_be32(buf: bytearray, offset: int, value: int) -> None:
    buf[offset:offset + 4] = value.to_bytes(4, "big")
