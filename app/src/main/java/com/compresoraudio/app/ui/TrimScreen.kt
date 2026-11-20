package com.compresoraudio.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.compresoraudio.app.audio.MIN_SEGMENT_MS
import com.compresoraudio.app.audio.OutputFormat
import com.compresoraudio.app.audio.Quality
import com.compresoraudio.app.audio.formatBytes
import com.compresoraudio.app.audio.formatMs
import kotlin.math.abs
import kotlin.math.round

@Composable
fun TrimScreen(viewModel: TrimViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.addUris(uris)
        }
    }

    val pickInputFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.addFolder(uri)
        }
    }

    val pickOutputFolder = rememberLauncherForActivityResult(
        MusicFolderPickerContract(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setOutputFolder(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        StepHeader("1", "Elige los audios")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { pickFiles.launch(arrayOf("audio/*")) },
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Agregar audios")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickInputFolder.launch(null) },
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Agregar carpeta")
        }
        if (state.files.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Puedes mezclar formatos: mp3, ogg, opus, wav, flac y m4a. " +
                    "Los segmentos se unen en un solo archivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.files.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            StepHeader("2", "Recorta la sección")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Arrastra los marcadores de la onda para elegir qué parte guardar. " +
                    "Toca la onda para mover el marcador más cercano. " +
                    "Si no quieres recortar, toca «Agregar audio completo a la lista».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.files.forEach { file ->
                    FilterChip(
                        selected = file.id == state.selectedFileId,
                        onClick = { viewModel.selectFile(file.id) },
                        label = { Text(file.name, maxLines = 1) },
                    )
                }
            }
            if (state.files.size > 1) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.addAllFilesWhole() },
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Agregar todos los audios completos")
                }
            }

            val selected = state.files.firstOrNull { it.id == state.selectedFileId }
            if (selected != null) {
                Spacer(Modifier.height(12.dp))
                TrimEditorCard(
                    file = selected,
                    isPreviewing = state.isPreviewing,
                    previewPositionMs = state.previewPositionMs,
                    isExporting = state.isExporting,
                    onRangeChange = { s, e -> viewModel.setRange(selected.id, s, e) },
                    onReset = { viewModel.resetSelection(selected.id) },
                    onTogglePreview = { viewModel.togglePreview() },
                    onAddSegment = { viewModel.addSegment() },
                    onAddWholeFile = { viewModel.addWholeFile(selected.id) },
                    onRemoveFile = { viewModel.removeFile(selected.id) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StepHeader("3", "Segmentos para unir")
        Spacer(Modifier.height(8.dp))
        SegmentsCard(
            segments = state.segments,
            isExporting = state.isExporting,
            onMove = { id, up -> viewModel.moveSegment(id, up) },
            onRemove = { viewModel.removeSegment(it) },
            onClearAll = { viewModel.clearAll() },
            hasFiles = state.files.isNotEmpty(),
        )

        Spacer(Modifier.height(20.dp))
        StepHeader("4", "Exporta")
        Spacer(Modifier.height(8.dp))
        ExportCard(
            state = state,
            onFormat = { viewModel.setOutputFormat(it) },
            onQuality = { viewModel.setQuality(it) },
            onBitrate = { viewModel.setManualBitrate(it) },
            onPickOutput = { pickOutputFolder.launch(Unit) },
            onExport = { viewModel.export() },
            onCancel = { viewModel.cancelExport() },
            onShare = { viewModel.shareIntentForExport()?.let { context.startActivity(it) } },
        )

        if (state.message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepHeader(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TrimEditorCard(
    file: TrimFileItem,
    isPreviewing: Boolean,
    previewPositionMs: Long?,
    isExporting: Boolean,
    onRangeChange: (Long, Long) -> Unit,
    onReset: () -> Unit,
    onTogglePreview: () -> Unit,
    onAddSegment: () -> Unit,
    onAddWholeFile: () -> Unit,
    onRemoveFile: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemoveFile, enabled = !isExporting) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar archivo")
                }
            }
            Text(
                text = "${formatBytes(file.sizeBytes)} · duración ${formatMs(file.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when {
                file.peaks != null -> {
                    WaveformView(
                        peaks = file.peaks,
                        durationMs = file.durationMs,
                        startMs = file.selStartMs,
                        endMs = file.selEndMs,
                        playheadMs = previewPositionMs,
                        onRangeChange = onRangeChange,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(
                            label = "Inicio",
                            valueMs = file.selStartMs,
                            fileKey = file.id,
                            onApply = { onRangeChange(it, file.selEndMs) },
                            modifier = Modifier.weight(1f),
                        )
                        TimeField(
                            label = "Fin",
                            valueMs = file.selEndMs,
                            fileKey = file.id * 10 + 1,
                            onApply = { onRangeChange(file.selStartMs, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Duración de la selección: ${formatMs(file.selEndMs - file.selStartMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                file.waveLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Cargando onda…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No se pudo generar la onda. Puedes usar el archivo completo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTogglePreview,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(if (isPreviewing) "Pausar vista previa" else "Escuchar selección")
            }
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = onReset, enabled = !isExporting) {
                    Text("Seleccionar todo")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemoveFile, enabled = !isExporting) {
                    Text("Quitar archivo")
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddSegment,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Agregar recorte a la lista")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddWholeFile,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Agregar audio completo a la lista")
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    valueMs: Long,
    fileKey: Long,
    onApply: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(fileKey) { mutableStateOf(formatMs(valueMs)) }
    var focused by remember(fileKey) { mutableStateOf(false) }
    LaunchedEffect(valueMs) {
        if (!focused) text = formatMs(valueMs)
    }
    fun commit() {
        val parsed = parseMs(text)
        if (parsed != null) onApply(parsed) else text = formatMs(valueMs)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier.onFocusChanged { state ->
            focused = state.isFocused
            if (!state.isFocused) commit()
        },
    )
}

/** Parses "m:ss.mmm", "m:ss", "ss.mmm" or plain seconds into milliseconds. */
private fun parseMs(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return try {
        val parts = t.split(":")
        when (parts.size) {
            1 -> (parts[0].toDouble() * 1000).toLong()
            2 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toDouble()
                minutes * 60_000L + (seconds * 1000).toLong()
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun SegmentsCard(
    segments: List<TrimSegment>,
    isExporting: Boolean,
    onMove: (Long, Boolean) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
    hasFiles: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val totalMs = segments.sumOf { it.endMs - it.startMs }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Segmentos (${segments.size})" +
                        if (segments.isNotEmpty()) " · total ${formatMs(totalMs)}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (segments.isNotEmpty() && hasFiles) {
                    TextButton(onClick = onClearAll, enabled = !isExporting) {
                        Text("Quitar todo")
                    }
                }
            }
            if (segments.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Toca «Agregar audio completo a la lista» para unir un audio entero, " +
                        "o recorta una sección y toca «Agregar recorte a la lista». " +
                        "El orden de la lista es el orden del archivo final.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                segments.forEachIndexed { idx, seg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = seg.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = "${formatMs(seg.startMs)} → ${formatMs(seg.endMs)} " +
                                    "(${formatMs(seg.endMs - seg.startMs)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onMove(seg.id, true) },
                            enabled = !isExporting && idx > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
                        }
                        IconButton(
                            onClick = { onMove(seg.id, false) },
                            enabled = !isExporting && idx < segments.size - 1,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar")
                        }
                        IconButton(onClick = { onRemove(seg.id) }, enabled = !isExporting) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar segmento")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCard(
    state: TrimUiState,
    onFormat: (OutputFormat) -> Unit,
    onQuality: (Quality) -> Unit,
    onBitrate: (Int) -> Unit,
    onPickOutput: () -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatDropdown(
                    label = "Formato",
                    selected = state.outputFormat.label,
                    options = OutputFormat.entries.map { it.label },
                    onSelect = { label ->
                        OutputFormat.entries.firstOrNull { it.label == label }?.let(onFormat)
                    },
                    modifier = Modifier.weight(1f),
                )
                FormatDropdown(
                    label = "Calidad",
                    selected = state.quality.label,
                    options = Quality.entries.map { it.label },
                    onSelect = { label ->
                        Quality.entries.firstOrNull { it.label == label }?.let(onQuality)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.quality == Quality.MANUAL &&
                state.outputFormat != OutputFormat.WAV &&
                state.outputFormat != OutputFormat.FLAC
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Bitrate: ${state.manualBitrateKbps} kbps",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.manualBitrateKbps.toFloat(),
                    onValueChange = { v ->
                        val snapped = ((v - 32f) / 8f).let { round(it).toInt() } * 8 + 32
                        onBitrate(snapped)
                    },
                    valueRange = 32f..320f,
                    steps = 35,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Salida: ${state.outputFolderLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickOutput, enabled = !state.isExporting) {
                Text("Cambiar carpeta de salida")
            }
            Spacer(Modifier.height(12.dp))
            if (state.isExporting) {
                LinearProgressIndicator(
                    progress = { state.exportProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport, enabled = false, modifier = Modifier.weight(1f)) {
                        Text("Exportando… ${(state.exportProgress * 100).toInt()}%")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancelar")
                    }
                }
            } else {
                Button(
                    onClick = onExport,
                    enabled = state.segments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exportar unión")
                }
                if (state.exportedUri != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Compartir ${state.exportedName ?: ""}")
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformView(
    peaks: FloatArray,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long?,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleTouchPx = with(density) { 40.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val selColor = primary.copy(alpha = 0.12f)
    val playheadColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(peaks, durationMs) {
                awaitEachGesture {
                    if (widthPx <= 0f || durationMs <= 0L) return@awaitEachGesture
                    val down = awaitFirstDown()
                    val x = down.position.x
                    val startX = startMs.toFloat() / durationMs * widthPx
                    val endX = endMs.toFloat() / durationMs * widthPx
                    val dragging = when {
                        abs(x - startX) <= handleTouchPx -> 1
                        abs(x - endX) <= handleTouchPx -> 2
                        else -> 0
                    }
                    if (dragging == 0) {
                        val t = (x / widthPx * durationMs).toLong()
                        if (abs(t - startMs) <= abs(t - endMs)) {
                            onRangeChange(t.coerceIn(0L, endMs - MIN_SEGMENT_MS), endMs)
                        } else {
                            onRangeChange(startMs, t.coerceIn(startMs + MIN_SEGMENT_MS, durationMs))
                        }
                        return@awaitEachGesture
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val t = (change.position.x / widthPx * durationMs)
                            .toLong().coerceIn(0L, durationMs)
                        if (dragging == 1) {
                            onRangeChange(t.coerceIn(0L, endMs - MIN_SEGMENT_MS), endMs)
                        } else {
                            onRangeChange(startMs, t.coerceIn(startMs + MIN_SEGMENT_MS, durationMs))
                        }
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val startX = if (durationMs > 0) startMs.toFloat() / durationMs * w else 0f
            val endX = if (durationMs > 0) endMs.toFloat() / durationMs * w else w
            drawRect(
                color = selColor,
                topLeft = Offset(startX, 0f),
                size = Size((endX - startX).coerceAtLeast(0f), h),
            )
            val n = peaks.size
            val barW = w / n
            val midY = h / 2f
            for (i in 0 until n) {
                val x = i * barW
                val amp = (peaks[i] * h * 0.45f).coerceAtLeast(1.5f)
                val inSel = x >= startX && x <= endX
                drawLine(
                    color = if (inSel) primary else dim.copy(alpha = 0.55f),
                    start = Offset(x + barW / 2f, midY - amp),
                    end = Offset(x + barW / 2f, midY + amp),
                    strokeWidth = (barW * 0.6f).coerceAtLeast(1.5f),
                )
            }
            drawLine(primary, Offset(startX, 0f), Offset(startX, h), strokeWidth = 3f)
            drawLine(primary, Offset(endX, 0f), Offset(endX, h), strokeWidth = 3f)
            drawCircle(primary, radius = 9f, center = Offset(startX, 16f))
            drawCircle(primary, radius = 9f, center = Offset(endX, 16f))
            if (playheadMs != null && durationMs > 0) {
                val px = playheadMs.toFloat() / durationMs * w
                drawLine(playheadColor, Offset(px, 0f), Offset(px, h), strokeWidth = 2.5f)
            }
        }
    }
}
