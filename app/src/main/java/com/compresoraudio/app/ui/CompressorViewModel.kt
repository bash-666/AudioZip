package com.compresoraudio.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.compresoraudio.app.audio.AudioCompressor
import com.compresoraudio.app.audio.AudioItem
import com.compresoraudio.app.audio.OutputFormat
import com.compresoraudio.app.audio.Quality
import com.compresoraudio.app.audio.SafUtils
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

data class CompressorUiState(
    val items: List<AudioItem> = emptyList(),
    val format: OutputFormat = OutputFormat.MP3,
    val quality: Quality = Quality.MEDIA,
    val outputTreeUri: Uri? = null,
    val outputFolderLabel: String = "Carpeta de la app (comprimidos)",
    val isRunning: Boolean = false,
    val message: String = "",
)

class CompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CompressorUiState())
    val state: StateFlow<CompressorUiState> = _state.asStateFlow()

    private var nextId = 1L
    private var compressJob: Job? = null

    private val appContext get() = getApplication<Application>().applicationContext

    fun setFormat(format: OutputFormat) = _state.update { it.copy(format = format) }
    fun setQuality(quality: Quality) = _state.update { it.copy(quality = quality) }

    fun addUris(uris: List<Uri>) {
        val existing = _state.value.items.map { it.uri.toString() }.toSet()
        val newItems = uris
            .filter { it.toString() !in existing }
            .mapNotNull { uri ->
                val (name, size) = SafUtils.queryNameAndSize(appContext, uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in OutputFormat.supportedInputExts) return@mapNotNull null
                AudioItem(id = nextId++, uri = uri, name = name, sizeBytes = size)
            }
        if (newItems.isNotEmpty()) {
            _state.update { it.copy(items = it.items + newItems, message = "") }
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                SafUtils.listAudioFiles(appContext, treeUri)
            }
            if (files.isEmpty()) {
                _state.update { it.copy(message = "No se encontraron audios en esa carpeta.") }
            } else {
                addUris(files)
            }
        }
    }

    fun setOutputFolder(treeUri: Uri) {
        val label = DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast('/')
            .ifBlank { "Carpeta elegida" }
        _state.update {
            it.copy(
                outputTreeUri = treeUri,
                outputFolderLabel = label,
                message = "Los archivos saldrán con su nombre original en esa carpeta.",
            )
        }
    }

    fun removeItem(id: Long) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
    }

    fun clearAll() {
        if (_state.value.isRunning) return
        _state.update { it.copy(items = emptyList(), message = "") }
    }

    fun cancel() {
        AudioCompressor.cancelAll()
        compressJob?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun compressAll() {
        if (_state.value.isRunning) return
        val pending = _state.value.items.filter { it.status != AudioItem.Status.DONE }
        if (pending.isEmpty()) return
        compressJob = viewModelScope.launch {
            _state.update { it.copy(isRunning = true, message = "") }
            val format = _state.value.format
            val quality = _state.value.quality
            val outputTree = _state.value.outputTreeUri
            for (item in pending) {
                updateItem(item.id) { it.copy(status = AudioItem.Status.WORKING, progress = 0f, error = null, errorDetail = null) }
                val result = try {
                    processItem(item, format, quality, outputTree)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ItemResult(false)
                }
                if (!result.ok) {
                    updateItem(item.id) {
                        it.copy(
                            status = AudioItem.Status.ERROR,
                            error = "No se pudo comprimir",
                            errorDetail = result.logTail.ifBlank { null },
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    isRunning = false,
                    message = "Listo. Los archivos conservan su nombre original.",
                )
            }
        }
    }

    private data class ItemResult(val ok: Boolean, val logTail: String = "")

    private suspend fun processItem(
        item: AudioItem,
        format: OutputFormat,
        quality: Quality,
        outputTree: Uri?,
    ): ItemResult = withContext(Dispatchers.IO) {
        val cacheDir = File(appContext.cacheDir, "compress").apply { mkdirs() }
        val inputFile = File(cacheDir, "in_${item.id}_${item.name}")
        val outputFile = File(cacheDir, "out_${item.id}.${format.ext}")
        try {
            SafUtils.copyUriToFile(appContext, item.uri, inputFile)
            val result = AudioCompressor.compress(inputFile, outputFile, format, quality) { p ->
                updateItem(item.id) { it.copy(progress = p) }
            }
            if (!result.success || !outputFile.exists()) {
                return@withContext ItemResult(false, result.logTail)
            }

            val outName = item.outputNameFor(format)
            val outputUri: Uri = if (outputTree != null) {
                val docUri = SafUtils.createOutputDocument(appContext, outputTree, outName, format.mime)
                    ?: return@withContext ItemResult(false)
                SafUtils.copyFileToUri(appContext, outputFile, docUri)
                docUri
            } else {
                val dir = File(appContext.getExternalFilesDir(null), "comprimidos").apply { mkdirs() }
                val dest = File(dir, outName)
                outputFile.copyTo(dest, overwrite = true)
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    dest,
                )
            }
            updateItem(item.id) {
                it.copy(
                    status = AudioItem.Status.DONE,
                    progress = 1f,
                    outputSizeBytes = outputFile.length(),
                    outputUri = outputUri,
                    outputName = outName,
                )
            }
            ItemResult(true)
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun updateItem(id: Long, transform: (AudioItem) -> AudioItem) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == id) transform(it) else it })
        }
    }

    fun shareIntentFor(item: AudioItem): Intent? {
        val uri = item.outputUri ?: return null
        val ext = item.outputName?.substringAfterLast('.', "")?.lowercase() ?: ""
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir audio")
    }
}
