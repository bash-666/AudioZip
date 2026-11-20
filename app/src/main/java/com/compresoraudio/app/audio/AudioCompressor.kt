package com.compresoraudio.app.audio

import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Thin wrapper around ffmpeg-kit for audio transcoding. */
object AudioCompressor {

    fun argsFor(format: OutputFormat, quality: Quality, inputPath: String, outputPath: String): List<String> {
        val codecArgs = when (format) {
            OutputFormat.MP3 -> when (quality) {
                Quality.ALTA -> listOf("-c:a", "libmp3lame", "-b:a", "320k")
                Quality.MEDIA -> listOf("-c:a", "libmp3lame", "-b:a", "192k")
                Quality.BAJA -> listOf("-c:a", "libmp3lame", "-b:a", "128k")
            }
            OutputFormat.OGG -> when (quality) {
                Quality.ALTA -> listOf("-c:a", "libvorbis", "-q:a", "7")
                Quality.MEDIA -> listOf("-c:a", "libvorbis", "-q:a", "5")
                Quality.BAJA -> listOf("-c:a", "libvorbis", "-q:a", "3")
            }
            OutputFormat.OPUS -> when (quality) {
                Quality.ALTA -> listOf("-c:a", "libopus", "-b:a", "192k")
                Quality.MEDIA -> listOf("-c:a", "libopus", "-b:a", "128k")
                Quality.BAJA -> listOf("-c:a", "libopus", "-b:a", "96k")
            }
            OutputFormat.WAV -> listOf("-c:a", "pcm_s16le")
        }
        return listOf("-i", inputPath) + codecArgs + listOf(outputPath)
    }

    fun durationMsOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Compresses [input] into [output]. Reports progress 0..1 via [onProgress]
     * when the input duration is known. Returns true on success.
     */
    suspend fun compress(
        input: File,
        output: File,
        format: OutputFormat,
        quality: Quality,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val durationMs = durationMsOf(input)
        val args = argsFor(format, quality, input.absolutePath, output.absolutePath)
        return suspendCancellableCoroutine { cont ->
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                if (durationMs > 0) {
                    val p = (stats.time.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    onProgress(p)
                }
            }
            val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { s ->
                FFmpegKitConfig.enableStatisticsCallback { _ -> }
                if (cont.isActive) {
                    cont.resume(ReturnCode.isSuccess(s.returnCode))
                }
            }
            cont.invokeOnCancellation {
                FFmpegKit.cancel(session.sessionId)
            }
        }
    }

    fun cancelAll() {
        FFmpegKit.cancel()
    }
}
