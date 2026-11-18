package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.updates.EngineUpdater
import com.example.engine.updates.UpdateChannel
import com.example.engine.updates.UpdateInstallState
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineUpdaterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "ytdlp_engine").deleteRecursively()
    }

    private fun createValidZipAppBytes(sizeBytes: Int = 70 * 1024): ByteArray {
        val header = "#!/usr/bin/env python3\n".toByteArray()
        val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val payload = ByteArray(sizeBytes)
        header.copyInto(payload, 0)
        zipMagic.copyInto(payload, header.size)
        return payload
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `stable and nightly channel release parsing detects updates and channel switches`() = runTest {
        val stableJson = """
            {
              "tag_name": "2026.11.15",
              "published_at": "2026-11-15T10:00:00Z",
              "body": "Stable extractor updates",
              "html_url": "https://github.com/yt-dlp/yt-dlp/releases/tag/2026.11.15",
              "assets": [
                {
                  "name": "yt-dlp",
                  "size": 3100000,
                  "browser_download_url": "https://example.test/stable/yt-dlp"
                },
                {
                  "name": "SHA2-256SUMS",
                  "size": 1024,
                  "browser_download_url": "https://example.test/stable/SHA2-256SUMS"
                }
              ]
            }
        """.trimIndent()

        val nightlyJson = """
            {
              "tag_name": "2026.11.18.232854",
              "published_at": "2026-11-18T23:30:00Z",
              "body": "Nightly master build",
              "html_url": "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/tag/2026.11.18.232854",
              "assets": [
                {
                  "name": "yt-dlp",
                  "size": 3120000,
                  "browser_download_url": "https://example.test/nightly/yt-dlp"
                },
                {
                  "name": "SHA2-256SUMS",
                  "size": 1024,
                  "browser_download_url": "https://example.test/nightly/SHA2-256SUMS"
                }
              ]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("yt-dlp-nightly-builds") -> nightlyJson
                    else -> stableJson
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val updater = EngineUpdater(context, mockClient)
        val initial = updater.getInstalledState()
        assertTrue(initial.isBundled)
        assertEquals(UpdateChannel.STABLE, initial.channel)

        val stableCheck = updater.checkForUpdates(UpdateChannel.STABLE).getOrThrow()
        assertTrue(stableCheck.hasUpdate)
        assertFalse(stableCheck.isChannelSwitch)
        assertEquals("2026.11.15", stableCheck.latestVersion)
        assertEquals("https://example.test/stable/yt-dlp", stableCheck.downloadUrl)

        val nightlyCheck = updater.checkForUpdates(UpdateChannel.NIGHTLY).getOrThrow()
        assertTrue(nightlyCheck.hasUpdate)
        assertTrue(nightlyCheck.isChannelSwitch)
        assertEquals("2026.11.18.232854", nightlyCheck.latestVersion)
    }

    @Test
    fun `download install verifies sha256 and supports rollback between stable and nightly`() = runTest {
        val stableBytes = createValidZipAppBytes(70 * 1024)
        val stableHash = sha256Hex(stableBytes)
        val nightlyBytes = createValidZipAppBytes(72 * 1024)
        val nightlyHash = sha256Hex(nightlyBytes)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val (bytes, mime) = when (url) {
                    "https://example.test/stable/yt-dlp" -> stableBytes to "application/octet-stream"
                    "https://example.test/stable/SHA2-256SUMS" -> "$stableHash  yt-dlp\n".toByteArray() to "text/plain"
                    "https://example.test/nightly/yt-dlp" -> nightlyBytes to "application/octet-stream"
                    "https://example.test/nightly/SHA2-256SUMS" -> "$nightlyHash  yt-dlp\n".toByteArray() to "text/plain"
                    else -> "{}".toByteArray() to "application/json"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes.toResponseBody(mime.toMediaType()))
                    .build()
            })
            .build()

        val updater = EngineUpdater(context, mockClient)

        val stableInfo = updater.parseReleaseJson(
            jsonString = """
                {
                  "tag_name": "2026.11.15",
                  "published_at": "2026-11-15T10:00:00Z",
                  "body": "Stable",
                  "html_url": "https://github.com/yt-dlp/yt-dlp/releases/tag/2026.11.15",
                  "assets": [
                    {"name": "yt-dlp", "size": ${stableBytes.size}, "browser_download_url": "https://example.test/stable/yt-dlp"},
                    {"name": "SHA2-256SUMS", "size": 100, "browser_download_url": "https://example.test/stable/SHA2-256SUMS"}
                  ]
                }
            """.trimIndent(),
            channel = UpdateChannel.STABLE,
            installedState = updater.getInstalledState()
        )

        val states = mutableListOf<UpdateInstallState>()
        val installedStable = updater.downloadAndInstallUpdate(stableInfo) { states.add(it) }.getOrThrow()
        assertEquals("2026.11.15", installedStable.version)
        assertEquals(UpdateChannel.STABLE, installedStable.channel)
        assertEquals(stableHash, installedStable.sha256)
        assertFalse(installedStable.isBundled)

        // Now switch & install Nightly
        val nightlyInfo = updater.parseReleaseJson(
            jsonString = """
                {
                  "tag_name": "2026.11.18.232854",
                  "published_at": "2026-11-18T23:30:00Z",
                  "body": "Nightly",
                  "html_url": "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/tag/2026.11.18.232854",
                  "assets": [
                    {"name": "yt-dlp", "size": ${nightlyBytes.size}, "browser_download_url": "https://example.test/nightly/yt-dlp"},
                    {"name": "SHA2-256SUMS", "size": 100, "browser_download_url": "https://example.test/nightly/SHA2-256SUMS"}
                  ]
                }
            """.trimIndent(),
            channel = UpdateChannel.NIGHTLY,
            installedState = installedStable
        )

        val installedNightly = updater.downloadAndInstallUpdate(nightlyInfo) {}.getOrThrow()
        assertEquals("2026.11.18.232854", installedNightly.version)
        assertEquals(UpdateChannel.NIGHTLY, installedNightly.channel)
        assertTrue(installedNightly.canRollback)
        assertEquals("2026.11.15", installedNightly.backupVersion)

        // Rollback back to Stable
        val rolledBack = updater.rollback().getOrThrow()
        assertEquals("2026.11.15", rolledBack.version)
        assertEquals(UpdateChannel.STABLE, rolledBack.channel)
        assertFalse(rolledBack.canRollback)
    }

    @Test
    fun `checksum mismatch rejects install and preserves existing version`() = runTest {
        val validBytes = createValidZipAppBytes(70 * 1024)
        val wrongHash = "0".repeat(64)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val (bytes, mime) = when (url) {
                    "https://example.test/yt-dlp" -> validBytes to "application/octet-stream"
                    "https://example.test/SHA2-256SUMS" -> "$wrongHash  yt-dlp\n".toByteArray() to "text/plain"
                    else -> "{}".toByteArray() to "application/json"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes.toResponseBody(mime.toMediaType()))
                    .build()
            })
            .build()

        val updater = EngineUpdater(context, mockClient)
        val info = updater.parseReleaseJson(
            jsonString = """
                {
                  "tag_name": "2026.11.18",
                  "assets": [
                    {"name": "yt-dlp", "size": ${validBytes.size}, "browser_download_url": "https://example.test/yt-dlp"},
                    {"name": "SHA2-256SUMS", "size": 100, "browser_download_url": "https://example.test/SHA2-256SUMS"}
                  ]
                }
            """.trimIndent(),
            channel = UpdateChannel.STABLE,
            installedState = updater.getInstalledState()
        )

        val result = updater.downloadAndInstallUpdate(info) {}
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("SHA-256 mismatch") == true)
        assertTrue(updater.getInstalledState().isBundled)
    }
}
