"""
HYPEX Downloader Engine
yt-dlp Python integration package for Android
"""

__version__ = "2026.03.01"
__engine__ = "HYPEX yt-dlp Engine"

from .engine import HypexEngine
from .extractor import MediaExtractor
from .formats import FormatManager
from .playlists import PlaylistExtractor
from .progress import ProgressHookHandler
from .cookies import CookieManager
from .subtitles import SubtitleManager
from .cancellation import CancellationController
from .errors import EngineError, classify_error

__all__ = [
    "HypexEngine",
    "MediaExtractor",
    "FormatManager",
    "PlaylistExtractor",
    "ProgressHookHandler",
    "CookieManager",
    "SubtitleManager",
    "CancellationController",
    "EngineError",
    "classify_error",
]
