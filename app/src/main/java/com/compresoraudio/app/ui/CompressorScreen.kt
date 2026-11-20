package com.compresoraudio.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.compresoraudio.app.audio.AudioItem
import com.compresoraudio.app.audio.OutputFormat
import com.compresoraudio.app.audio.Quality
import com.compresoraudio.app.audio.UpdateChecker
import com.compresoraudio.app.audio.estimateOutputBytes
import com.compresoraudio.app.audio.formatBytes
import com.compresoraudio.app.audio.isPositiveCompression
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(viewModel: CompressorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trimViewModel: TrimViewModel = viewModel()
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showUpdate by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    fun currentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun checkForUpdates() {
        val current = currentVersion()
        updateState = UpdateUiState(phase = UpdatePhase.CHECKING, currentVersion = current)
        scope.launch {
            val info = UpdateChecker.checkLatest()
            updateState = when {
                info == null -> UpdateUiState(
                    phase = UpdatePhase.ERROR,
                    currentVersion = current,
                    errorMessage = "No se pudo comprobar. Revisa tu conexión e inténtalo de nuevo.",
                )
                UpdateChecker.isNewerVersion(info.version, current) -> UpdateUiState(
                    phase = UpdatePhase.AVAILABLE,
                    currentVersion = current,
                    release = info,
                )
                else -> UpdateUiState(
                    phase = UpdatePhase.UP_TO_DATE,
                    currentVersion = current,
                    release = info,
                )
            }
        }
    }

    fun startUpdateDownload() {
        val info = updateState.release ?: return
        val url = info.apkUrl
        if (url == null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(UpdateChecker.installPermissionIntent(context))
            updateState = updateState.copy(
                phase = UpdatePhase.ERROR,
                errorMessage = "Concede el permiso de instalar apps desconocidas a AudioZip " +
                    "y vuelve a intentarlo.",
            )
            return
        }
        downloadId = UpdateChecker.enqueueDownload(context, url, "AudioZip-${info.version}.apk")
        updateState = updateState.copy(phase = UpdatePhase.DOWNLOADING, downloadProgress = -1f)
    }

    fun installUpdate() {
        val id = downloadId ?: return
        UpdateChecker.installIntent(context, id)?.let { context.startActivity(it) }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            when (UpdateChecker.downloadStatus(context, id)) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                    updateState = updateState.copy(phase = UpdatePhase.DOWNLOADED)
                    break
                }
                android.app.DownloadManager.STATUS_FAILED -> {
                    updateState = updateState.copy(
                        phase = UpdatePhase.ERROR,
                        errorMessage = "La descarga falló. Inténtalo de nuevo.",
                    )
                    break
                }
            }
            val p = UpdateChecker.downloadProgress(context, id)
            if (p != null && p.second > 0) {
                updateState = updateState.copy(downloadProgress = p.first.toFloat() / p.second.toFloat())
            }
            delay(500)
        }
    }

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

    if (showHelp) {
        HelpDialog(
            onDismiss = { showHelp = false },
            onCheckUpdates = {
                showHelp = false
                showUpdate = true
                checkForUpdates()
            },
        )
    }

    if (showUpdate) {
        UpdateDialog(
            state = updateState,
            onDismiss = { showUpdate = false },
            onRetry = { checkForUpdates() },
            onDownload = { startUpdateDownload() },
            onInstall = { installUpdate() },
            onOpenReleasePage = {
                updateState.release?.pageUrl?.let { page ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AudioZip") },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = { showHelp = true }) {
                                Icon(Icons.Filled.Info, contentDescription = "Cómo comprimir")
                            }
                        }
                    },
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Comprimir") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Recortar y unir") },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (selectedTab) {
                0 -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickFiles.launch(arrayOf("audio/*")) },
                    enabled = !state.isRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Agregar audios")
                }
                OutlinedButton(
                    onClick = { pickInputFolder.launch(null) },
                    enabled = !state.isRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Agregar carpeta")
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Salida: ${state.outputFolderLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pickOutputFolder.launch(Unit) }) {
                        Text("Cambiar carpeta de salida")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatDropdown(
                    label = "Formato",
                    selected = state.format.label,
                    options = OutputFormat.entries.map { it.label },
                    onSelect = { label ->
                        OutputFormat.entries.firstOrNull { it.label == label }?.let(viewModel::setFormat)
                    },
                    modifier = Modifier.weight(1f),
                )
                FormatDropdown(
                    label = "Calidad",
                    selected = state.quality.label,
                    options = Quality.entries.map { it.label },
                    onSelect = { label ->
                        Quality.entries.firstOrNull { it.label == label }?.let(viewModel::setQuality)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.format == OutputFormat.WAV || state.format == OutputFormat.FLAC) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "WAV y FLAC son sin pérdida: no se comprimen. Si quieres archivos más ligeros, elige mp3, ogg, opus o m4a.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.quality == Quality.MANUAL) {
                Spacer(Modifier.height(12.dp))
                ManualControls(
                    format = state.format,
                    bitrateKbps = state.manualBitrateKbps,
                    items = state.items,
                    onBitrateChange = viewModel::setManualBitrate,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.compressAll() },
                    enabled = !state.isRunning && state.items.any { it.status != AudioItem.Status.DONE },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Comprimir todo")
                }
                if (state.isRunning) {
                    OutlinedButton(onClick = { viewModel.cancel() }) {
                        Text("Cancelar")
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.clearAll() },
                        enabled = state.items.isNotEmpty(),
                    ) {
                        Text("Quitar todo")
                    }
                }
            }

            if (state.message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.items, key = { it.id }) { item ->
                    AudioItemCard(
                        item = item,
                        outputFormat = state.format,
                        quality = state.quality,
                        bitrateKbps = state.manualBitrateKbps,
                        isRunning = state.isRunning,
                        playback = state.playback,
                        onRemove = { viewModel.removeItem(item.id) },
                        onTogglePlay = { useOutput -> viewModel.togglePlay(item, useOutput) },
                        onShare = {
                            viewModel.shareIntentFor(item)?.let { intent ->
                                context.startActivity(intent)
                            }
                        },
                    )
                }
            }
                }
                1 -> {
                    TrimScreen(viewModel = trimViewModel)
                }
            }
        }
    }
}

