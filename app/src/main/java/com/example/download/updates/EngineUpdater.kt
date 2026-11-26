package com.example.engine.updates

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class UpdateChannel(
    val displayName: String,
    val repoSlug: String,
    val description: String
) {
    STABLE(
        displayName = "Stable",
        repoSlug = "yt-dlp/yt-dlp",
        description = "Tested official releases (recommended)"
    ),
    NIGHTLY(
        displayName = "Nightly",
        repoSlug = "yt-dlp/yt-dlp-nightly-builds",
        description = "Daily builds with latest site fixes"
    );

    companion object {
        fun fromName(name: String?): UpdateChannel =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: STABLE
    }
}

data class EngineUpdateInfo(
    val channel: UpdateChannel,
    val currentVersion: String,
    val latestVersion: String,
    val hasUpdate: Boolean,
    val isChannelSwitch: Boolean,
    val releaseNotes: String,
    val releaseDate: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val checksumUrl: String?
)

data class InstalledEngineState(
    val version: String,
    val channel: UpdateChannel,
    val installedAt: Long,
    val sha256: String,
    val isBundled: Boolean,
    val canRollback: Boolean,
    val backupVersion: String?,
    val backupChannel: UpdateChannel?,
    val engineFilePath: String?
)

sealed class UpdateInstallState {
    data object Idle : UpdateInstallState()
    data class Checking(val channel: UpdateChannel) : UpdateInstallState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateInstallState()
    data class Verifying(val step: String) : UpdateInstallState()
    data object Installing : UpdateInstallState()
    data class Success(val state: InstalledEngineState, val message: String) : UpdateInstallState()
    data class Error(val message: String) : UpdateInstallState()
}

