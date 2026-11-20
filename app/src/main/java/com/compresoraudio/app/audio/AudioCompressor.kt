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
        return listOf("-y", "-i", inputPath) + codecArgs + listOf(outputPath)
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

    data class CompressResult(val success: Boolean, val logTail: String)

    /**
     * Compresses [input] into [output]. Reports progress 0..1 via [onProgress]
     * when the input duration is known. On failure, [CompressResult.logTail]
     * carries the last lines of the ffmpeg output to help diagnose the error.
     */
    suspend fun compress(
        input: File,
        output: File,
        format: OutputFormat,
        quality: Quality,
        onProgress: (Float) -> Unit,
    ): CompressResult {
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
                    val log = errorExcerpt(s.output ?: "")
                    cont.resume(CompressResult(ReturnCode.isSuccess(s.returnCode), log))
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

    /**
     * Extracts the most useful lines from a full ffmpeg log: lines that look
     * like errors, or the last lines when nothing matches.
     */
    private fun errorExcerpt(log: String): String {
        val lines = log.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        val keywords = listOf(
            "error", "invalid", "failed", "unknown", "could not", "cannot",
            "unsupported", "conversion failed", "not found", "no such",
            "missing", "unable",
        )
        val relevant = lines.filter { l -> keywords.any { l.contains(it, ignoreCase = true) } }
        val picked = if (relevant.isNotEmpty()) relevant.takeLast(5) else lines.takeLast(5)
        return picked.joinToString("\n")
    }
}
