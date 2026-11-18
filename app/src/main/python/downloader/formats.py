"""
Format parsing, filtering, and preset generator for HYPEX Engine.
The UI is deliberately capped to keep large format catalogs responsive.
"""
from typing import List, Dict, Any, Optional


class FormatManager:
    MAX_VISIBLE_FORMATS = 60

    @staticmethod
    def parse_formats(formats: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        parsed = []
        for f in formats:
            format_id = str(f.get("format_id", ""))
            ext = f.get("ext", "mp4")
            resolution = f.get("resolution") or (
                f"{f.get('width')}x{f.get('height')}"
                if f.get("width") and f.get("height") else None
            )
            vcodec = f.get("vcodec", "none")
            acodec = f.get("acodec", "none")
            filesize = f.get("filesize") or f.get("filesize_approx") or 0
            fps = f.get("fps")
            tbr = f.get("tbr")
            asr = f.get("asr")
            format_note = f.get("format_note", "")

            is_video = vcodec != "none" and vcodec is not None
            is_audio = acodec != "none" and acodec is not None
            if ext in ("mhtml", "webp") and not is_video:
                continue

            parsed.append({
                "format_id": format_id,
                "ext": ext,
                "resolution": resolution or ("Audio only" if is_audio and not is_video else "Unknown"),
                "vcodec": vcodec if is_video else "none",
                "acodec": acodec if is_audio else "none",
                "filesize": filesize,
                "fps": fps,
                "tbr": round(tbr, 1) if tbr else None,
                "asr": asr,
                "format_note": format_note,
                "is_video_only": is_video and not is_audio,
                "is_audio_only": is_audio and not is_video,
                "is_muxed": is_video and is_audio,
                "height": f.get("height", 0) or 0,
                "width": f.get("width", 0) or 0,
            })

        # Prefer ready-to-play muxed formats: they do not need an FFmpeg merge step.
        parsed.sort(
            key=lambda item: (
                item["is_muxed"],
                item["height"],
                item["tbr"] if item["tbr"] else 0
            ),
            reverse=True
        )
        return parsed[:FormatManager.MAX_VISIBLE_FORMATS]

    @staticmethod
    def build_format_selector(preset_name: str, custom_format_id: Optional[str] = None) -> str:
        if custom_format_id:
            return custom_format_id

        name_lower = preset_name.lower()
        if "best" in name_lower:
            return "best[ext=mp4]/best"
        elif "2160" in name_lower or "4k" in name_lower:
            return "best[height<=2160][ext=mp4]/best[height<=2160]/best"
        elif "1440" in name_lower or "2k" in name_lower:
            return "best[height<=1440][ext=mp4]/best[height<=1440]/best"
        elif "1080" in name_lower:
            return "best[height<=1080][ext=mp4]/best[height<=1080]/best"
        elif "720" in name_lower:
            return "best[height<=720][ext=mp4]/best[height<=720]/best"
        elif "480" in name_lower or "data saver" in name_lower:
            return "best[height<=480][ext=mp4]/best[height<=480]/best"
        elif "mp3" in name_lower:
            return "bestaudio[ext=m4a]/bestaudio/best"
        elif "m4a" in name_lower or "audio" in name_lower:
            return "bestaudio[ext=m4a]/bestaudio/best"
        return "best[ext=mp4]/best"
