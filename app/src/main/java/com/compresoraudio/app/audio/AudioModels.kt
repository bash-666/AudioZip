package com.compresoraudio.app.audio

import android.net.Uri

enum class OutputFormat(val label: String, val ext: String, val mime: String) {
    MP3("MP3", "mp3", "audio/mpeg"),
    OGG("OGG (Vorbis)", "ogg", "audio/ogg"),
    OPUS("Opus", "opus", "audio/ogg"),
    WAV("WAV", "wav", "audio/wav");

    companion object {
        val supportedInputExts = setOf("mp3", "ogg", "opus", "wav")
    }
}

enum class Quality(val label: String) {
    ALTA("Alta"),
    MEDIA("Media"),
    BAJA("Baja"),
    MANUAL("Manual"),
}

data class AudioItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val status: Status = Status.PENDING,
    val progress: Float = 0f,
    val outputSizeBytes: Long? = null,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val error: String? = null,
    val errorDetail: String? = null,
    val durationMs: Long = 0L,
) {
    enum class Status { PENDING, WORKING, DONE, ERROR }

    val baseName: String
        get() = name.substringBeforeLast('.', name)

    fun outputNameFor(format: OutputFormat): String = "$baseName.${format.ext}"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
}

/**
 * Approximate output size for a lossy encode at [bitrateKbps] over [durationMs].
 * CBR/ABR encoders make this close; VBR varies with content. Returns 0 when
 * the duration is unknown.
 */
fun estimateOutputBytes(bitrateKbps: Int, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    return bitrateKbps.toLong() * durationMs / 8L + 2048L
}

/** True when the estimated output would actually be smaller than the original. */
fun isPositiveCompression(originalBytes: Long, estimatedBytes: Long): Boolean =
    estimatedBytes > 0 && estimatedBytes < originalBytes
