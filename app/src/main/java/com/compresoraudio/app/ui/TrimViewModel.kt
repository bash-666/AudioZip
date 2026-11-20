package com.compresoraudio.app.ui

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.compresoraudio.app.audio.MIN_SEGMENT_MS
import com.compresoraudio.app.audio.OutputFormat
import com.compresoraudio.app.audio.Quality
import com.compresoraudio.app.audio.SafUtils
import com.compresoraudio.app.audio.TrimJoiner
import com.compresoraudio.app.audio.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TrimFileItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val cachedPath: String? = null,
    val peaks: FloatArray? = null,
    val waveLoading: Boolean = true,
    val selStartMs: Long = 0L,
    val selEndMs: Long = 0L,
)

data class TrimSegment(
    val id: Long,
    val fileId: Long,
    val fileName: String,
    val cachedPath: String,
    val startMs: Long,
    val endMs: Long,
)

data class TrimUiState(
    val files: List<TrimFileItem> = emptyList(),
    val segments: List<TrimSegment> = emptyList(),
    val selectedFileId: Long? = null,
    val outputFormat: OutputFormat = OutputFormat.MP3,
    val quality: Quality = Quality.MEDIA,
    val manualBitrateKbps: Int = 128,
    val outputTreeUri: Uri? = null,
    val outputFolderLabel: String = "Música",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val isPreviewing: Boolean = false,
    val previewPositionMs: Long? = null,
    val message: String = "",
    val exportedUri: Uri? = null,
    val exportedName: String? = null,
)

class TrimViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val _state = MutableStateFlow(TrimUiState())
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    private var nextFileId = 1L
    private var nextSegmentId = 1L
    private var exportJob: Job? = null
    private var previewPlayer: MediaPlayer? = null
    private val previewHandler = Handler(Looper.getMainLooper())

    private fun trimDir(): File = File(appContext.cacheDir, "trim").apply { mkdirs() }

    // ---------- Files ----------

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch {
            val newItems = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val (name, size) = SafUtils.queryNameAndSize(appContext, uri)
                    val dur = SafUtils.queryDurationMs(appContext, uri)
                    TrimFileItem(
                        id = nextFileId++,
                        uri = uri,
                        name = name,
                        sizeBytes = size,
                        durationMs = dur,
                        selEndMs = dur,
                    )
                }
            }
            _state.update { s ->
                s.copy(
                    files = s.files + newItems,
                    selectedFileId = s.selectedFileId ?: newItems.firstOrNull()?.id,
                )
            }
            newItems.forEach { loadWaveform(it) }
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            val uris = withContext(Dispatchers.IO) {
                SafUtils.listAudioFiles(appContext, treeUri)
            }
            if (uris.isNotEmpty()) addUris(uris)
        }
    }

    private fun loadWaveform(item: TrimFileItem) {
        viewModelScope.launch {
            // Phase 1: copy to cache so the file is usable right away, even
            // while the waveform is still being generated.
            val cachedPath = withContext(Dispatchers.IO) {
                try {
                    val ext = item.name.substringAfterLast('.', "bin").lowercase()
                    val cached = File(trimDir(), "in_${item.id}.$ext")
                    SafUtils.copyUriToFile(appContext, item.uri, cached)
                    cached.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            _state.update { s ->
                s.copy(
                    files = s.files.map { f ->
                        if (f.id == item.id) f.copy(cachedPath = cachedPath) else f
                    },
                    message = if (cachedPath == null) "No se pudo preparar ${item.name}" else s.message,
                )
            }
            if (cachedPath == null) {
                _state.update { s ->
                    s.copy(
                        files = s.files.map { f ->
                            if (f.id == item.id) f.copy(waveLoading = false) else f
                        }
                    )
                }
                return@launch
            }
            // Phase 2: waveform peaks (streaming, safe for long files).
            val peaks = withContext(Dispatchers.IO) {
                try {
                    TrimJoiner.extractPeaks(File(cachedPath))
                } catch (e: Exception) {
                    null
                }
            }
            _state.update { s ->
                s.copy(
                    files = s.files.map { f ->
                        if (f.id == item.id) f.copy(peaks = peaks, waveLoading = false) else f
                    }
                )
            }
        }
    }

    fun selectFile(id: Long) {
        stopPreview()
        _state.update { it.copy(selectedFileId = id) }
    }

    fun removeFile(id: Long) {
        if (_state.value.isExporting) return
        val s = _state.value
        if (s.files.firstOrNull { it.id == id }?.let { it.id == s.selectedFileId } == true) {
            stopPreview()
        }
        s.files.firstOrNull { it.id == id }?.cachedPath?.let { runCatching { File(it).delete() } }
        _state.update { st ->
            val files = st.files.filterNot { it.id == id }
            st.copy(
                files = files,
                segments = st.segments.filterNot { it.fileId == id },
                selectedFileId = if (st.selectedFileId == id) files.firstOrNull()?.id else st.selectedFileId,
            )
        }
    }

    fun clearAll() {
        if (_state.value.isExporting) return
        stopPreview()
        _state.value.files.forEach { f ->
            f.cachedPath?.let { runCatching { File(it).delete() } }
        }
        _state.update {
            it.copy(files = emptyList(), segments = emptyList(), selectedFileId = null, message = "")
        }
    }

    // ---------- Selection / waveform ----------

    fun setRange(fileId: Long, startMs: Long, endMs: Long) {
        _state.update { s ->
            s.copy(
                files = s.files.map { f ->
                    if (f.id == fileId && f.durationMs > 0) {
                        val end = endMs.coerceIn(MIN_SEGMENT_MS, f.durationMs)
                        val start = startMs.coerceIn(0L, end - MIN_SEGMENT_MS)
                        f.copy(selStartMs = start, selEndMs = end)
                    } else f
                }
            )
        }
    }

    fun resetSelection(fileId: Long) {
        _state.update { s ->
            s.copy(
                files = s.files.map { f ->
                    if (f.id == fileId) f.copy(selStartMs = 0L, selEndMs = f.durationMs) else f
                }
            )
        }
    }

    // ---------- Segments ----------

    fun addSegment() {
        val s = _state.value
        val file = s.files.firstOrNull { it.id == s.selectedFileId } ?: return
        addSegmentFor(file, file.selStartMs, file.selEndMs)
    }

    /** Adds the whole file as one segment, without trimming. */
    fun addWholeFile(fileId: Long) {
        val s = _state.value
        val file = s.files.firstOrNull { it.id == fileId } ?: return
        addSegmentFor(file, 0L, file.durationMs)
    }

    /** Adds every loaded file whole, in list order. */
    fun addAllFilesWhole() {
        val s = _state.value
        s.files.forEach { file -> addSegmentFor(file, 0L, file.durationMs) }
    }

    private fun addSegmentFor(file: TrimFileItem, startMs: Long, endMs: Long) {
        val cached = file.cachedPath ?: run {
            _state.update { it.copy(message = "Espera a que se cargue ${file.name}") }
            return
        }
        if (endMs - startMs < MIN_SEGMENT_MS) return
        val seg = TrimSegment(
            id = nextSegmentId++,
            fileId = file.id,
            fileName = file.name,
            cachedPath = cached,
            startMs = startMs,
            endMs = endMs,
        )
        _state.update {
            it.copy(
                segments = it.segments + seg,
                message = "Segmento agregado (${it.segments.size + 1})",
                exportedUri = null,
            )
        }
    }

    fun removeSegment(id: Long) {
        _state.update {
            it.copy(segments = it.segments.filterNot { it.id == id }, exportedUri = null)
        }
    }

    fun moveSegment(id: Long, up: Boolean) {
        _state.update { s ->
            val idx = s.segments.indexOfFirst { it.id == id }
            val target = if (up) idx - 1 else idx + 1
            if (idx < 0 || target < 0 || target >= s.segments.size) return@update s
            val segs = s.segments.toMutableList()
            val tmp = segs[idx]
            segs[idx] = segs[target]
            segs[target] = tmp
            s.copy(segments = segs)
        }
    }

    // ---------- Preview ----------

    /** Plays the current selection in a loop so the user can check the cut. */
    fun togglePreview() {
        if (_state.value.isPreviewing) {
            stopPreview()
            return
        }
        val s = _state.value
        val file = s.files.firstOrNull { it.id == s.selectedFileId } ?: return
        if (file.selEndMs - file.selStartMs < MIN_SEGMENT_MS) return
        stopPreview()
        val player = MediaPlayer()
        previewPlayer = player
        _state.update { it.copy(isPreviewing = true, previewPositionMs = file.selStartMs) }
        try {
            player.setDataSource(appContext, file.uri)
            player.setOnPreparedListener { p ->
                p.seekTo(file.selStartMs.toInt())
                p.start()
                schedulePreviewLoop(p, file.selStartMs, file.selEndMs)
            }
            player.setOnErrorListener { _, _, _ ->
                stopPreview()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            stopPreview()
        }
    }

    private fun schedulePreviewLoop(player: MediaPlayer, startMs: Long, endMs: Long) {
        val check = object : Runnable {
            override fun run() {
                if (previewPlayer !== player) return
                val pos = runCatching { player.currentPosition }.getOrDefault(0)
                _state.update { it.copy(previewPositionMs = pos.toLong()) }
                if (pos >= endMs.toInt()) {
                    runCatching { player.seekTo(startMs.toInt()) }
                }
                previewHandler.postDelayed(this, 100)
            }
        }
        previewHandler.postDelayed(check, 100)
    }

    fun stopPreview() {
        previewHandler.removeCallbacksAndMessages(null)
        runCatching { previewPlayer?.stop() }
        runCatching { previewPlayer?.release() }
        previewPlayer = null
        if (_state.value.isPreviewing || _state.value.previewPositionMs != null) {
            _state.update { it.copy(isPreviewing = false, previewPositionMs = null) }
        }
    }

    // ---------- Export ----------

    fun setOutputFormat(format: OutputFormat) {
        _state.update { it.copy(outputFormat = format) }
    }

    fun setQuality(quality: Quality) {
        _state.update { it.copy(quality = quality) }
    }

    fun setManualBitrate(kbps: Int) {
        _state.update { it.copy(manualBitrateKbps = kbps) }
    }

    fun setOutputFolder(uri: Uri) {
        val label = uri.lastPathSegment?.substringAfterLast(':') ?: "Carpeta seleccionada"
        _state.update { it.copy(outputTreeUri = uri, outputFolderLabel = label) }
    }

    fun export() {
        val s = _state.value
        if (s.isExporting || s.segments.isEmpty()) return
        stopPreview()
        val ts = System.currentTimeMillis()
        val workDir = File(appContext.cacheDir, "trim_work_$ts").apply { mkdirs() }
        val outFile = File(appContext.cacheDir, "trim_out_$ts.${s.outputFormat.ext}")
        val firstName = s.segments.first().fileName.substringBeforeLast('.', s.segments.first().fileName)
        val outName = "${firstName}_unido.${s.outputFormat.ext}"
        val specs = s.segments.map {
            TrimJoiner.SegmentSpec(File(it.cachedPath), it.startMs, it.endMs)
        }
        val format = s.outputFormat
        val quality = s.quality
        val bitrate = s.manualBitrateKbps
        val treeUri = s.outputTreeUri
        exportJob = viewModelScope.launch {
            _state.update {
                it.copy(isExporting = true, exportProgress = 0f, message = "", exportedUri = null)
            }
            try {
                val ok = withContext(Dispatchers.IO) {
                    TrimJoiner.exportJoin(workDir, specs, format, quality, bitrate, outFile) { p ->
                        _state.update { it.copy(exportProgress = p) }
                    }
                }
                if (!ok || !outFile.exists()) {
                    runCatching { outFile.delete() }
                    _state.update {
                        it.copy(
                            isExporting = false,
                            message = "No se pudo exportar. Revisa los segmentos e inténtalo de nuevo.",
                        )
                    }
                    return@launch
                }
                val outSize = outFile.length()
                val written = withContext(Dispatchers.IO) {
                    if (treeUri != null) {
                        val docUri = SafUtils.createOutputDocument(
                            appContext, treeUri, outName, format.mime,
                        )
                        if (docUri != null) {
                            SafUtils.copyFileToUri(appContext, outFile, docUri)
                            docUri
                        } else null
                    } else {
                        SafUtils.writeToMusic(appContext, outName, format.mime, outFile)
                    }
                }
                runCatching { outFile.delete() }
                _state.update {
                    it.copy(
                        isExporting = false,
                        exportedUri = written,
                        exportedName = outName,
                        message = if (written != null) {
                            "Listo: $outName (${formatBytes(outSize)})"
                        } else {
                            "Exportado, pero no se pudo guardar en la carpeta de salida."
                        },
                    )
                }
            } catch (e: CancellationException) {
                runCatching { outFile.delete() }
                _state.update {
                    it.copy(isExporting = false, message = "Exportación cancelada.")
                }
                throw e
            }
        }
    }

    fun cancelExport() {
        if (!_state.value.isExporting) return
        FFmpegKit.cancel()
        exportJob?.cancel()
    }

    fun shareIntentForExport(): Intent? {
        val s = _state.value
        val uri = s.exportedUri ?: return null
        val name = s.exportedName ?: "audio"
        return Intent(Intent.ACTION_SEND).apply {
            type = s.outputFormat.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCleared() {
        stopPreview()
        exportJob?.cancel()
        super.onCleared()
    }
}
