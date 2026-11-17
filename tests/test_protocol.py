"""Original protocol tests. These assert our implementation's behaviour,
nout a copy of any vendor test suite."""

import pathlib
import socket
import sys
import threading
import time
import unittest
import base64
import struct

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tvunboxed import protocol  # noqa: E402
from tvunboxed.client import Session  # noqa: E402


class DiscoveryPacketTests(unittest.TestCase):
    def test_layout(self):
        pkt = protocol.build_discovery_packet()
        self.assertEqual(len(pkt), protocol.DISCOVERY_PACKET_LEN)
        self.assertEqual(int.from_bytes(pkt[0x0C:0x10], "big"), len(protocol.DISCOVERY_MAGIC))
        self.assertEqual(pkt[protocol.DISCOVERY_MAGIC_OFFSET:
                             protocol.DISCOVERY_MAGIC_OFFSET + len(protocol.DISCOVERY_MAGIC)],
                         protocol.DISCOVERY_MAGIC)

    def test_reply_parsing(self):
        info = protocol.parse_discovery_reply(
            b"ECloudBox516:ExampleTV:8121:24680:whynot:sn=0x0000:1")
        self.assertIsNotNone(info)
        self.assertEqual(info.control_port, 8121)
        self.assertEqual(info.speaker_port, 24680)
        self.assertEqual(info.serial, "sn=0x0000")

    def test_reply_rejection(self):
        self.assertIsNone(protocol.parse_discovery_reply(b"noise"))
        self.assertIsNone(protocol.parse_discovery_reply(b"ECloudBox516:x:PORT:1:2"))


class MessageTests(unittest.TestCase):
    def test_lines(self):
        self.assertEqual(protocol.probe_message(), "sayHello\r\nTVunboxed\r\n\r\n")
        self.assertEqual(protocol.identify_message("M"), "getFeatures\r\nM\r\n\r\n")

    def test_handshake(self):
        msg = protocol.handshake_message("/", 1, "marker")
        self.assertEqual(msg, "auth\r\n/ 1 marker\r\n\r\n")

    def test_key_variants(self):
        self.assertEqual(protocol.key(24), "KEYEVENT\r\n24\r\n\r\n")
        self.assertEqual(protocol.key(26, long_press=True),
                         "KEYEVENT\r\n26\r\nlongpress\r\n\r\n")

    def test_volume_clamp(self):
        self.assertIn("setVolume 100", protocol.volume(999))
        self.assertIn("setVolume 0", protocol.volume(-5))

    def test_launch_validation(self):
        self.assertEqual(protocol.launch(" com.x "), "STARTAPPEVNET\r\ncom.x\r\n\r\n")
        with self.assertRaises(ValueError):
            protocol.launch("   ")


class SessionTests(unittest.TestCase):
    def test_session_round_trip(self):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.bind(("127.0.0.1", 0))
        srv.listen(1)
        port = srv.getsockname()[1]
        seen = []

        def serve():
            conn, _ = srv.accept()
            conn.settimeout(1.5)
            buf = b""
            end = time.monotonic() + 2.0
            while time.monotonic() < end:
                try:
                    chunk = conn.recv(1024)
                except socket.timeout:
                    break
                if not chunk:
                    break
                buf += chunk
                while b"\r\n\r\n" in buf:
                    record, buf = buf.split(b"\r\n\r\n", 1)
                    seen.append(record.decode())
                    if record.startswith(b"getFeatures"):
                        conn.sendall(b"server_features:sn=0x0000 v=91 m=T ap=the TV control service\r\n\r\n")
            conn.close()
            srv.close()

        threading.Thread(target=serve, daemon=True).start()
        session = Session("127.0.0.1", port)
        reply = session.send(protocol.identify_message(), wait_reply=True)
        session.send(protocol.key(24))
        session.close()
        time.sleep(0.1)

        self.assertIn("server_features", reply)
        transcript = "\n".join(seen)
        self.assertIn("sayHello", transcript)
        self.assertIn("auth", transcript)
        self.assertIn("KEYEVENT\r\n24", transcript)

    def test_collect_once_collects_batch_reply(self):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.bind(("127.0.0.1", 0))
        srv.listen(1)
        port = srv.getsockname()[1]
        from tvunboxed.client import collect_once

        def serve():
            conn, _ = srv.accept()
            conn.settimeout(2.0)
            buf = b""
            end = time.monotonic() + 2.0
            got = 0
            while time.monotonic() < end:
                try:
                    chunk = conn.recv(1024)
                except socket.timeout:
                    break
                if not chunk:
                    break
                buf += chunk
                if buf.count(b"\r\n\r\n") >= 4:
                    break
            conn.sendall(b"CurrentVolume:30/100\r\n")
            conn.close()
            srv.close()

        threading.Thread(target=serve, daemon=True).start()
        out = collect_once("127.0.0.1", port,
                           ["MediaControl\r\ngetVolume\r\n\r\n",
                            "MediaControl\r\ngetMediaDuration\r\n\r\n"],
                           wait=1.0)
        self.assertIn("CurrentVolume:30/100", out)


