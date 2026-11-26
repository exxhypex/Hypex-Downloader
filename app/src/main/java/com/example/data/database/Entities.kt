package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: Long = 0L,
    val formatId: String,
    val formatDescription: String,
    val outputPath: String,
    val temporaryPath: String = "",
    val status: String, // DownloadStatus enum name
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: Long = 0L,
    val eta: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorCode: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val position: Int,
    val downloadId: Long,
    val status: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val thumbnail: String,
    val videoCount: Int,
    val uploader: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String,
    val videoId: String,
    val title: String,
    val duration: Long,
    val thumbnail: String,
    val position: Int,
    val url: String,
    val selected: Boolean = true
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val formatSelector: String,
    val container: String = "mp4",
    val audioFormat: String = "mp3",
    val subtitleLanguage: String = "en",
    val filenameTemplate: String = "%(title)s.%(ext)s",
    val isBuiltIn: Boolean = false
)
