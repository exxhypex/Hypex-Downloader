package com.example.engine.ffmpeg

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.nio.ByteBuffer

/**
 * Native Android media remuxing.
 *
 * This copies already-encoded tracks; it does not transcode codecs. MP4 output
 * therefore requires codecs accepted by the device's MediaMuxer implementation.
 */
class MediaProcessor {
    private val activeFfmpegSessions = ConcurrentHashMap<Long, Long>()
    companion object {
        private const val TAG = "MediaProcessor"
        private const val DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024
        private const val MAX_BUFFER_SIZE = 32 * 1024 * 1024
    }

    private data class TrackCursor(
        val extractor: MediaExtractor,
        val muxerTrack: Int,
        var hasSample: Boolean = true
    ) {
        val timeUs: Long
            get() = if (hasSample) extractor.sampleTime else Long.MAX_VALUE
    }

    /**
     * Merge separate encoded video and audio files into an MP4 container.
     * Tracks are written in presentation-time order to keep interleaving valid.
     */
    suspend fun mergeVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false

        try {
            require(videoFile.isFile && videoFile.length() > 0L) { "Video input is missing or empty" }
            require(audioFile.isFile && audioFile.length() > 0L) { "Audio input is missing or empty" }
            require(outputFile.extension.equals("mp4", ignoreCase = true)) {
                "Native track muxing currently supports MP4 output only"
            }

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrackIndex = findTrack(videoExtractor, "video/")
                ?: throw IllegalArgumentException("No video track found in video input")
            val audioTrackIndex = findTrack(audioExtractor, "audio/")
                ?: throw IllegalArgumentException("No audio track found in audio input")

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
            val bufferSize = requiredBufferSize(videoFormat, audioFormat)

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Could not replace existing output file")
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoMuxerTrack = muxer.addTrack(videoFormat)
            val audioMuxerTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)
            val videoCursor = TrackCursor(videoExtractor, videoMuxerTrack)
            val audioCursor = TrackCursor(audioExtractor, audioMuxerTrack)
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val videoDuration = durationUs(videoFormat)
            val audioDuration = durationUs(audioFormat)
            val duration = maxOf(videoDuration, audioDuration).coerceAtLeast(1L)
            var lastProgress = -1

            while (videoCursor.hasSample || audioCursor.hasSample) {
                val cursor = when {
                    !audioCursor.hasSample -> videoCursor
                    !videoCursor.hasSample -> audioCursor
                    videoCursor.timeUs <= audioCursor.timeUs -> videoCursor
                    else -> audioCursor
                }

                buffer.clear()
                val sampleTimeUs = cursor.extractor.sampleTime
                val sampleSize = cursor.extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    cursor.hasSample = false
                    continue
                }

                bufferInfo.set(
                    0,
                    sampleSize,
                    sampleTimeUs,
                    mapSampleFlagsToBufferFlags(cursor.extractor.sampleFlags)
                )
                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(cursor.muxerTrack, buffer, bufferInfo)
                cursor.extractor.advance()
                cursor.hasSample = cursor.extractor.sampleTime >= 0L

