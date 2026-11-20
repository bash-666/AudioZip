package com.compresoraudio.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.compresoraudio.app.audio.ReleaseInfo

enum class UpdatePhase { CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, ERROR }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.CHECKING,
    val currentVersion: String = "",
    val release: ReleaseInfo? = null,
    val downloadProgress: Float = -1f,
    val errorMessage: String = "",
)

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizaciones") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (state.phase) {
                    UpdatePhase.CHECKING -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Buscando actualizaciones…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    UpdatePhase.UP_TO_DATE -> {
                        Text(
                            text = "Tienes la última versión (${state.currentVersion}).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    UpdatePhase.AVAILABLE -> {
                        Text(
                            text = "Nueva versión disponible: ${state.release?.version}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val notes = state.release?.notes?.trim().orEmpty()
                        if (notes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    UpdatePhase.DOWNLOADING -> {
                        Text(
                            text = "Descargando actualización…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.downloadProgress >= 0f) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    UpdatePhase.DOWNLOADED -> {
                        Text(
                            text = "Descarga lista. Toca «Instalar» para actualizar.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    UpdatePhase.ERROR -> {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.AVAILABLE -> {
                    TextButton(onClick = onDownload) { Text("Descargar e instalar") }
                    TextButton(onClick = onOpenReleasePage) { Text("Ver en GitHub") }
                }
                UpdatePhase.DOWNLOADED -> {
                    TextButton(onClick = onInstall) { Text("Instalar") }
                }
                UpdatePhase.ERROR -> {
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
