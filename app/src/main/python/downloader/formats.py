"""
Format parsing, filtering, and preset generator for HYPEX Engine
"""
from typing import List, Dict, Any, Optional

class FormatManager:
    @staticmethod
    def parse_formats(formats: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        parsed = []
        for f in formats:
            format_id = str(f.get("format_id", ""))
            ext = f.get("ext", "mp4")
            resolution = f.get("resolution") or (f"{f.get('width')}x{f.get('height')}" if f.get("width") and f.get("height") else None)
            vcodec = f.get("vcodec", "none")
            acodec = f.get("acodec", "none")
            filesize = f.get("filesize") or f.get("filesize_approx") or 0
            fps = f.get("fps")
            tbr = f.get("tbr")
            asr = f.get("asr")
            format_note = f.get("format_note", "")

            is_video = vcodec != "none" and vcodec is not None
            is_audio = acodec != "none" and acodec is not None

            # Skip storyboards or mhtml
            if ext in ["mhtml", "webp"] and not is_video:
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
        
        # Sort formats: highest resolution first, then highest bitrate
        parsed.sort(key=lambda x: (x["height"] if x["height"] else 0, x["tbr"] if x["tbr"] else 0), reverse=True)
        return parsed

    @staticmethod
    def build_format_selector(preset_name: str, custom_format_id: Optional[str] = None) -> str:
        if custom_format_id:
            return f"{custom_format_id}+bestaudio/best"
        
        name_lower = preset_name.lower()
        if "best" in name_lower:
            return "bestvideo+bestaudio/best"
        elif "2160" in name_lower or "4k" in name_lower:
            return "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
        elif "1440" in name_lower or "2k" in name_lower:
            return "bestvideo[height<=1440]+bestaudio/best[height<=1440]"
        elif "1080" in name_lower:
            return "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]"
        elif "720" in name_lower:
            return "bestvideo[height<=720]+bestaudio/best[height<=720]"
        elif "480" in name_lower or "data saver" in name_lower:
            return "bestvideo[height<=480]+bestaudio/best[height<=480]"
        elif "mp3" in name_lower:
            return "bestaudio/best"
        elif "m4a" in name_lower or "audio" in name_lower:
            return "bestaudio[ext=m4a]/bestaudio/best"
        else:
            return "bestvideo+bestaudio/best"
