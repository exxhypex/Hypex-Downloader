package com.exxhypex.hypexdownloader.engine.bridge

import com.exxhypex.hypexdownloader.core.model.DownloadProgress
import com.exxhypex.hypexdownloader.core.model.DownloadTask
import com.exxhypex.hypexdownloader.core.model.FormatItem
import com.exxhypex.hypexdownloader.core.model.MediaMetadata
import com.exxhypex.hypexdownloader.core.model.PlaylistInfo
import java.io.File

interface EngineBridge {
    suspend fun initializeEngine(): Boolean
    fun getEngineVersion(): String
    suspend fun extractMedia(url: String): Result<MediaMetadata>
    suspend fun extractPlaylist(url: String): Result<PlaylistInfo>
    suspend fun getFormats(url: String): Result<List<FormatItem>>
    suspend fun startDownload(
        task: DownloadTask,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File>
    fun pauseDownload(taskId: Long)
    fun cancelDownload(taskId: Long)
    fun getEngineDiagnostics(): Map<String, String>
}