class AdvancedProtocolTests(unittest.TestCase):
    def test_builders(self):
        self.assertEqual(protocol.airmouse(3, -4, 1), "AIRMOUSEEVNET\r\n3.0\r\n-4.0\r\n1\r\n\r\n")
        self.assertEqual(protocol.mouse_enable(True), "MOUSEENABLEEVENT\r\n1\r\n\r\n")
        self.assertEqual(protocol.mouse_enable(False), "MOUSEENABLEEVENT\r\n0\r\n\r\n")
        self.assertEqual(protocol.airclick(), "AIRCLICKEVENT\r\n1\r\n\r\n")
        self.assertEqual(protocol.type_text("hi"), "CONTENT\r\n1\r\n0 hi\r\n\r\n")
        self.assertEqual(protocol.uninstall_app("com.x"), "uninstallApp\r\ncom.x\r\n\r\n")
        self.assertEqual(protocol.start_browser(), "STARTBROWSEREVENT\r\n1\r\n\r\n")
        self.assertEqual(protocol.open_link("https://example.com"), "OpenLink\r\nhttps://example.com\r\n\r\n")
        self.assertEqual(protocol.media_control("getCurrentPosition"), "MediaControl\r\ngetCurrentPosition\r\n\r\n")
        self.assertEqual(protocol.media_control("setVolume 30"), "MediaControl\r\nsetVolume 30\r\n\r\n")
        self.assertEqual(protocol.media_query("getCurrentPosition"),
                         "MediaControl\r\ngetCurrentPosition\r\n\r\n")
        with self.assertRaises(ValueError):
            protocol.media_query("setVolume 99")
        self.assertEqual(protocol.media_seek(1500), "MediaControl\r\nseekTo 1500\r\n\r\n")
        with self.assertRaises(ValueError):
            protocol.media_seek(-1)
        self.assertEqual(protocol.parse_media_int("CurrentPosition:12345", "CurrentPosition"), 12345)
        self.assertEqual(protocol.parse_media_int("MediaDuration=8000", "MediaDuration"), 8000)
        self.assertEqual(protocol.parse_media_int("CurrentVolume:21/100", "CurrentVolume"), 21)
        self.assertIsNone(protocol.parse_media_int("", "CurrentPosition"))

    def test_type_text_rejects_newlines(self):
        with self.assertRaises(ValueError):
            protocol.type_text("a\nb")

    def test_uninstall_validates(self):
        with self.assertRaises(ValueError):
            protocol.uninstall_app("")
        with self.assertRaises(ValueError):
            protocol.uninstall_app("a\r\nb")

    def test_open_link_and_media_validate(self):
        with self.assertRaises(ValueError):
            protocol.open_link("")
        with self.assertRaises(ValueError):
            protocol.open_link("a\nb")
        with self.assertRaises(ValueError):
            protocol.media_control("")
        with self.assertRaises(ValueError):
            protocol.media_control("bad\r\nx")


class WebDavParseTests(unittest.TestCase):
    FIXTURE = b"""<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
 <D:response><D:href>/movies/</D:href><D:propstat><D:prop>
  <D:resourcetype><D:collection/></D:resourcetype><D:displayname>movies</D:displayname>
 </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
 <D:response><D:href>/movies/clip.mkv</D:href><D:propstat><D:prop>
  <D:resourcetype/><D:getcontentlength>734003200</D:getcontentlength>
  <D:getlastmodified>Sun, 15 Nov 2026 10:00:00 GMT</D:getlastmodified>
  <D:getetag>"abc"</D:getetag><D:displayname>clip.mkv</D:displayname>
  <D:quota-available-bytes>5000000000</D:quota-available-bytes><D:quota-used-bytes>8000000000</D:quota-used-bytes>
 </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
</D:multistatus>"""

    def test_parse_multistatus(self):
        from tvunboxed import tvdav
        result = tvdav._parse_multistatus(self.FIXTURE, "/movies/")
        # the requested root collection itself is filtered out
        self.assertEqual([e.name for e in result.entries], ["clip.mkv"])
        self.assertEqual(result.entries[0].size, 734003200)
        self.assertEqual(result.quota_available, 5000000000)
        self.assertEqual(result.quota_used, 8000000000)


