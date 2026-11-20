package com.compresoraudio.app.audio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Trim + join engine built on ffmpeg-kit.
 *
 * Joining audios of different formats works by decoding every segment to the
 * same PCM WAV parameters, trimming each one, concatenating the PCM files
 * (stream copy, safe because they share format/rate/channels) and finally
 * encoding the joined WAV to the chosen output format.
 *
 * All functions block; call them from a background dispatcher.
 */
object TrimJoiner {

    private const val PCM_RATE = 44100
    private const val PCM_CHANNELS = 2

    data class SegmentSpec(
        val input: File,
        val startMs: Long,
        val endMs: Long,
    )

    private fun runFfmpeg(args: List<String>): Boolean {
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        return ReturnCode.isSuccess(session.returnCode)
    }

    private fun sec(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    /**
     * Decodes [input] to raw mono PCM and reduces it to [buckets] peak values
     * (0..1) for waveform display. Streams the PCM file in chunks so long
     * files do not load entirely into memory. Returns null on failure.
     */
    fun extractPeaks(input: File, buckets: Int = 240): FloatArray? {
        val pcm = File(input.parentFile, input.nameWithoutExtension + "_wave.pcm")
        val ok = runFfmpeg(
            listOf(
                "-y", "-vn", "-sn", "-dn", "-i", input.absolutePath,
                "-ac", "1", "-ar", "11025", "-f", "s16le", pcm.absolutePath,
            )
        )
        if (!ok || !pcm.exists()) return null
        try {
            val totalSamples = pcm.length() / 2
            if (totalSamples <= 0) return null
            val peaks = FloatArray(buckets)
            val bucketSize = maxOf(1L, totalSamples / buckets)
            var sampleIdx = 0L
            var bucket = 0
            var bucketMax = 0
            val buf = ByteArray(8192)
            BufferedInputStream(pcm.inputStream(), 65536).use { stream ->
                var read: Int
                while (stream.read(buf).also { read = it } > 0) {
                    var i = 0
                    while (i + 1 < read) {
                        val lo = buf[i].toInt() and 0xFF
                        val hi = buf[i + 1].toInt()
                        val v = abs((hi shl 8) or lo)
                        if (v > bucketMax) bucketMax = v
                        sampleIdx++
                        val b = (sampleIdx / bucketSize).toInt()
                        if (b > bucket) {
                            peaks[bucket] = bucketMax / 32768f
                            bucket = b.coerceAtMost(buckets - 1)
                            bucketMax = 0
                        }
                        i += 2
                    }
                }
            }
            peaks[bucket] = bucketMax / 32768f
            return peaks
        } finally {
            pcm.delete()
        }
    }

    /**
     * Trims every segment to normalized PCM, concatenates them and encodes the
     * result to [format] at [outFile]. Reports progress 0..1 via [onProgress].
     * Returns true on success. All temp files are removed from [workDir].
     */
    fun exportJoin(
        workDir: File,
        segments: List<SegmentSpec>,
        format: OutputFormat,
        quality: Quality,
        bitrateKbps: Int,
        outFile: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        workDir.mkdirs()
        try {
            val segFiles = mutableListOf<File>()
            segments.forEachIndexed { idx, seg ->
                val out = File(workDir, "seg_$idx.wav")
                val durMs = (seg.endMs - seg.startMs).coerceAtLeast(MIN_SEGMENT_MS)
                val ok = runFfmpeg(
                    listOf(
                        "-y", "-vn", "-sn", "-dn",
                        "-ss", sec(seg.startMs), "-t", sec(durMs),
                        "-i", seg.input.absolutePath,
                        "-ac", PCM_CHANNELS.toString(), "-ar", PCM_RATE.toString(),
                        "-c:a", "pcm_s16le", out.absolutePath,
                    )
                )
                if (!ok) return false
                segFiles.add(out)
                onProgress(0.7f * (idx + 1) / segments.size)
            }
            val joined = File(workDir, "joined.wav")
            if (segFiles.size == 1) {
                segFiles[0].copyTo(joined, overwrite = true)
            } else {
                val list = File(workDir, "list.txt")
                list.writeText(segFiles.joinToString("\n") { "file '${it.absolutePath}'" })
                val ok = runFfmpeg(
                    listOf(
                        "-y", "-f", "concat", "-safe", "0",
                        "-i", list.absolutePath, "-c", "copy", joined.absolutePath,
                    )
                )
                if (!ok) return false
            }
            onProgress(0.8f)
            val ok = if (format == OutputFormat.WAV) {
                joined.copyTo(outFile, overwrite = true)
                true
            } else {
                runFfmpeg(
                    AudioCompressor.argsFor(
                        format, quality, joined.absolutePath, outFile.absolutePath, bitrateKbps,
                    )
                )
            }
            if (ok) onProgress(1f)
            return ok
        } finally {
            workDir.deleteRecursively()
        }
    }
}
