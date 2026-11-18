package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.DownloadStatus
import com.example.core.model.DownloadTask
import com.example.core.model.FormatItem
import com.example.core.model.MediaMetadata
import com.example.core.model.PlaylistInfo
import com.example.core.model.PresetItem
import com.example.data.repository.DownloadRepository
import com.example.engine.updates.EngineUpdateInfo
import com.example.engine.updates.EngineUpdater
import com.example.engine.updates.InstalledEngineState
import com.example.engine.updates.UpdateChannel
import com.example.engine.updates.UpdateInstallState
import com.example.worker.DownloadForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HypexViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository(application)
    private val updater = EngineUpdater(application)

    val downloads: StateFlow<List<DownloadTask>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistInfo>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractedMedia = MutableStateFlow<MediaMetadata?>(null)
    val extractedMedia: StateFlow<MediaMetadata?> = _extractedMedia.asStateFlow()

    private val _extractedPlaylist = MutableStateFlow<PlaylistInfo?>(null)
    val extractedPlaylist: StateFlow<PlaylistInfo?> = _extractedPlaylist.asStateFlow()

    private val _selectedFormat = MutableStateFlow<FormatItem?>(null)
    val selectedFormat: StateFlow<FormatItem?> = _selectedFormat.asStateFlow()

    private val _selectedContainer = MutableStateFlow("mp4")
    val selectedContainer: StateFlow<String> = _selectedContainer.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _engineDiagnostics = MutableStateFlow<Map<String, String>>(emptyMap())
    val engineDiagnostics: StateFlow<Map<String, String>> = _engineDiagnostics.asStateFlow()

    private val _selectedUpdateChannel = MutableStateFlow(updater.getPreferredChannel())
    val selectedUpdateChannel: StateFlow<UpdateChannel> = _selectedUpdateChannel.asStateFlow()

    private val _installedEngineState = MutableStateFlow(updater.getInstalledState())
    val installedEngineState: StateFlow<InstalledEngineState> = _installedEngineState.asStateFlow()

    private val _updateInstallState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val updateInstallState: StateFlow<UpdateInstallState> = _updateInstallState.asStateFlow()

    private val _engineUpdate = MutableStateFlow<EngineUpdateInfo?>(null)
    val engineUpdate: StateFlow<EngineUpdateInfo?> = _engineUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _cookieLoaded = MutableStateFlow(false)
    val cookieLoaded: StateFlow<Boolean> = _cookieLoaded.asStateFlow()

    // Built-in presets
    val defaultPresets = listOf(
        PresetItem("best", "Best Quality", "bestvideo+bestaudio/best", "mp4", false, "Highest resolution available"),
        PresetItem("1080p", "1080p MP4", "bestvideo[height<=1080]+bestaudio/best", "mp4", false, "Full HD video & audio"),
        PresetItem("720p", "720p Balanced", "bestvideo[height<=720]+bestaudio/best", "mp4", false, "Fast download HD"),
        PresetItem("480p", "Data Saver", "bestvideo[height<=480]+bestaudio/best", "mp4", false, "Lowest mobile data"),
        PresetItem("mp3", "Audio MP3", "bestaudio/best", "mp3", true, "Universal audio file"),
        PresetItem("m4a", "Audio M4A", "bestaudio[ext=m4a]/bestaudio/best", "m4a", true, "High fidelity AAC audio")
    )

    init {
        loadDiagnostics()
    }

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
        if (_errorMessage.value != null) _errorMessage.value = null
    }

    fun extractUrl(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()
        if (targetUrl.isBlank()) {
            _errorMessage.value = "Please enter or paste a valid media URL."
            return
        }

        viewModelScope.launch {
            _isExtracting.value = true
            _errorMessage.value = null

            val result = repository.engineBridge.extractMedia(targetUrl)
            _isExtracting.value = false

            if (result.isSuccess) {
                val media = result.getOrThrow()
                if (media.isPlaylist) {
                    val pResult = repository.engineBridge.extractPlaylist(targetUrl)
                    if (pResult.isSuccess) {
                        _extractedPlaylist.value = pResult.getOrThrow()
                    }
                } else {
                    _extractedMedia.value = media
                    // Default to first video or best format
                    _selectedFormat.value = media.formats.firstOrNull { !it.isAudioOnly } ?: media.formats.firstOrNull()
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Extraction failed. Check the URL."
            }
        }
    }

    fun selectFormat(format: FormatItem) {
        _selectedFormat.value = format
        _isAudioOnly.value = format.isAudioOnly
        _selectedContainer.value = format.ext
    }

    fun toggleAudioOnly(audioOnly: Boolean) {
        _isAudioOnly.value = audioOnly
        val media = _extractedMedia.value ?: return
        if (audioOnly) {
            _selectedFormat.value = media.formats.firstOrNull { it.isAudioOnly }
            _selectedContainer.value = "mp3"
        } else {
            _selectedFormat.value = media.formats.firstOrNull { !it.isAudioOnly }
            _selectedContainer.value = "mp4"
        }
    }

    fun setContainer(container: String) {
        _selectedContainer.value = container
    }

    fun startDownloadExtracted() {
        val media = _extractedMedia.value ?: return
        val format = _selectedFormat.value ?: media.formats.firstOrNull() ?: return

        viewModelScope.launch {
            val taskId = repository.enqueueDownload(
                url = media.webpageUrl,
                title = media.title,
                thumbnail = media.thumbnail,
                duration = media.duration,
                formatId = format.formatId,
                formatDescription = "${format.resolution} • ${_selectedContainer.value.uppercase()}"
            )

            // Start background foreground service
            DownloadForegroundService.startDownload(getApplication(), taskId)

            _extractedMedia.value = null
            _urlInput.value = ""
            Toast.makeText(getApplication(), "Download queued: ${media.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyPresetAndDownload(preset: PresetItem) {
        val media = _extractedMedia.value ?: return
        viewModelScope.launch {
            val taskId = repository.enqueueDownload(
                url = media.webpageUrl,
                title = media.title,
                thumbnail = media.thumbnail,
                duration = media.duration,
                formatId = preset.formatSelector,
                formatDescription = "${preset.name} • ${preset.container.uppercase()}"
            )
            DownloadForegroundService.startDownload(getApplication(), taskId)
            _extractedMedia.value = null
            _urlInput.value = ""
            Toast.makeText(getApplication(), "Queued with ${preset.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissExtraction() {
        _extractedMedia.value = null
        _extractedPlaylist.value = null
    }

    fun pauseTask(taskId: Long) {
        DownloadForegroundService.pauseDownload(getApplication(), taskId)
    }

    fun resumeTask(taskId: Long) {
        DownloadForegroundService.resumeDownload(getApplication(), taskId)
    }

    fun cancelTask(taskId: Long) {
        DownloadForegroundService.cancelDownload(getApplication(), taskId)
    }

    fun retryTask(taskId: Long) {
        viewModelScope.launch {
            repository.retryDownload(taskId)
            DownloadForegroundService.startDownload(getApplication(), taskId)
        }
    }

    fun deleteTask(taskId: Long, deleteFile: Boolean = false) {
        viewModelScope.launch {
            repository.deleteDownload(taskId, deleteFile)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun togglePlaylistItem(index: Int) {
        val pl = _extractedPlaylist.value ?: return
        val items = pl.items.toMutableList()
        if (index in items.indices) {
            val item = items[index]
            items[index] = item.copy(selected = !item.selected)
            _extractedPlaylist.value = pl.copy(items = items)
        }
    }

    fun selectAllPlaylist(selected: Boolean) {
        val pl = _extractedPlaylist.value ?: return
        val updated = pl.items.map { it.copy(selected = selected) }
        _extractedPlaylist.value = pl.copy(items = updated)
    }

    fun downloadSelectedPlaylistItems(presetName: String = "1080p MP4") {
        val pl = _extractedPlaylist.value ?: return
        val selected = pl.items.filter { it.selected }
        if (selected.isEmpty()) {
            Toast.makeText(getApplication(), "No items selected in playlist", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            repository.savePlaylist(pl)
            for (item in selected) {
                val taskId = repository.enqueueDownload(
                    url = item.url,
                    title = item.title,
                    thumbnail = item.thumbnail,
                    duration = item.duration,
                    formatId = "bestvideo+bestaudio/best",
                    formatDescription = "$presetName"
                )
                DownloadForegroundService.startDownload(getApplication(), taskId)
            }
            _extractedPlaylist.value = null
            Toast.makeText(getApplication(), "Queued ${selected.size} videos from playlist", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectUpdateChannel(channel: UpdateChannel) {
        if (_selectedUpdateChannel.value == channel && _engineUpdate.value != null) return
        updater.setPreferredChannel(channel)
        _selectedUpdateChannel.value = channel
        _engineUpdate.value = null
        _updateInstallState.value = UpdateInstallState.Idle
        checkForUpdates(channel)
    }

    fun checkForUpdates(channel: UpdateChannel = _selectedUpdateChannel.value) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateInstallState.value = UpdateInstallState.Checking(channel)
            val res = updater.checkForUpdates(channel)
            _isCheckingUpdate.value = false
            if (res.isSuccess) {
                _engineUpdate.value = res.getOrThrow()
                _updateInstallState.value = UpdateInstallState.Idle
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Failed to check ${channel.displayName} channel"
                _updateInstallState.value = UpdateInstallState.Error(msg)
                Toast.makeText(getApplication(), "Update check failed: $msg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun installEngineUpdate() {
        val info = _engineUpdate.value ?: return
        viewModelScope.launch {
            val res = updater.downloadAndInstallUpdate(info) { state ->
                _updateInstallState.value = state
            }
            if (res.isSuccess) {
                val newState = res.getOrThrow()
                _installedEngineState.value = newState
                _selectedUpdateChannel.value = newState.channel
                _engineUpdate.value = info.copy(
                    currentVersion = newState.version,
                    hasUpdate = false,
                    isChannelSwitch = false
                )
                loadDiagnostics()
                Toast.makeText(
                    getApplication(),
                    "Updated yt-dlp to ${newState.version} (${newState.channel.displayName})",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val errMsg = res.exceptionOrNull()?.message ?: "Install failed"
                Toast.makeText(getApplication(), errMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun rollbackEngineUpdate() {
        viewModelScope.launch {
            val res = updater.rollback()
            if (res.isSuccess) {
                val restored = res.getOrThrow()
                _installedEngineState.value = restored
                _selectedUpdateChannel.value = restored.channel
                _engineUpdate.value = null
                _updateInstallState.value = UpdateInstallState.Success(
                    restored,
                    "Rolled back to yt-dlp ${restored.version} (${restored.channel.displayName})"
                )
                loadDiagnostics()
                Toast.makeText(
                    getApplication(),
                    "Rolled back to ${restored.version}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    getApplication(),
                    res.exceptionOrNull()?.message ?: "Rollback unavailable",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun resetEngineToBundled() {
        viewModelScope.launch {
            val res = updater.resetToBundled()
            if (res.isSuccess) {
                val bundled = res.getOrThrow()
                _installedEngineState.value = bundled
                _selectedUpdateChannel.value = bundled.channel
                _engineUpdate.value = null
                _updateInstallState.value = UpdateInstallState.Success(
                    bundled,
                    "Restored bundled yt-dlp ${bundled.version}"
                )
                loadDiagnostics()
                Toast.makeText(getApplication(), "Restored bundled yt-dlp ${bundled.version}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveCookies(content: String): Boolean {
        return if (content.isNotBlank()) {
            _cookieLoaded.value = true
            Toast.makeText(getApplication(), "Cookies loaded successfully", Toast.LENGTH_SHORT).show()
            true
        } else false
    }

    fun clearCookies() {
        _cookieLoaded.value = false
        Toast.makeText(getApplication(), "Cookies cleared", Toast.LENGTH_SHORT).show()
    }

    fun openDownloadedFile(context: Context, task: DownloadTask) {
        if (task.outputPath.isBlank()) return
        val file = File(task.outputPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on device", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val ext = file.extension.lowercase()
            val mime = when (ext) {
                "mp4", "mkv", "webm" -> "video/*"
                "mp3", "m4a", "opus", "wav" -> "audio/*"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDownloadedFile(context: Context, task: DownloadTask) {
        if (task.outputPath.isBlank()) return
        val file = File(task.outputPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media with..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDiagnostics() {
        _engineDiagnostics.value = repository.engineBridge.getEngineDiagnostics()
    }
}