if __name__ == "__main__":
    unittest.main()

class ShareTests(unittest.TestCase):
    def test_open_file_local(self):
        from tvunboxed import share
        msg = share.open_file_message("files/My Vid.mkv")
        self.assertTrue(msg.startswith("Openfile\r\nOpen files/My%20Vid.mkv "))
        self.assertIn("matroska", msg)

    def test_open_file_url_passthrough(self):
        from tvunboxed import share
        msg = share.open_file_message("https://example.com/x.m3u8")
        self.assertIn("https://example.com/x.m3u8", msg)

    def test_save_file(self):
        from tvunboxed import share
        msg = share.save_file_message("files/My Pic.png")
        self.assertEqual(msg, "Savefile\r\nSave files/My%20Pic.png image/png\r\n\r\n")

    def test_normalize_relpath_blocks_escape(self):
        from tvunboxed import share
        self.assertEqual(share.normalize_relpath("./a//b"), "a/b")
        with self.assertRaises(ValueError):
            share.normalize_relpath("../etc/passwd")
        with self.assertRaises(ValueError):
            share.normalize_relpath("a/../../b")

    def test_open_file_url_fixups(self):
        from tvunboxed import share
        # protocol-relative URL is promoted to https:// (TV treats it as host-relative)
        self.assertIn("https://cdn.example.com/v.mp4", share.open_file_message("//cdn.example.com/v.mp4"))
        # bare host + path (no scheme) is promoted too (Big Buck Bunny log case)
        self.assertIn("https://cdn.example.com/v.mp4", share.open_file_message("cdn.example.com/v.mp4"))
        # local share paths are still encode_path'd
        self.assertIn("x%20y.mp4", share.open_file_message("x y.mp4"))

    def test_list_share(self):
        import os, tempfile
        from tvunboxed import share
        with tempfile.TemporaryDirectory() as td:
            os.mkdir(os.path.join(td, "sub"))
            with open(os.path.join(td, "b.txt"), "w") as _f:
                _f.write("x")
            with open(os.path.join(td, "a.txt"), "w") as _f:
                _f.write("x")
            out = share.list_share(td)
            self.assertEqual(out["dir"], ".")
            self.assertEqual([e["name"] for e in out["entries"]], ["sub", "a.txt", "b.txt"])
            self.assertEqual(share.list_share(td, "sub")["dir"], "sub")

class TvAppsParseTests(unittest.TestCase):
    def _build(self, records):
        body = b""
        for name, pkg, ver, vcode, icon in records:
            body += struct.pack("<I", len(name)) + name
            body += struct.pack("<I", len(pkg)) + pkg
            body += struct.pack("<I", len(ver)) + ver
            body += struct.pack("<II", 4, vcode)
            body += struct.pack("<I", len(icon)) + icon
        return b"\x4e\x55\x02\x00" + body

    def test_parse_matches_live_layout(self):
        from tvunboxed import tvapps
        blob = self._build([
            ("YouTube".encode(), b"com.google.android.youtube.tv", b"5.30.340", 530340320, b"\x89PNGicon"),
            ("VLC".encode(), b"org.videolan.vlc", b"3.7.2", 0, b""),
        ])
        apps = tvapps.parse_app_blob(blob)
        self.assertEqual([a["name"] for a in apps], ["YouTube", "VLC"])
        self.assertEqual(apps[0]["package"], "com.google.android.youtube.tv")
        self.assertEqual(apps[0]["versionCode"], 530340320)
        self.assertTrue(apps[0]["icon"].startswith("data:image/png;base64,"))
        self.assertEqual(apps[0]["icon"], "data:image/png;base64," + base64.b64encode(b"\x89PNGicon").decode())

    def test_truncated_blob_stops_cleanly(self):
        from tvunboxed import tvapps
        blob = self._build([("A".encode(), b"com.a", b"1", 1, b"icon")])
        self.assertEqual(len(tvapps.parse_app_blob(blob)), 1)
        self.assertEqual(tvapps.parse_app_blob(blob[:-2]), [])
