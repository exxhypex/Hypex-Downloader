package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY updatedAt DESC")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadFlow(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING') ORDER BY createdAt ASC")
    suspend fun getActiveAndQueuedTasks(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: DownloadEntity): Long

    @Update
    suspend fun updateDownload(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloaded, totalBytes = :total, speed = :speed, eta = :eta, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = :status, errorCode = :code, errorMessage = :msg, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFailed(id: Long, status: String, code: String?, msg: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'COMPLETED', outputPath = :outputPath, progress = 100.0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, outputPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistory()
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueueItems(): Flow<List<QueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueEntity): Long

    @Query("DELETE FROM queue WHERE downloadId = :downloadId")
    suspend fun removeByDownloadId(downloadId: Long)

    @Query("DELETE FROM queue")
    suspend fun clearQueue()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<PresetEntity>)
}
