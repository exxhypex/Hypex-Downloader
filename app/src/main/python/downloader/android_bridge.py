"""Chaquopy entry points used by the Kotlin engine bridge."""
import json
import os
from .engine import HypexEngine

_engine = None

def initialize(storage_dir):
    global _engine
    os.makedirs(storage_dir, exist_ok=True)
    _engine = HypexEngine(storage_dir)
    return True

def _get_engine():
    if _engine is None:
        raise RuntimeError("HYPEX Python engine has not been initialized")
    return _engine

def get_version():
    return _get_engine().get_version()

def get_diagnostics():
    return json.dumps(_get_engine().get_diagnostics())

def extract_info(url):
    return _get_engine().extract_info(url)

def extract_playlist(url):
    return _get_engine().extract_info(url)

class _ProgressCallback:
    def __init__(self, target):
        self.target = target

    def __call__(self, payload):
        self.target.emitProgress(json.dumps(payload))

def download_media(task_id, url, format_selector, output_template, callback):
    engine = _get_engine()
    progress_callback = _ProgressCallback(callback) if callback is not None else None
    return engine.download_media(
        str(task_id),
        url,
        format_selector,
        output_template,
        progress_callback
    )

def cancel_task(task_id):
    _get_engine().cancel_task(str(task_id))
    return True
