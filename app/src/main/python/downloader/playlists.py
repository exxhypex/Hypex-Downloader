"""
Playlist extraction and parsing for HYPEX Engine
"""
from typing import Dict, Any, List

class PlaylistExtractor:
    @staticmethod
    def parse_playlist(info_dict: Dict[str, Any]) -> Dict[str, Any]:
        playlist_id = str(info_dict.get("id", ""))
        title = info_dict.get("title") or "Untitled Playlist"
        uploader = info_dict.get("uploader") or info_dict.get("channel") or ""
        entries = info_dict.get("entries") or []

        items = []
        for idx, entry in enumerate(entries):
            if not entry:
                continue
            items.append({
                "video_id": str(entry.get("id", f"item_{idx}")),
                "title": entry.get("title") or f"Track {idx + 1}",
                "duration": entry.get("duration", 0) or 0,
                "thumbnail": entry.get("thumbnail") or (entry.get("thumbnails", [{}])[-1].get("url") if entry.get("thumbnails") else ""),
                "uploader": entry.get("uploader") or entry.get("channel") or uploader,
                "position": idx + 1,
                "url": entry.get("webpage_url") or entry.get("url") or f"https://www.youtube.com/watch?v={entry.get('id')}",
                "selected": True
            })

        return {
            "playlist_id": playlist_id,
            "title": title,
            "uploader": uploader,
            "video_count": len(items),
            "thumbnail": info_dict.get("thumbnail") or (items[0]["thumbnail"] if items else ""),
            "items": items
        }
