"""Build file-share payloads for the TV's 'open on TV' flow."""

import mimetypes
import re
from urllib.parse import quote, unquote

_BARE_HOST = re.compile(r"^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/|$)")

def _normalize_target(target: str) -> str:
    """Fix common user URL mistakes: strip duplicate slashes and add a scheme
    when the TV would otherwise treat a protocol-relative or bare-host path as
    relative to our bridge."""
    target = target.strip()
    if target.startswith("//"):
        return "https:" + target
    if target.startswith(("http://", "https://")):
        return target
    if "\r" in target or "\n" in target:
        return target
    if _BARE_HOST.match(target):
        return "https://" + target
    return target


def encode_path(relpath: str) -> str:
    """URL-encode a path segment-wise, preserving slashes (as the client does)."""
    parts = [quote(seg, safe="") for seg in relpath.strip("/").split("/")]
    return "/".join(parts)


def open_file_message(target: str, mime: str | None = None) -> str:
    """'Openfile' tells the TV to fetch /<target> from the HTTP origin named
    in the session auth handshake (our bridge). Absolute http(s) URLs are
    passed through untouched (used for streams)."""
    target = _normalize_target(target)
    if target.startswith(("http://", "https://")):
        if mime is None:
            mime = mimetypes.guess_type(target)[0] or "video/mp4"
        return f"Openfile\r\nOpen {target} {mime}\r\n\r\n"
    if mime is None:
        mime = mimetypes.guess_type(target)[0] or "application/octet-stream"
    return f"Openfile\r\nOpen {encode_path(target)} {mime}\r\n\r\n"


def save_file_message(target: str, mime: str | None = None) -> str:
    """'Savefile' makes the TV download /<target> from our bridge and store it
    under its own Upload folder (copy, not open)."""
    target = _normalize_target(target)
    if target.startswith(("http://", "https://")):
        if mime is None:
            mime = mimetypes.guess_type(target)[0] or "application/octet-stream"
        return f"Savefile\r\nSave {target} {mime}\r\n\r\n"
    if mime is None:
        mime = mimetypes.guess_type(target)[0] or "application/octet-stream"
    return f"Savefile\r\nSave {encode_path(target)} {mime}\r\n\r\n"


def normalize_relpath(relpath: str) -> str:
    """Return a clean relative path, or raise ValueError on escape attempts."""
    relpath = unquote(str(relpath)).replace("\\", "/").strip("/")
    parts = [p for p in relpath.split("/") if p not in ("", ".")]
    if any(p == ".." for p in parts):
        raise ValueError("path escapes share root")
    return "/".join(parts)


def list_share(share_dir: str, relpath: str = ".", limit: int = 500) -> dict:
    """List one directory of the share. Directories first, then files,
    both alphabetical. Path is confined to share_dir."""
    import os

    relpath = normalize_relpath(relpath) or "."
    base = os.path.realpath(share_dir)
    target = os.path.realpath(os.path.join(base, relpath))
    if target != base and not target.startswith(base + os.sep):
        raise ValueError("path escapes share root")
    entries = []
    with os.scandir(target) as it:
        for entry in it:
            try:
                is_dir = entry.is_dir(follow_symlinks=False)
                stat = entry.stat(follow_symlinks=False)
            except OSError:
                continue
            entries.append({"name": entry.name, "type": "dir" if is_dir else "file",
                            "size": 0 if is_dir else stat.st_size,
                            "mtime": int(stat.st_mtime)})
            if len(entries) >= limit:
                break
    entries.sort(key=lambda e: (e["type"] != "dir", e["name"].lower()))
    return {"dir": relpath, "entries": entries}
