"""
Unit tests for HYPEX Downloader Python Engine
"""
import unittest
import json
import sys
import os

# Add package to path
sys.path.insert(0, os.path.abspath("app/src/main/python"))

from downloader.formats import FormatManager
from downloader.cancellation import CancellationController
from downloader.cookies import CookieManager
from downloader.errors import classify_error, NetworkError, VideoUnavailableError
from downloader.playlists import PlaylistExtractor
from downloader.progress import ProgressHookHandler

class TestHypexEngine(unittest.TestCase):
    def test_cancellation(self):
        controller = CancellationController()
        controller.request_cancellation("task_123")
        self.assertTrue(controller.is_cancelled("task_123"))
        self.assertFalse(controller.is_cancelled("task_456"))
        controller.clear("task_123")
        self.assertFalse(controller.is_cancelled("task_123"))

    def test_format_selector_generation(self):
        self.assertEqual(FormatManager.build_format_selector("Best Quality"), "bestvideo+bestaudio/best")
        self.assertIn("1080", FormatManager.build_format_selector("1080p MP4"))
        self.assertIn("720", FormatManager.build_format_selector("720p Balanced"))
        self.assertIn("480", FormatManager.build_format_selector("Data Saver"))
        self.assertIn("bestaudio", FormatManager.build_format_selector("Audio MP3"))

    def test_error_classification(self):
        err1 = classify_error(Exception("Connection timed out after 30000ms"))
        self.assertEqual(err1.code, "NETWORK_ERROR")

        err2 = classify_error(Exception("This video is unavailable"))
        self.assertEqual(err2.code, "VIDEO_UNAVAILABLE")

        err3 = classify_error(Exception("Sign in to confirm your age"))
        self.assertEqual(err3.code, "AGE_RESTRICTED")

    def test_cookie_validation(self):
        cm = CookieManager("/tmp/hypex_test")
        invalid = "invalid cookie format"
        self.assertFalse(cm.validate_netscape_format(invalid))

        valid = "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t2147483647\tSID\tvalue123"
        self.assertTrue(cm.validate_netscape_format(valid))

    def test_playlist_parsing(self):
        mock_info = {
            "id": "PL123",
            "title": "Cool Playlist",
            "entries": [
                {"id": "v1", "title": "Video 1", "duration": 120},
                {"id": "v2", "title": "Video 2", "duration": 340}
            ]
        }
        parsed = PlaylistExtractor.parse_playlist(mock_info)
        self.assertEqual(parsed["playlist_id"], "PL123")
        self.assertEqual(parsed["video_count"], 2)
        self.assertEqual(parsed["items"][0]["video_id"], "v1")

if __name__ == '__main__':
    unittest.main()
