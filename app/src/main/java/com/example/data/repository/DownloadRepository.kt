package com.example.data.repository

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.database.DownloadDao
import com.example.core.database.DownloadEntity
import com.example.core.database.PlaylistDao
import com.example.core.database.PlaylistEntity
import com.example.core.database.PlaylistItemEntity
import com.example.core.database.QueueDao
import com.example.core.database.QueueEntity
import com.example.core.model.DownloadProgress
import com.example.core.model.DownloadStatus
import com.example.core.model.DownloadTask
import com.example.core.model.PlaylistInfo
import com.example.core.model.PlaylistItem
import com.example.engine.bridge.EngineBridge
import com.example.engine.network.NativeMediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val downloadDao: DownloadDao = database.downloadDao(),
    private val queueDao: QueueDao = database.queueDao(),
    private val playlistDao: PlaylistDao = database.playlistDao(),
    val engineBridge: EngineBridge = NativeMediaEngine(context)
) {
    val allDownloads: Flow<List<DownloadTask>> = downloadDao.getAllDownloads().map { list ->
        list.map { it.toModel() }
    }

    val allPlaylists: Flow<List<PlaylistInfo>> = playlistDao.getAllPlaylists().map { list ->
        list.map { entity ->
            PlaylistInfo(
                playlistId = entity.playlistId,
                title = entity.title,
                uploader = entity.uploader,
                videoCount = entity.videoCount,
                thumbnail = entity.thumbnail
            )
        }
    }

    suspend fun getDownloadById(id: Long): DownloadTask? = withContext(Dispatchers.IO) {
        downloadDao.getDownloadById(id)?.toModel()
    }

    suspend fun enqueueDownload(
        url: String,
        title: String,
        thumbnail: String,
        duration: Long,
        formatId: String,
        formatDescription: String,
        outputPath: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val entity = DownloadEntity(
            url = url,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            formatId = formatId,
            formatDescription = formatDescription,
            outputPath = outputPath,
            status = DownloadStatus.QUEUED.name,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val downloadId = downloadDao.insertDownload(entity)
        queueDao.insert(
            QueueEntity(
                name = title,
                position = 0,
                downloadId = downloadId,
                status = DownloadStatus.QUEUED.name
            )
        )
        downloadId
    }

    suspend fun enqueueMultiple(tasks: List<DownloadTask>) = withContext(Dispatchers.IO) {
        tasks.forEach { task ->
            enqueueDownload(
                url = task.url,
                title = task.title,
                thumbnail = task.thumbnail,
                duration = task.duration,
                formatId = task.formatId,
                formatDescription = task.formatDescription,
                outputPath = task.outputPath
            )
        }
    }

    suspend fun updateProgress(progress: DownloadProgress) = withContext(Dispatchers.IO) {
        downloadDao.updateProgress(
            id = progress.taskId,
            progress = progress.progress,
            downloaded = progress.downloadedBytes,
            total = progress.totalBytes,
            speed = progress.speed,
            eta = progress.eta
        )
    }

    suspend fun markStatus(id: Long, status: DownloadStatus) = withContext(Dispatchers.IO) {
        downloadDao.updateStatus(id, status.name)
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED) {
            queueDao.removeByDownloadId(id)
        }
    }

    suspend fun markCompleted(id: Long, outputPath: String) = withContext(Dispatchers.IO) {
        downloadDao.markCompleted(id, outputPath)
        queueDao.removeByDownloadId(id)
    }

    suspend fun markFailed(id: Long, errorCode: String?, errorMessage: String?) = withContext(Dispatchers.IO) {
        downloadDao.markFailed(id, DownloadStatus.FAILED.name, errorCode, errorMessage)
        queueDao.removeByDownloadId(id)
    }

    suspend fun pauseDownload(id: Long) = withContext(Dispatchers.IO) {
        engineBridge.pauseDownload(id)
        downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
    }

    suspend fun cancelDownload(id: Long) = withContext(Dispatchers.IO) {
        engineBridge.cancelDownload(id)
        downloadDao.updateStatus(id, DownloadStatus.CANCELLED.name)
        queueDao.removeByDownloadId(id)
    }

    suspend fun retryDownload(id: Long) = withContext(Dispatchers.IO) {
        downloadDao.updateStatus(id, DownloadStatus.QUEUED.name)
        queueDao.insert(
            QueueEntity(
                name = "Retry $id",
                position = 0,
                downloadId = id,
                status = DownloadStatus.QUEUED.name
            )
        )
    }

    suspend fun retryAllFailed() = withContext(Dispatchers.IO) {
        val list = downloadDao.getActiveAndQueuedTasks()
        // Find failed
        // Re-queue
    }

    suspend fun deleteDownload(id: Long, deleteFile: Boolean = false) = withContext(Dispatchers.IO) {
        if (deleteFile) {
            val task = downloadDao.getDownloadById(id)
            if (task != null && task.outputPath.isNotBlank()) {
                val file = File(task.outputPath)
                if (file.exists()) file.delete()
            }
        }
        downloadDao.deleteDownloadById(id)
        queueDao.removeByDownloadId(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        downloadDao.clearHistory()
    }

    suspend fun savePlaylist(playlist: PlaylistInfo) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                playlistId = playlist.playlistId,
                title = playlist.title,
                thumbnail = playlist.thumbnail,
                videoCount = playlist.videoCount,
                uploader = playlist.uploader
            )
        )
        val entities = playlist.items.map {
            PlaylistItemEntity(
                playlistId = playlist.playlistId,
                videoId = it.videoId,
                title = it.title,
                duration = it.duration,
                thumbnail = it.thumbnail,
                position = it.position,
                url = it.url,
                selected = it.selected
            )
        }
        playlistDao.insertPlaylistItems(entities)
    }

    suspend fun reconcilePendingTasks() = withContext(Dispatchers.IO) {
        // Find any tasks that were left in DOWNLOADING or EXTRACTING states from killed process
        val active = downloadDao.getActiveAndQueuedTasks()
        for (task in active) {
            if (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.PROCESSING.name) {
                // Check if file exists or can be resumed
                downloadDao.updateStatus(task.id, DownloadStatus.PAUSED.name)
            }
        }
    }

    private fun DownloadEntity.toModel(): DownloadTask {
        val st = try {
            DownloadStatus.valueOf(status)
        } catch (e: Exception) {
            DownloadStatus.QUEUED
        }

        return DownloadTask(
            id = id,
            url = url,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            formatId = formatId,
            formatDescription = formatDescription,
            outputPath = outputPath,
            temporaryPath = temporaryPath,
            status = st,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            eta = eta,
            createdAt = createdAt,
            updatedAt = updatedAt,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }
}
