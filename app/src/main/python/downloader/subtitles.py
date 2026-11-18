"""
Subtitle detection and management for HYPEX Engine
"""
from typing import Dict, Any, List

class SubtitleManager:
    @staticmethod
    def parse_subtitles(info_dict: Dict[str, Any]) -> List[Dict[str, Any]]:
        results = []
        subs = info_dict.get("subtitles") or {}
        auto_subs = info_dict.get("automatic_captions") or {}

        for lang, formats in subs.items():
            exts = [f.get("ext", "vtt") for f in formats if isinstance(f, dict)]
            results.append({
                "language": lang,
                "is_auto": False,
                "formats": list(set(exts)),
                "name": lang
            })

        for lang, formats in auto_subs.items():
            exts = [f.get("ext", "vtt") for f in formats if isinstance(f, dict)]
            results.append({
                "language": lang,
                "is_auto": True,
                "formats": list(set(exts)),
                "name": f"{lang} (auto-generated)"
            })

        return results
