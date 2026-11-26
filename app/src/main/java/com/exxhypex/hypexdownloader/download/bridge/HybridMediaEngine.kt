package com.exxhypex.hypexdownloader.engine.bridge

import android.content.Context
import android.net.Uri
import com.exxhypex.hypexdownloader.core.model.DownloadProgress
import com.exxhypex.hypexdownloader.core.model.DownloadTask
import com.exxhypex.hypexdownloader.core.model.FormatItem
import com.exxhypex.hypexdownloader.core.model.MediaMetadata
import com.exxhypex.hypexdownloader.core.model.PlaylistInfo
import com.exxhypex.hypexdownloader.engine.network.NativeMediaEngine
import java.util.Locale

/**
 * Routes direct media URLs to the lightweight OkHttp engine and supported sites
 * to the embedded yt-dlp runtime.
 */
class HybridMediaEngine(context: Context) : EngineBridge {
    private val native = NativeMediaEngine(context)
    private val ytdlp = PythonYtDlpEngine(context)

    override suspend fun initializeEngine(): Boolean {
        // Prepare only the lightweight native paths during app startup.
        // Chaquopy starts on the first website extraction/download, never on the home screen.
        return native.initializeEngine()
    }

    override fun getEngineVersion(): String = ytdlp.getEngineVersion()

    override suspend fun extractMedia(url: String): Result<MediaMetadata> =
        if (isDirectMediaUrl(url)) native.extractMedia(url) else ytdlp.extractMedia(url)

    override suspend fun extractPlaylist(url: String): Result<PlaylistInfo> = ytdlp.extractPlaylist(url)

    override suspend fun getFormats(url: String): Result<List<FormatItem>> =
        if (isDirectMediaUrl(url)) native.getFormats(url) else ytdlp.getFormats(url)

    override suspend fun startDownload(
        task: DownloadTask,
        onProgress: (DownloadProgress) -> Unit
    ) = if (isDirectMediaUrl(task.url)) {
        native.startDownload(task, onProgress)
    } else {
        ytdlp.startDownload(task, onProgress)
    }

    override fun pauseDownload(taskId: Long) {
        native.pauseDownload(taskId)
        ytdlp.pauseDownload(taskId)
    }

    override fun cancelDownload(taskId: Long) {
        native.cancelDownload(taskId)
        ytdlp.cancelDownload(taskId)
    }

    override fun getEngineDiagnostics(): Map<String, String> =
        native.getEngineDiagnostics() + ytdlp.getEngineDiagnostics()

    private fun isDirectMediaUrl(value: String): Boolean {
        val path = runCatching { Uri.parse(value).path.orEmpty() }.getOrDefault("")
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in setOf("mp4", "m4a", "mp3", "webm", "mkv", "ogg", "opus", "wav")
    }
}
