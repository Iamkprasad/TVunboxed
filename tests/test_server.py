"""Regression: POST handlers that emit errors must not write a second response."""

import types
import unittest

from tvunboxed.server import RemoteHandler


class Probe(RemoteHandler):
    def __init__(self, body, server=None):
        self.path = "/api/care/cleanup"
        self.body = body
        self.responses = []
        self.server = server or types.SimpleNamespace(care_token=None, care_paths=[])

    def _read_body(self):
        return self.body

    def _send_json(self, obj, status=200):
        self._response_sent = True
        self.responses.append((obj, status))


class PostResponseTests(unittest.TestCase):
    def test_error_is_sent_once(self):
        handler = Probe({"paths": ["/system/app/x.apk"], "confirm": "wrong"})
        handler._do_POST_route()
        self.assertEqual(len(handler.responses), 1)
        self.assertEqual(handler.responses[0][1], 400)

    def test_invalid_paths_sent_once(self):
        server = types.SimpleNamespace(care_token="abc", care_paths=[])
        handler = Probe({"paths": [], "confirm": "abc"}, server)
        handler._do_POST_route()
        self.assertEqual(len(handler.responses), 1)
        self.assertEqual(handler.responses[0][1], 400)


if __name__ == "__main__":
    unittest.main()
