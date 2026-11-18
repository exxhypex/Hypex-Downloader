package com.example.engine.bridge

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.example.core.model.DownloadProgress
import com.example.core.model.DownloadTask
import com.example.core.model.FormatItem
import com.example.core.model.MediaMetadata
import com.example.core.model.PlaylistInfo
import com.example.core.model.PlaylistItem
import com.example.core.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the bundled yt-dlp package through Chaquopy.
 * All Python entry points are invoked on Dispatchers.IO.
 */
class PythonYtDlpEngine(private val context: Context) : EngineBridge {
    @Volatile private var initialized = false
    private val playlistCache = ConcurrentHashMap<String, PlaylistInfo>()

    private val bridge: PyObject
        get() = Python.getInstance().getModule("downloader.android_bridge")

    private fun ensureInitialized() {
        if (!Python.isStarted()) {
            Python.start(com.chaquo.python.android.AndroidPlatform(context))
        }
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    check(bridge.callAttr("initialize", context.filesDir.absolutePath).toBoolean()) {
                        "Unable to initialize the embedded yt-dlp engine"
                    }
                    initialized = true
                }
            }
        }
    }

    override suspend fun initializeEngine(): Boolean = withContext(Dispatchers.IO) {
        ensureInitialized()
        initialized
    }

    override fun getEngineVersion(): String = if (!Python.isStarted() || !initialized) {
        "Embedded (lazy initialization)"
    } else {
        runCatching { bridge.callAttr("get_version").toString() }.getOrDefault("Unavailable")
    }

    override suspend fun extractMedia(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            val response = JSONObject(bridge.callAttr("extract_info", url).toString())
            require(response.optBoolean("success")) {
                response.optJSONObject("error")?.optString("message")
                    ?: "yt-dlp could not extract this URL"
            }
            val data = response.getJSONObject("data")
            if (data.optBoolean("is_playlist")) {
                val playlist = parsePlaylist(data.getJSONObject("playlist"))
                playlistCache[url] = playlist
                MediaMetadata(
                    id = playlist.playlistId,
                    title = playlist.title,
                    author = playlist.uploader,
                    duration = 0L,
                    thumbnail = playlist.thumbnail,
                    webpageUrl = url,
                    description = "Playlist with ${playlist.videoCount} items",
                    isPlaylist = true
                )
            } else {
                parseMedia(data, url)
            }
        }
    }

    override suspend fun extractPlaylist(url: String): Result<PlaylistInfo> = withContext(Dispatchers.IO) {
        playlistCache.remove(url)?.let { return@withContext Result.success(it) }
        runCatching {
            ensureInitialized()
            val response = JSONObject(bridge.callAttr("extract_playlist", url).toString())
            require(response.optBoolean("success")) {
                response.optJSONObject("error")?.optString("message")
                    ?: "yt-dlp could not extract this playlist"
            }
            val data = response.getJSONObject("data")
            val playlistJson = if (data.optBoolean("is_playlist")) data.getJSONObject("playlist") else data
            parsePlaylist(playlistJson)
        }
    }

    override suspend fun getFormats(url: String): Result<List<FormatItem>> =
        extractMedia(url).map { it.formats }

    override suspend fun startDownload(
        task: DownloadTask,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            val outputDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val template = File(outputDir, "${task.id}_%(title).80s.%(ext)s").absolutePath
            val callback = object {
                @Suppress("unused")
                fun emitProgress(payload: String) {
                    runCatching {
                        val json = JSONObject(payload)
                        onProgress(
                            DownloadProgress(
                                taskId = task.id,
                                status = json.optString("status", "DOWNLOADING").uppercase(),
                                progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 100f),
                                downloadedBytes = json.optLong("downloaded_bytes"),
                                totalBytes = json.optLong("total_bytes"),
                                speed = json.optDouble("speed").toLong(),
                                eta = json.optLong("eta"),
                                filename = json.optString("filename")
                            )
                        )
                    }
                }
            }
            val response = JSONObject(
                bridge.callAttr(
                    "download_media",
                    task.id.toString(),
                    task.url,
                    task.formatId,
                    template,
                    PyObject.fromJava(callback)
                ).toString()
            )
            require(response.optBoolean("success")) {
                response.optJSONObject("error")?.optString("message")
                    ?: "yt-dlp download failed"
            }
            val outputPath = response.optString("output_file")
            val outputFile = File(outputPath)
            require(outputPath.isNotBlank() && outputFile.exists()) {
                "yt-dlp reported success but the output file was not found"
            }
            outputFile
        }
    }

    override fun pauseDownload(taskId: Long) {
        if (Python.isStarted() && initialized) {
            runCatching { bridge.callAttr("cancel_task", taskId.toString()) }
        }
    }

    override fun cancelDownload(taskId: Long) {
        if (Python.isStarted() && initialized) {
            runCatching { bridge.callAttr("cancel_task", taskId.toString()) }
        }
    }

    override fun getEngineDiagnostics(): Map<String, String> {
        if (!Python.isStarted() || !initialized) {
            return mapOf("yt-dlp Runtime" to "Not started (lazy)")
        }
        return runCatching {
            val json = JSONObject(bridge.callAttr("get_diagnostics").toString())
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrDefault(mapOf("yt-dlp Runtime" to "Initialization error"))
    }

    private fun parseMedia(data: JSONObject, originalUrl: String): MediaMetadata {
        val formats = data.optJSONArray("formats").toFormatItems()
        val subtitles = data.optJSONArray("subtitles").toSubtitleItems()
        return MediaMetadata(
            id = data.optString("id"),
            title = data.optString("title", "Unknown title"),
            author = data.optString("author", "Unknown creator"),
            duration = data.optLong("duration"),
            thumbnail = data.optString("thumbnail"),
            webpageUrl = data.optString("webpage_url").ifBlank { originalUrl },
            description = data.optString("description"),
            viewCount = if (data.isNull("view_count")) null else data.optLong("view_count"),
            uploadDate = data.optString("upload_date").ifBlank { null },
            formats = formats,
            subtitles = subtitles
        )
    }

    private fun parsePlaylist(json: JSONObject): PlaylistInfo {
        val items = mutableListOf<PlaylistItem>()
        val array = json.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("video_id")
            val itemUrl = item.optString("url").let {
                if (it.startsWith("http")) it else "https://www.youtube.com/watch?v=$id"
            }
            items += PlaylistItem(
                videoId = id,
                title = item.optString("title", "Track ${i + 1}"),
                duration = item.optLong("duration"),
                thumbnail = item.optString("thumbnail"),
                uploader = item.optString("uploader"),
                position = item.optInt("position", i + 1),
                url = itemUrl,
                selected = item.optBoolean("selected", true)
            )
        }
        return PlaylistInfo(
            playlistId = json.optString("playlist_id"),
            title = json.optString("title", "Untitled playlist"),
            uploader = json.optString("uploader"),
            videoCount = json.optInt("video_count", items.size),
            thumbnail = json.optString("thumbnail"),
            items = items
        )
    }

    private fun JSONArray?.toFormatItems(): List<FormatItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            FormatItem(
                formatId = item.optString("format_id"),
                ext = item.optString("ext", "mp4"),
                resolution = item.optString("resolution", "Unknown"),
                vcodec = item.optString("vcodec", "none"),
                acodec = item.optString("acodec", "none"),
                filesize = item.optLong("filesize"),
                fps = item.optDouble("fps").takeIf { !item.isNull("fps") && it > 0 },
                tbr = item.optDouble("tbr").takeIf { !item.isNull("tbr") && it > 0 },
                asr = item.optInt("asr").takeIf { !item.isNull("asr") && it > 0 },
                formatNote = item.optString("format_note"),
                isVideoOnly = item.optBoolean("is_video_only"),
                isAudioOnly = item.optBoolean("is_audio_only"),
                isMuxed = item.optBoolean("is_muxed")
            )
        }
    }

    private fun JSONArray?.toSubtitleItems(): List<SubtitleItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val item = optJSONObject(index) ?: return@mapNotNull null
            val formats = item.optJSONArray("formats") ?: JSONArray()
            SubtitleItem(
                language = item.optString("language"),
                name = item.optString("name"),
                isAuto = item.optBoolean("is_auto"),
                formats = (0 until formats.length()).map { formats.optString(it) }
            )
        }
    }
}
