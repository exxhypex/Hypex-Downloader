"""
Core HYPEX Downloader Engine Singleton
Orchestrates yt-dlp initialization, media extraction, and downloads.
"""
import os
import json
import threading
from typing import Dict, Any, Optional, Callable

from .extractor import MediaExtractor
from .cancellation import CancellationController
from .progress import ProgressHookHandler
from .cookies import CookieManager
from .errors import classify_error, DownloadCancelledError


class HypexEngine:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, *args, **kwargs):
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(HypexEngine, cls).__new__(cls)
                cls._instance._initialized = False
        return cls._instance

    def __init__(self, storage_dir: str = "/data/data/com.hypex.downloader/files"):
        if getattr(self, "_initialized", False):
            return

        self.storage_dir = storage_dir
        os.makedirs(storage_dir, exist_ok=True)
        self.cookie_manager = CookieManager(storage_dir)
        self.cancellation = CancellationController()
        self._ydl_module = None
        self._version = "unavailable"
        self._initialized = True

    def _get_ydl_class(self):
        if self._ydl_module is None:
            try:
                import yt_dlp
                self._ydl_module = yt_dlp.YoutubeDL
                self._version = getattr(yt_dlp, "__version__", self._version)
            except ImportError:
                self._ydl_module = None
        return self._ydl_module

    def get_version(self) -> str:
        self._get_ydl_class()
        return self._version

    def get_diagnostics(self) -> Dict[str, Any]:
        ydl = self._get_ydl_class()
        return {
            "engine_name": "HYPEX embedded yt-dlp",
            "version": self.get_version(),
            "ydl_available": ydl is not None,
            "cookies_loaded": self.cookie_manager.has_cookies(),
            "storage_path": self.storage_dir,
            "active_tasks": len(self.cancellation._cancelled_tasks)
        }

    def _build_options(
        self,
        task_id: Optional[str] = None,
        format_selector: str = "best[ext=mp4]/best",
        output_template: Optional[str] = None,
        progress_callback: Optional[Callable[[dict], None]] = None,
        is_extract_only: bool = False
    ) -> Dict[str, Any]:
        options = {
            "quiet": True,
            "no_warnings": True,
            "extract_flat": False,
            "ignoreerrors": False,
            "continuedl": True,
            "nocheckcertificate": False,
            "socket_timeout": 30,
            "retries": 3,
            "fragment_retries": 3,
            "extractor_retries": 2,
            "concurrent_fragment_downloads": 2,
            "format": format_selector,
        }

        cookie_path = self.cookie_manager.get_cookie_file_path()
        if cookie_path:
            options["cookiefile"] = cookie_path

        if is_extract_only:
            options["extract_flat"] = "in_playlist"
            options["skip_download"] = True
            return options

        if output_template:
            options["outtmpl"] = output_template

        if task_id and progress_callback:
            hook = ProgressHookHandler(task_id, progress_callback)
            options["progress_hooks"] = [hook]

        return options

    def extract_info(self, url: str) -> str:
        try:
            ydl_class = self._get_ydl_class()
            if ydl_class is None:
                raise RuntimeError("Bundled yt-dlp runtime is unavailable")

            options = self._build_options(is_extract_only=True)
            with ydl_class(options) as ydl:
                info = ydl.extract_info(url, download=False)
                if info is None:
                    raise RuntimeError("No media information was returned by yt-dlp")
                parsed = MediaExtractor.parse_media_info(info)
                return json.dumps({"success": True, "data": parsed})
        except Exception as e:
            err = classify_error(e)
            return json.dumps({"success": False, "error": err.to_dict()})

    def download_media(
        self,
        task_id: str,
        url: str,
        format_selector: str,
        output_template: str,
        progress_callback: Optional[Callable[[dict], None]] = None
    ) -> str:
        try:
            self.cancellation.clear(task_id)
            ydl_class = self._get_ydl_class()
            if ydl_class is None:
                raise RuntimeError("Bundled yt-dlp runtime is unavailable")

            options = self._build_options(
                task_id=task_id,
                format_selector=format_selector or "best[ext=mp4]/best",
                output_template=output_template,
                progress_callback=progress_callback,
                is_extract_only=False
            )

            with ydl_class(options) as ydl:
                info = ydl.extract_info(url, download=True)
                if info is None:
                    raise RuntimeError("yt-dlp completed without returning media metadata")
                filename = info.get("filepath") or info.get("_filename") or ydl.prepare_filename(info)
                return json.dumps({
                    "success": True,
                    "task_id": task_id,
                    "output_file": filename
                })
        except Exception as e:
            if "DOWNLOAD_CANCELLED" in str(e):
                err = DownloadCancelledError()
            else:
                err = classify_error(e)
            return json.dumps({"success": False, "task_id": task_id, "error": err.to_dict()})
        finally:
            self.cancellation.clear(task_id)

    def cancel_task(self, task_id: str):
        self.cancellation.request_cancellation(task_id)
