package com.example.engine.ffmpeg

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class MediaProcessor {
    companion object {
        private const val TAG = "MediaProcessor"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer
    }

    /**
     * Merges separate video and audio files into a single MP4 container
     * using Android's hardware/native MediaExtractor and MediaMuxer.
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

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            // Find video track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                return@withContext Result.failure(IllegalStateException("No valid video track found in source"))
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.parentFile?.mkdirs()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (audioTrackIndex != -1 && audioFormat != null) {
                muxer.addTrack(audioFormat)
            } else -1

            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy video track
            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = mapSampleFlagsToBufferFlags(videoExtractor.sampleFlags)
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy audio track if present
            if (audioTrackIndex != -1 && muxerAudioTrack != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    }
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = mapSampleFlagsToBufferFlags(audioExtractor.sampleFlags)
                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            onProgress?.invoke(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error merging media tracks: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (ignored: Exception) {}
            try { videoExtractor?.release() } catch (ignored: Exception) {}
            try { audioExtractor?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Extracts audio track from video file and saves as audio container (.m4a)
     */
    suspend fun extractAudio(
        videoFile: File,
        outputAudioFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(IllegalStateException("No audio track found in video"))
            }

            outputAudioFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()
            extractor.selectTrack(audioTrackIndex)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = mapSampleFlagsToBufferFlags(extractor.sampleFlags)
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            Result.success(outputAudioFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio: ${e.message}", e)
            Result.failure(e)
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
        }
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
