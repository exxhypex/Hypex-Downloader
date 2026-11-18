"""
Post-processing and media remux options for yt-dlp & FFmpeg
"""
from typing import Dict, Any, Optional

class PostProcessorManager:
    @staticmethod
    def get_postprocessors(
        output_format: str = "mp4",
        extract_audio: bool = False,
        embed_subs: bool = False,
        embed_metadata: bool = True
    ) -> list:
        postprocessors = []

        if extract_audio:
            postprocessors.append({
                "key": "FFmpegExtractAudio",
                "preferredcodec": output_format if output_format in ["mp3", "m4a", "opus", "wav"] else "mp3",
                "preferredquality": "192",
            })
        elif output_format.lower() in ["mp4", "mkv", "webm"]:
            postprocessors.append({
                "key": "FFmpegVideoRemuxer",
                "preferedformat": output_format.lower()
            })

        if embed_subs:
            postprocessors.append({
                "key": "FFmpegEmbedSubtitle"
            })

        if embed_metadata:
            postprocessors.append({
                "key": "FFmpegMetadata",
                "add_chapters": True,
                "add_metadata": True,
            })

        return postprocessors