class EngineUpdater(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        const val BUNDLED_VERSION = "2026.03.01"
        private const val PREFS_NAME = "hypex_engine_updater_prefs"
        private const val KEY_CHANNEL = "preferred_update_channel"
        private const val ENGINE_ROOT_DIR = "ytdlp_engine"
        private const val CURRENT_DIR = "current"
        private const val BACKUP_DIR = "backup"
        private const val STAGING_DIR = "staging"
        private const val ENGINE_FILENAME = "yt-dlp"
        private const val METADATA_FILENAME = "metadata.json"
        private const val MIN_VALID_PACKAGE_BYTES = 64 * 1024L
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val engineRoot: File
        get() = File(context.filesDir, ENGINE_ROOT_DIR)

    private val currentDir: File
        get() = File(engineRoot, CURRENT_DIR)

    private val backupDir: File
        get() = File(engineRoot, BACKUP_DIR)

    private val stagingDir: File
        get() = File(engineRoot, STAGING_DIR)

    fun getPreferredChannel(): UpdateChannel {
        return UpdateChannel.fromName(prefs.getString(KEY_CHANNEL, UpdateChannel.STABLE.name))
    }

    fun setPreferredChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.name).apply()
    }

    fun getInstalledState(): InstalledEngineState {
        val currentBinary = File(currentDir, ENGINE_FILENAME)
        val currentMeta = readMetadata(File(currentDir, METADATA_FILENAME))
        val backupBinary = File(backupDir, ENGINE_FILENAME)
        val backupMeta = readMetadata(File(backupDir, METADATA_FILENAME))

        val hasBackup = backupBinary.exists() && backupBinary.length() > 0 && backupMeta != null

        return if (currentBinary.exists() && currentBinary.length() > 0 && currentMeta != null) {
            InstalledEngineState(
                version = currentMeta.optString("version", BUNDLED_VERSION),
                channel = UpdateChannel.fromName(currentMeta.optString("channel", UpdateChannel.STABLE.name)),
                installedAt = currentMeta.optLong("installed_at", 0L),
                sha256 = currentMeta.optString("sha256", ""),
                isBundled = false,
                canRollback = hasBackup,
                backupVersion = if (hasBackup) backupMeta?.optString("version") else null,
                backupChannel = if (hasBackup) UpdateChannel.fromName(backupMeta?.optString("channel")) else null,
                engineFilePath = currentBinary.absolutePath
            )
        } else {
            InstalledEngineState(
                version = BUNDLED_VERSION,
                channel = UpdateChannel.STABLE,
                installedAt = 0L,
                sha256 = "bundled",
                isBundled = true,
                canRollback = hasBackup,
                backupVersion = if (hasBackup) backupMeta?.optString("version") else null,
                backupChannel = if (hasBackup) UpdateChannel.fromName(backupMeta?.optString("channel")) else null,
                engineFilePath = null
            )
        }
    }

    suspend fun checkForUpdates(
        channel: UpdateChannel = getPreferredChannel()
    ): Result<EngineUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val installedState = getInstalledState()
            val apiUrl = "https://api.github.com/repos/${channel.repoSlug}/releases/latest"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "HYPEX-Downloader-Android")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("GitHub API returned HTTP ${response.code} for ${channel.displayName}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response from GitHub"))

                val info = parseReleaseJson(body, channel, installedState)
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseReleaseJson(
        jsonString: String,
        channel: UpdateChannel,
        installedState: InstalledEngineState
    ): EngineUpdateInfo {
        val json = JSONObject(jsonString)
        val tagName = json.optString("tag_name", "").removePrefix("v").trim()
        val bodyText = json.optString("body", "No release notes available.")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", "https://github.com/${channel.repoSlug}/releases")
        val assets = json.optJSONArray("assets")

        var primaryDownloadUrl = ""
        var primaryAssetName = "yt-dlp"
        var primaryAssetSize = 0L
        var fallbackDownloadUrl = ""
        var fallbackAssetName = ""
        var fallbackAssetSize = 0L
        var checksumUrl: String? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)

                when (name) {
                    "yt-dlp" -> {
                        primaryDownloadUrl = url
                        primaryAssetName = name
                        primaryAssetSize = size
                    }
                    "yt-dlp.tar.gz" -> {
                        fallbackDownloadUrl = url
                        fallbackAssetName = name
                        fallbackAssetSize = size
                    }
                    "SHA2-256SUMS" -> {
                        checksumUrl = url
                    }
                }
            }
        }

        val resolvedDownloadUrl = primaryDownloadUrl.ifBlank { fallbackDownloadUrl }
        val resolvedAssetName = if (primaryDownloadUrl.isNotBlank()) primaryAssetName else fallbackAssetName
        val resolvedAssetSize = if (primaryDownloadUrl.isNotBlank()) primaryAssetSize else fallbackAssetSize

        val latestTag = tagName.ifBlank { installedState.version }
        val isChannelSwitch = channel != installedState.channel
        val hasNewerVersion = isVersionDifferentOrNewer(
            currentVersion = installedState.version,
            latestVersion = latestTag,
            isChannelSwitch = isChannelSwitch
        )

        return EngineUpdateInfo(
            channel = channel,
            currentVersion = installedState.version,
            latestVersion = latestTag,
            hasUpdate = hasNewerVersion,
            isChannelSwitch = isChannelSwitch,
            releaseNotes = bodyText.trim().take(600),
            releaseDate = publishedAt.take(10),
            htmlUrl = htmlUrl,
            downloadUrl = resolvedDownloadUrl,
            assetName = resolvedAssetName,
            assetSizeBytes = resolvedAssetSize,
            checksumUrl = checksumUrl
        )
    }

    internal fun isVersionDifferentOrNewer(
        currentVersion: String,
        latestVersion: String,
        isChannelSwitch: Boolean
    ): Boolean {
        if (latestVersion.isBlank()) return false
        if (isChannelSwitch) return true
        return latestVersion != currentVersion && compareVersions(latestVersion, currentVersion) > 0
    }

    internal fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-", "_").mapNotNull { it.toLongOrNull() }
        val parts2 = v2.split(".", "-", "_").mapNotNull { it.toLongOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0L }
            val p2 = parts2.getOrElse(i) { 0L }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return v1.compareTo(v2)
    }

    internal fun parseSha256Sums(sumsContent: String, assetName: String): String? {
        for (line in sumsContent.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val hash = parts[0].lowercase()
                val file = parts[1].removePrefix("*").removePrefix("./")
                if (file == assetName && hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
                    return hash
                }
            }
        }
        return null
    }

    suspend fun downloadAndInstallUpdate(
        updateInfo: EngineUpdateInfo,
        onStateChange: (UpdateInstallState) -> Unit
    ): Result<InstalledEngineState> = withContext(Dispatchers.IO) {
        if (updateInfo.downloadUrl.isBlank()) {
            val msg = "No compatible yt-dlp package asset found in this release."
            onStateChange(UpdateInstallState.Error(msg))
            return@withContext Result.failure(IllegalStateException(msg))
        }

        stagingDir.mkdirs()
        val stagedFile = File(stagingDir, "$ENGINE_FILENAME.tmp")
        if (stagedFile.exists()) stagedFile.delete()

        try {
            // 1. Download release package over HTTPS
            onStateChange(UpdateInstallState.Downloading(0f, 0L, updateInfo.assetSizeBytes))
            val downloadRequest = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "HYPEX-Downloader-Android")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download stream")
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else updateInfo.assetSizeBytes
                var downloadedBytes = 0L
                var lastReportAt = 0L

                body.byteStream().use { input ->
                    FileOutputStream(stagedFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportAt >= 120L) {
                                val pct = if (totalBytes > 0) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                onStateChange(UpdateInstallState.Downloading(pct, downloadedBytes, totalBytes))
                                lastReportAt = now
                            }
                        }
                        output.flush()
                    }
                }
                onStateChange(UpdateInstallState.Downloading(1f, downloadedBytes, totalBytes.coerceAtLeast(downloadedBytes)))
            }

            // 2. Verify payload format & minimum size
            onStateChange(UpdateInstallState.Verifying("Validating yt-dlp archive integrity..."))
            if (!isValidEnginePayload(stagedFile, updateInfo.assetName)) {
                throw SecurityException("Downloaded file is corrupted or invalid yt-dlp package.")
            }

            val actualSha256 = computeSha256(stagedFile)

            // 3. Verify SHA-256 against official SHA2-256SUMS when available
            if (!updateInfo.checksumUrl.isNullOrBlank()) {
                onStateChange(UpdateInstallState.Verifying("Verifying SHA-256 signature..."))
                val sumReq = Request.Builder()
                    .url(updateInfo.checksumUrl)
                    .header("User-Agent", "HYPEX-Downloader-Android")
                    .build()

                client.newCall(sumReq).execute().use { sumResp ->
                    if (!sumResp.isSuccessful) {
                        throw SecurityException("Failed to fetch SHA2-256SUMS (HTTP ${sumResp.code})")
                    }
                    val sumsText = sumResp.body?.string()
                        ?: throw SecurityException("Empty SHA2-256SUMS response")
                    val expectedSha256 = parseSha256Sums(sumsText, updateInfo.assetName)
                        ?: throw SecurityException("Missing checksum entry for ${updateInfo.assetName}")

                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        throw SecurityException(
                            "SHA-256 mismatch! Expected ${expectedSha256.take(12)}…, got ${actualSha256.take(12)}…"
                        )
                    }
                }
            }

            // 4. Preserve previous version in backup before atomic swap
            onStateChange(UpdateInstallState.Installing)
            val currentBinary = File(currentDir, ENGINE_FILENAME)
            val currentMetaFile = File(currentDir, METADATA_FILENAME)

            if (currentBinary.exists() && currentMetaFile.exists()) {
                backupDir.mkdirs()
                val backupBinary = File(backupDir, ENGINE_FILENAME)
                val backupMetaFile = File(backupDir, METADATA_FILENAME)
                currentBinary.copyTo(backupBinary, overwrite = true)
                currentMetaFile.copyTo(backupMetaFile, overwrite = true)
            }

            // 5. Move staged package to active directory
            currentDir.mkdirs()
            val targetBinary = File(currentDir, ENGINE_FILENAME)
            if (targetBinary.exists()) targetBinary.delete()
            if (!stagedFile.renameTo(targetBinary)) {
                stagedFile.copyTo(targetBinary, overwrite = true)
                stagedFile.delete()
            }

            val installedAt = System.currentTimeMillis()
            val metaJson = JSONObject().apply {
                put("version", updateInfo.latestVersion)
                put("channel", updateInfo.channel.name)
                put("sha256", actualSha256)
                put("asset_name", updateInfo.assetName)
                put("installed_at", installedAt)
                put("source_url", updateInfo.htmlUrl)
            }
            File(currentDir, METADATA_FILENAME).writeText(metaJson.toString(2))

            setPreferredChannel(updateInfo.channel)

            val newState = getInstalledState()
            onStateChange(
                UpdateInstallState.Success(
                    state = newState,
                    message = "Installed yt-dlp ${newState.version} (${newState.channel.displayName})"
                )
            )
            Result.success(newState)
        } catch (e: Exception) {
            if (stagedFile.exists()) stagedFile.delete()
            val msg = e.message ?: "Update installation failed"
            onStateChange(UpdateInstallState.Error(msg))
            Result.failure(e)
        }
    }

    suspend fun rollback(): Result<InstalledEngineState> = withContext(Dispatchers.IO) {
        try {
            val backupBinary = File(backupDir, ENGINE_FILENAME)
            val backupMetaFile = File(backupDir, METADATA_FILENAME)
            if (!backupBinary.exists() || !backupMetaFile.exists()) {
                return@withContext Result.failure(IllegalStateException("No previous version snapshot available to rollback."))
            }

            currentDir.mkdirs()
            val currentBinary = File(currentDir, ENGINE_FILENAME)
            val currentMetaFile = File(currentDir, METADATA_FILENAME)

            backupBinary.copyTo(currentBinary, overwrite = true)
            backupMetaFile.copyTo(currentMetaFile, overwrite = true)

            backupBinary.delete()
            backupMetaFile.delete()

            val restoredState = getInstalledState()
            setPreferredChannel(restoredState.channel)
            Result.success(restoredState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetToBundled(): Result<InstalledEngineState> = withContext(Dispatchers.IO) {
        try {
            currentDir.deleteRecursively()
            backupDir.deleteRecursively()
            stagingDir.deleteRecursively()
            setPreferredChannel(UpdateChannel.STABLE)
            Result.success(getInstalledState())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun isValidEnginePayload(file: File, assetName: String): Boolean {
        if (!file.exists() || file.length() < MIN_VALID_PACKAGE_BYTES) return false
        val header = ByteArray(512)
        val readCount = FileInputStream(file).use { it.read(header) }
        if (readCount < 4) return false

        // yt-dlp zipapp starts with "#!/usr/bin/env python3\nPK\x03\x04" or "PK\x03\x04"
        if (assetName == "yt-dlp") {
            val hasPkMagic = (0 until readCount - 3).any { i ->
                header[i] == 0x50.toByte() &&
                    header[i + 1] == 0x4B.toByte() &&
                    header[i + 2] == 0x03.toByte() &&
                    header[i + 3] == 0x04.toByte()
            }
            return hasPkMagic
        }
        // tar.gz magic: 0x1F 0x8B
        if (assetName.endsWith(".tar.gz")) {
            return header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()
        }
        return true
    }

    internal fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readMetadata(file: File): JSONObject? {
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }
}
