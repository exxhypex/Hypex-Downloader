package com.example.engine.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class EngineUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val hasUpdate: Boolean,
    val releaseNotes: String,
    val releaseDate: String,
    val htmlUrl: String
)

class EngineUpdater(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun checkForUpdates(currentVersion: String): Result<EngineUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
                .header("User-Agent", "HYPEX-Downloader-Android")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API returned ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val name = json.optString("name", "yt-dlp release")
                val bodyText = json.optString("body", "No release notes available.")
                val publishedAt = json.optString("published_at", "")
                val htmlUrl = json.optString("html_url", "https://github.com/yt-dlp/yt-dlp/releases")

                val hasUpdate = tagName.isNotBlank() && tagName != currentVersion && tagName > currentVersion

                Result.success(
                    EngineUpdateInfo(
                        currentVersion = currentVersion,
                        latestVersion = tagName.ifBlank { currentVersion },
                        hasUpdate = hasUpdate,
                        releaseNotes = bodyText.take(500),
                        releaseDate = publishedAt.take(10),
                        htmlUrl = htmlUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
