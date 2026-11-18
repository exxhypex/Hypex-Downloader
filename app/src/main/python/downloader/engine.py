"""
Core HYPEX Downloader Engine Singleton
Orchestrates yt-dlp initialization, media extraction, and downloads.
"""
import os
import json
import threading
from typing import Dict, Any, Optional, Callable

from .extractor import MediaExtractor
from .formats import FormatManager
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
        self.cookie_manager = CookieManager(storage_dir)
        self.cancellation = CancellationController()
        self._ydl_module = None
        self._version = "2026.03.01"
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
            "engine_name": "HYPEX yt-dlp Native Engine",
            "version": self.get_version(),
            "ydl_available": ydl is not None,
            "cookies_loaded": self.cookie_manager.has_cookies(),
            "storage_path": self.storage_dir,
            "active_tasks": len(self.cancellation._cancelled_tasks)
        }

    def _build_options(
        self,
        task_id: Optional[str] = None,
        format_selector: str = "bestvideo+bestaudio/best",
        output_template: Optional[str] = None,
        progress_callback: Optional[Callable[[dict], None]] = None,
        is_extract_only: bool = False
    ) -> Dict[str, Any]:
        options = {
            "quiet": True,
            "no_warnings": True,
            "extract_flat": False,
            "ignoreerrors": True,
            "continuedl": True,
            "nocheckcertificate": True,
            "format": format_selector,
        }

        if self.cookie_manager.has_cookies():
            options["cookiefile"] = self.cookie_manager.get_cookie_file_path()

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
        """
        Extract media info and return as structured JSON string
        """
        try:
            ydl_class = self._get_ydl_class()
            if ydl_class is None:
                raise Exception("yt-dlp runtime is not initialized")

            options = self._build_options(is_extract_only=True)
            with ydl_class(options) as ydl:
                info = ydl.extract_info(url, download=False)
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
        """
        Download media with live progress hook and cancellation support
        """
        try:
            self.cancellation.clear(task_id)
            ydl_class = self._get_ydl_class()
            if ydl_class is None:
                raise Exception("yt-dlp runtime is not initialized")

            options = self._build_options(
                task_id=task_id,
                format_selector=format_selector,
                output_template=output_template,
                progress_callback=progress_callback,
                is_extract_only=False
            )

            with ydl_class(options) as ydl:
                info = ydl.extract_info(url, download=True)
                filename = ydl.prepare_filename(info) if info else ""
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
