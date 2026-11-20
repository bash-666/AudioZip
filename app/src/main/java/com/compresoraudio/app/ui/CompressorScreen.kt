package com.compresoraudio.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.compresoraudio.app.audio.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(viewModel: CompressorViewModel = viewModel()) {
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
        ActivityResultContracts.OpenDocumentTree(),
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Compresor de Audio") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
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
                    OutlinedButton(onClick = { pickOutputFolder.launch(null) }) {
                        Text("Cambiar carpeta de salida")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatDropdown(
                    label = "Formato de salida",
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

            if (state.format == OutputFormat.WAV) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "WAV no se comprime: es audio sin pérdida. Si quieres archivos más ligeros, elige mp3, ogg u opus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        isRunning = state.isRunning,
                        onRemove = { viewModel.removeItem(item.id) },
                        onShare = {
                            viewModel.shareIntentFor(item)?.let { intent ->
                                context.startActivity(intent)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioItemCard(
    item: AudioItem,
    outputFormat: OutputFormat,
    isRunning: Boolean,
    onRemove: () -> Unit,
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
