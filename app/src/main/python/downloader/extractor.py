"""
Media metadata extraction logic using yt-dlp API
"""
from typing import Dict, Any, Optional
from .formats import FormatManager
from .subtitles import SubtitleManager
from .playlists import PlaylistExtractor
from .errors import classify_error

class MediaExtractor:
    @staticmethod
    def parse_media_info(info_dict: Dict[str, Any]) -> Dict[str, Any]:
        try:
            # Check if this is a playlist
            if info_dict.get("_type") == "playlist" or "entries" in info_dict:
                return {
                    "is_playlist": True,
                    "playlist": PlaylistExtractor.parse_playlist(info_dict)
                }

            media_id = str(info_dict.get("id", ""))
            title = info_dict.get("title") or "Unknown Title"
            author = info_dict.get("uploader") or info_dict.get("channel") or info_dict.get("creator") or "Unknown Author"
            duration = int(info_dict.get("duration", 0) or 0)
            thumbnail = info_dict.get("thumbnail") or ""
            if not thumbnail and info_dict.get("thumbnails"):
                thumbnail = info_dict["thumbnails"][-1].get("url", "")
            
            view_count = info_dict.get("view_count")
            upload_date = info_dict.get("upload_date")
            description = info_dict.get("description", "")
            if description and len(description) > 300:
                description = description[:300] + "..."

            raw_formats = info_dict.get("formats", [])
            formats = FormatManager.parse_formats(raw_formats)
            subtitles = SubtitleManager.parse_subtitles(info_dict)

            return {
                "is_playlist": False,
                "id": media_id,
                "title": title,
                "author": author,
                "duration": duration,
                "thumbnail": thumbnail,
                "view_count": view_count,
                "upload_date": upload_date,
                "description": description,
                "webpage_url": info_dict.get("webpage_url", ""),
                "formats": formats,
                "subtitles": subtitles
            }
        except Exception as e:
            raise classify_error(e)
