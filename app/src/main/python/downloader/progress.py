"""
Progress hook handler for yt-dlp callbacks
"""
import time
from typing import Callable, Optional
from .cancellation import CancellationController

class ProgressHookHandler:
    def __init__(self, task_id: str, callback: Optional[Callable[[dict], None]] = None):
        self.task_id = str(task_id)
        self.callback = callback
        self.last_emit_time = 0.0
        self.cancellation = CancellationController()

    def __call__(self, d: dict):
        # Check cancellation
        if self.cancellation.is_cancelled(self.task_id):
            raise Exception("DOWNLOAD_CANCELLED")

        now = time.time()
        status = d.get("status", "downloading")

        # Throttle progress events to at most once every 300ms, except finished/error
        if status != "finished" and (now - self.last_emit_time) < 0.3:
            return

        self.last_emit_time = now

        downloaded = d.get("downloaded_bytes", 0) or 0
        total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
        speed = d.get("speed", 0) or 0
        eta = d.get("eta", 0) or 0
        filename = d.get("filename", "")

        progress_pct = 0.0
        if total > 0:
            progress_pct = min(100.0, max(0.0, (downloaded / total) * 100.0))

        payload = {
            "task_id": self.task_id,
            "status": status,
            "downloaded_bytes": downloaded,
            "total_bytes": total,
            "speed": speed,
            "eta": eta,
            "progress": round(progress_pct, 1),
            "filename": filename
        }

        if self.callback:
            try:
                self.callback(payload)
            except Exception:
                pass
