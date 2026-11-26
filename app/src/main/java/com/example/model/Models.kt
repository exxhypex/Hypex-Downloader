package com.example.core.model

import java.io.File

enum class DownloadStatus {
    QUEUED,
    EXTRACTING,
    READY,
    DOWNLOADING,
    PAUSED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class FormatItem(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val vcodec: String = "none",
    val acodec: String = "none",
    val filesize: Long = 0L,
    val fps: Double? = null,
    val tbr: Double? = null,
    val asr: Int? = null,
    val formatNote: String = "",
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isMuxed: Boolean = true
) {
    val displayLabel: String
        get() = when {
            isAudioOnly -> "Audio • $ext • ${if (tbr != null) "${tbr.toInt()} kbps" else ext.uppercase()}"
            isVideoOnly -> "$resolution • Video only • $ext"
            else -> "$resolution • $ext • ${if (fps != null && fps > 30) "${fps.toInt()}fps" else ""}"
        }
}

data class SubtitleItem(
    val language: String,
    val name: String,
    val isAuto: Boolean = false,
    val formats: List<String> = emptyList()
)

data class MediaMetadata(
    val id: String,
    val title: String,
    val author: String,
    val duration: Long,
    val thumbnail: String,
    val webpageUrl: String,
    val description: String = "",
    val viewCount: Long? = null,
    val uploadDate: String? = null,
    val formats: List<FormatItem> = emptyList(),
    val subtitles: List<SubtitleItem> = emptyList(),
    val isPlaylist: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (duration <= 0) return "Live / Unknown"
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
}

data class PlaylistItem(
    val videoId: String,
    val title: String,
    val duration: Long,
    val thumbnail: String,
    val uploader: String,
    val position: Int,
    val url: String,
    val selected: Boolean = true
)

data class PlaylistInfo(
    val playlistId: String,
    val title: String,
    val uploader: String,
    val videoCount: Int,
    val thumbnail: String,
    val items: List<PlaylistItem> = emptyList()
)

data class DownloadTask(
    val id: Long = 0L,
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: Long = 0L,
    val formatId: String,
    val formatDescription: String,
    val outputPath: String,
    val temporaryPath: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: Long = 0L, // bytes per second
    val eta: Long = 0L,   // seconds
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    val formattedSpeed: String
        get() = when {
            speed <= 0 -> "--"
            speed < 1024 * 1024 -> String.format("%.1f KB/s", speed / 1024f)
            else -> String.format("%.2f MB/s", speed / (1024f * 1024f))
        }

    val formattedDownloaded: String
        get() = when {
            downloadedBytes <= 0 -> "0 B"
            downloadedBytes < 1024 * 1024 -> String.format("%.1f KB", downloadedBytes / 1024f)
            else -> String.format("%.2f MB", downloadedBytes / (1024f * 1024f))
        }

    val formattedTotal: String
        get() = when {
            totalBytes <= 0 -> "Unknown size"
            totalBytes < 1024 * 1024 -> String.format("%.1f KB", totalBytes / 1024f)
            else -> String.format("%.2f MB", totalBytes / (1024f * 1024f))
        }

    val formattedEta: String
        get() = when {
            eta <= 0 -> "--"
            eta < 60 -> "${eta}s"
            eta < 3600 -> "${eta / 60}m ${eta % 60}s"
            else -> "${eta / 3600}h ${(eta % 3600) / 60}m"
        }
}

data class PresetItem(
    val id: String,
    val name: String,
    val formatSelector: String,
    val container: String = "mp4",
    val isAudioOnly: Boolean = false,
    val description: String = ""
)

data class DownloadProgress(
    val taskId: Long,
    val status: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long,
    val eta: Long,
    val filename: String = ""
)
