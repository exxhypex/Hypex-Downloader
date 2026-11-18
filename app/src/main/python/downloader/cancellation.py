"""
Thread-safe cancellation hooks for active yt-dlp tasks
"""
import threading

class CancellationController:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(CancellationController, cls).__new__(cls)
                cls._instance._cancelled_tasks = set()
                cls._instance._task_lock = threading.Lock()
        return cls._instance

    def request_cancellation(self, task_id: str):
        with self._task_lock:
            self._cancelled_tasks.add(str(task_id))

    def is_cancelled(self, task_id: str) -> bool:
        with self._task_lock:
            return str(task_id) in self._cancelled_tasks

    def clear(self, task_id: str):
        with self._task_lock:
            self._cancelled_tasks.discard(str(task_id))