@Composable
private fun ManualControls(
    format: OutputFormat,
    bitrateKbps: Int,
    items: List<AudioItem>,
    onBitrateChange: (Int) -> Unit,
) {
    if (format == OutputFormat.WAV || format == OutputFormat.FLAC) {
        Text(
            text = "El modo manual no aplica a ${format.label}: es audio sin pérdida.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Bitrate manual",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$bitrateKbps kbps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = bitrateKbps.toFloat(),
                onValueChange = { v ->
                    val snapped = ((v - 32f) / 8f).roundToInt() * 8 + 32
                    onBitrateChange(snapped)
                },
                valueRange = 32f..320f,
                steps = 35,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "32",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "320 kbps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            val estimable = items.filter { it.durationMs > 0 }
            if (estimable.isEmpty()) {
                Text(
                    text = "Agrega audios para ver la estimación de tamaño.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val totalOriginal = estimable.sumOf { it.sizeBytes }
                val totalEstimated = estimable.sumOf { estimateOutputBytes(bitrateKbps, it.durationMs) }
                val positive = isPositiveCompression(totalOriginal, totalEstimated)
                val pct = if (totalOriginal > 0) {
                    ((1 - totalEstimated.toDouble() / totalOriginal.toDouble()) * 100).toInt()
                } else 0
                Text(
                    text = if (positive) {
                        "Estimado: ≈ ${formatBytes(totalEstimated)} (${pct}% menos) ✓ Positivo"
                    } else {
                        "Ojo: pesaría ≈ ${formatBytes(totalEstimated)}, más que los ${formatBytes(totalOriginal)} originales. Baja el bitrate."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (positive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit, onCheckUpdates: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cómo comprimir bien") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpSection(
                    title = "Elige el formato según tu uso",
                    body = "MP3: compatible con todo, pero es el menos eficiente. Bueno para compartir.\n" +
                        "OGG (Vorbis): más eficiente que MP3 a igual calidad. Ideal para audios de juegos.\n" +
                        "Opus: el más eficiente de todos; ahorra más espacio a igual calidad. Perfecto para voces y música a bitrates bajos.\n" +
                        "M4A (AAC): buena calidad y tamaño, muy compatible con iPhone y reproductores.\n" +
                        "FLAC: sin pérdida, como WAV pero pesa menos. La calidad controla su nivel de compresión.\n" +
                        "WAV: no comprime nada, es audio sin pérdida. El archivo pesa lo mismo o más; úsalo solo si necesitas la máxima calidad.",
                )
                HelpSection(
                    title = "Calidad",
                    body = "Alta, Media y Baja controlan el bitrate de salida: a mayor calidad, mayor tamaño. " +
                        "Para audios de juegos, Media suele ser el mejor balance entre tamaño y calidad.",
                )
                HelpSection(
                    title = "Modo manual",
                    body = "Con el modo Manual eliges el bitrate exacto (kbps) con un deslizador. " +
                        "La app estima el tamaño final de cada archivo y te dice si la compresión es positiva " +
                        "(el resultado pesará menos que el original) o si pesaría más. " +
                        "Un bitrate bajo ahorra más espacio pero suena peor; uno alto suena mejor pero puede pesar más que el original.",
                )
                HelpSection(
                    title = "¿Por qué a veces pesa más?",
                    body = "• Si el original ya viene comprimido (mp3, opus, ogg), recomprimirlo no lo hace más ligero: la calidad ya se perdió y solo se agrega tamaño.\n" +
                        "• Si la calidad de salida es mayor que la del original (por ejemplo, original a 96 kbps y salida a 320 kbps), el archivo crece.\n" +
                        "• Opus es más eficiente que MP3: un opus a 64 kbps puede pesar menos que un mp3 a 128 kbps aunque suene igual o mejor. Por eso pasar de opus a mp3 a veces da un archivo un poco más grande.\n" +
                        "• WAV no comprime: siempre pesa mucho.",
                )
                HelpSection(
                    title = "Consejos rápidos",
                    body = "• WAV → MP3, OGG u Opus: siempre ahorra espacio.\n" +
                        "• MP3/OGG/Opus → mismo formato: casi nunca ahorra; usa Media o Baja si necesitas reducir.\n" +
                        "• Los archivos conservan su nombre original; solo cambia la extensión.\n" +
                        "• Puedes escuchar el original y el resultado con los botones de reproducción de cada tarjeta, para comparar la calidad.",
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCheckUpdates) { Text("Buscar actualizaciones") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AudioItemCard(
    item: AudioItem,
    outputFormat: OutputFormat,
    quality: Quality,
    bitrateKbps: Int,
    isRunning: Boolean,
    playback: PlaybackState,
    onRemove: () -> Unit,
    onTogglePlay: (Boolean) -> Unit,
    onShare: () -> Unit,
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
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (item.status != AudioItem.Status.WORKING) {
                    IconButton(onClick = onRemove, enabled = !isRunning) {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar")
                    }
                }
            }
            val inExt = item.name.substringAfterLast('.', "").uppercase()
            Text(
                text = "${formatBytes(item.sizeBytes)} · $inExt → ${outputFormat.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText(item),
                style = MaterialTheme.typography.bodySmall,
                color = when (item.status) {
                    AudioItem.Status.DONE -> MaterialTheme.colorScheme.primary
                    AudioItem.Status.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (quality == Quality.MANUAL && outputFormat != OutputFormat.WAV &&
                outputFormat != OutputFormat.FLAC &&
                item.status != AudioItem.Status.DONE && item.durationMs > 0
            ) {
                val est = estimateOutputBytes(bitrateKbps, item.durationMs)
                val positive = isPositiveCompression(item.sizeBytes, est)
                val pct = if (item.sizeBytes > 0) {
                    ((1 - est.toDouble() / item.sizeBytes.toDouble()) * 100).toInt()
                } else 0
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (positive) "≈ ${formatBytes(est)} (↓ $pct% menos)"
                    else "≈ ${formatBytes(est)} (↑ pesaría más)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (positive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            if (item.status == AudioItem.Status.ERROR && !item.errorDetail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = item.errorDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val playingOriginal = playback.itemId == item.id && !playback.output && playback.isPlaying
                OutlinedButton(onClick = { onTogglePlay(false) }) {
                    Icon(
                        if (playingOriginal) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(if (playingOriginal) "Pausar" else "Original")
                }
                if (item.status == AudioItem.Status.DONE) {
                    val playingOutput = playback.itemId == item.id && playback.output && playback.isPlaying
                    OutlinedButton(onClick = { onTogglePlay(true) }) {
                        Icon(
                            if (playingOutput) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(if (playingOutput) "Pausar" else "Comprimido")
                    }
                }
            }
            if (item.status == AudioItem.Status.WORKING || item.status == AudioItem.Status.DONE) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (item.status == AudioItem.Status.DONE) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Compartir")
                }
            }
        }
    }
}

private fun statusText(item: AudioItem): String = when (item.status) {
    AudioItem.Status.PENDING -> "En cola"
    AudioItem.Status.WORKING -> "Comprimiendo… ${(item.progress * 100).toInt()}%"
    AudioItem.Status.DONE -> {
        val outSize = item.outputSizeBytes ?: 0L
        val saved = if (item.sizeBytes > 0) {
            ((1 - outSize.toDouble() / item.sizeBytes.toDouble()) * 100).toInt()
        } else 0
        if (saved > 0) "Listo: ${formatBytes(outSize)} ($saved% menos)"
        else "Listo: ${formatBytes(outSize)}"
    }
    AudioItem.Status.ERROR -> "Error: ${item.error ?: "no se pudo comprimir"}"
}