                val progress = ((sampleTimeUs.toDouble() / duration) * 100.0)
                    .toInt().coerceIn(0, 99)
                if (progress != lastProgress) {
                    lastProgress = progress
                    onProgress?.invoke(progress / 100f)
                }
            }

            muxer.stop()
            muxerStarted = false
            if (!outputFile.isFile || outputFile.length() == 0L) {
                throw IllegalStateException("Muxer produced an empty output file")
            }
            success = true
            onProgress?.invoke(1f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Media merge failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            if (!success && outputFile.exists()) outputFile.delete()
        }
    }

    /**
     * Extracts an existing encoded audio track into an M4A container.
     * This is remuxing, not MP3 transcoding.
     */
    suspend fun extractAudio(
        videoFile: File,
        outputAudioFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false

        try {
            require(videoFile.isFile && videoFile.length() > 0L) { "Input video is missing or empty" }
            require(outputAudioFile.extension.equals("m4a", ignoreCase = true) ||
                outputAudioFile.extension.equals("mp4", ignoreCase = true)) {
                "Audio extraction output must use an M4A/MP4 container"
            }

            extractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val audioTrackIndex = findTrack(extractor, "audio/")
                ?: throw IllegalArgumentException("No audio track found in video")
            val audioFormat = extractor.getTrackFormat(audioTrackIndex)
            val buffer = ByteBuffer.allocateDirect(requiredBufferSize(audioFormat))

            outputAudioFile.parentFile?.mkdirs()
            if (outputAudioFile.exists() && !outputAudioFile.delete()) {
                throw IllegalStateException("Could not replace existing audio output")
            }

            muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true
            extractor.selectTrack(audioTrackIndex)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
                val sampleTimeUs = extractor.sampleTime
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.set(
                    0,
                    sampleSize,
                    sampleTimeUs,
                    mapSampleFlagsToBufferFlags(extractor.sampleFlags)
                )
                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            check(outputAudioFile.isFile && outputAudioFile.length() > 0L) {
                "Audio extraction produced an empty file"
            }
            success = true
            Result.success(outputAudioFile)
        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
            if (!success && outputAudioFile.exists()) outputAudioFile.delete()
        }
    }

    /**
     * Convert an audio file to MP3 using the bundled audio-only FFmpeg runtime.
     * The FFmpeg session is tracked by task ID so cancellation reaches the encoder.
     */
    suspend fun convertAudioToMp3(
        inputFile: File,
        outputFile: File,
        taskId: Long,
        bitrateKbps: Int = 192,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            require(inputFile.isFile && inputFile.length() > 0L) { "Audio input is missing or empty" }
            require(outputFile.extension.equals("mp3", ignoreCase = true)) {
                "MP3 conversion output must use the .mp3 extension"
            }
            require(bitrateKbps in 64..320) { "Unsupported MP3 bitrate: $bitrateKbps kbps" }

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Could not replace existing MP3 output")
            }

            val durationMs = readDurationMs(inputFile)
            val arguments = arrayOf(
                "-hide_banner", "-y", "-i", inputFile.absolutePath,
                "-map", "0:a:0", "-vn", "-c:a", "libmp3lame",
                "-b:a", "${bitrateKbps}k", "-id3v2_version", "3",
                outputFile.absolutePath
            )

            val conversionResult = suspendCancellableCoroutine<Result<File>> { continuation ->
                val cancelled = AtomicBoolean(false)
                var sessionId: Long? = null

                continuation.invokeOnCancellation {
                    cancelled.set(true)
                    sessionId?.let { FFmpegKit.cancel(it) }
                }

                val session = FFmpegKit.executeWithArgumentsAsync(
                    arguments,
                    { completed ->
                        activeFfmpegSessions.remove(taskId)
                        val result = if (ReturnCode.isSuccess(completed.returnCode) &&
                            outputFile.isFile && outputFile.length() > 0L
                        ) {
                            onProgress?.invoke(1f)
                            Result.success(outputFile)
                        } else {
                            val detail = completed.failStackTrace
                                ?: completed.allLogsAsString
                                ?: "FFmpeg MP3 conversion failed"
                            Result.failure(IllegalStateException(detail))
                        }
                        if (continuation.isActive) continuation.resume(result)
                    },
                    null,
                    { statistics ->
                        if (durationMs > 0L) {
                            onProgress?.invoke(
                                (statistics.time.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.99f)
                            )
                        }
                    }
                )

                sessionId = session.sessionId
                activeFfmpegSessions[taskId] = session.sessionId
                if (cancelled.get()) {
                    activeFfmpegSessions.remove(taskId)
                    FFmpegKit.cancel(session.sessionId)
                }
            }

            conversionResult.onFailure {
                if (outputFile.exists()) outputFile.delete()
            }
            conversionResult
        } catch (error: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(error)
        }
    }

    /** Cancel an active FFmpeg conversion associated with a download task. */
    fun cancelProcessing(taskId: Long) {
        activeFfmpegSessions.remove(taskId)?.let { sessionId ->
            FFmpegKit.cancel(sessionId)
        }
    }

    private fun readDurationMs(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .mapNotNull { index ->
                    val format = extractor.getTrackFormat(index)
                    if (format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                        runCatching { format.getLong(MediaFormat.KEY_DURATION) / 1_000L }.getOrNull()
                    } else null
                }
                .maxOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return index
        }
        return null
    }

    private fun durationUs(format: MediaFormat): Long =
        runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)

    private fun requiredBufferSize(vararg formats: MediaFormat): Int {
        val advertised = formats.mapNotNull { format ->
            runCatching {
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else null
            }.getOrNull()
        }.maxOrNull() ?: DEFAULT_BUFFER_SIZE

        require(advertised in 1..MAX_BUFFER_SIZE) {
            "Media sample buffer requirement is too large: $advertised bytes"
        }
        return maxOf(DEFAULT_BUFFER_SIZE, advertised)
    }

    private fun mapSampleFlagsToBufferFlags(sampleFlags: Int): Int {
        var bufferFlags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return bufferFlags
    }
}
