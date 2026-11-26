package com.exxhypex.hypexdownloader

import com.exxhypex.hypexdownloader.core.model.DownloadStatus
import com.exxhypex.hypexdownloader.core.model.DownloadTask
import com.exxhypex.hypexdownloader.core.model.FormatItem
import com.exxhypex.hypexdownloader.core.model.MediaMetadata
import com.exxhypex.hypexdownloader.core.model.PresetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDurationFormatting() {
        val media1 = MediaMetadata(
            id = "test1",
            title = "Test Video",
            author = "Tester",
            duration = 125L, // 2m 5s
            thumbnail = "",
            webpageUrl = ""
        )
        assertEquals("02:05", media1.formattedDuration)

        val media2 = MediaMetadata(
            id = "test2",
            title = "Long Video",
            author = "Tester",
            duration = 3665L, // 1h 1m 5s
            thumbnail = "",
            webpageUrl = ""
        )
        assertEquals("1:01:05", media2.formattedDuration)
    }

    @Test
    fun testDownloadTaskFormatting() {
        val task = DownloadTask(
            id = 1L,
            url = "https://example.com/video.mp4",
            title = "Video Title",
            thumbnail = "",
            formatId = "1080p",
            formatDescription = "1080p MP4",
            outputPath = "/path/to/file.mp4",
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = 5 * 1024 * 1024L,
            totalBytes = 20 * 1024 * 1024L,
            speed = 2 * 1024 * 1024L,
            eta = 45L
        )

        assertEquals("2.00 MB/s", task.formattedSpeed)
        assertEquals("5.00 MB", task.formattedDownloaded)
        assertEquals("20.00 MB", task.formattedTotal)
        assertEquals("45s", task.formattedEta)
    }

    @Test
    fun testFormatItemDisplay() {
        val videoFormat = FormatItem(
            formatId = "137",
            ext = "mp4",
            resolution = "1080p",
            vcodec = "avc1",
            acodec = "none",
            fps = 60.0,
            isVideoOnly = true,
            isAudioOnly = false
        )
        assertTrue(videoFormat.displayLabel.contains("Video only"))

        val audioFormat = FormatItem(
            formatId = "140",
            ext = "m4a",
            resolution = "audio",
            vcodec = "none",
            acodec = "mp4a",
            tbr = 128.0,
            isVideoOnly = false,
            isAudioOnly = true
        )
        assertTrue(audioFormat.displayLabel.contains("Audio"))
        assertTrue(audioFormat.displayLabel.contains("128 kbps"))
    }
}
