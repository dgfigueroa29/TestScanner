package com.testscanner.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.model.Detection
import com.testscanner.core.scanner.EngineAvailability
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla con estado. Solo obtiene el ViewModel y delega: toda la UI real vive en
 * [ScannerContent], que es stateless y por tanto previsualizable y testeable sin DI.
 */
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScannerContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun ScannerContent(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { SessionControls(state, onAction) }

        item {
            Text(
                text = "Alternativas de escaneo",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        items(state.catalog, key = { it.id.id }) { status ->
            EngineCard(
                status = status,
                selected = status.id == state.selectedEngineId,
                active = status.id == state.activeEngineId,
                onSelect = { onAction(ScannerAction.SelectEngine(status.id)) },
            )
        }

        if (state.detections.isNotEmpty()) {
            item {
                Text(
                    text = "Detecciones",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            items(state.detections, key = { it.id }) { DetectionRow(it) }
        }
    }
}

@Composable
private fun SessionControls(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = when (state.sessionStatus) {
                    SessionStatus.Idle -> "Sesión detenida"
                    SessionStatus.Starting -> "Arrancando…"
                    SessionStatus.Scanning -> "Escaneando con ${state.activeEngineId?.id ?: "-"}"
                    SessionStatus.Finished -> "Sesión terminada"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            state.switchedFrom?.let {
                Text(
                    text = "Se degradó desde ${it.id}: el motor anterior no pudo continuar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            state.error?.let {
                Text(
                    text = "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onAction(ScannerAction.StartSession) }) { Text("Escanear") }
                OutlinedButton(onClick = { onAction(ScannerAction.StopSession) }) { Text("Detener") }
                OutlinedButton(onClick = { onAction(ScannerAction.SelectEngine(null)) }) {
                    Text("Auto")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = state.continuous,
                    onCheckedChange = { onAction(ScannerAction.SetContinuous(it)) },
                )
                Text("Escaneo continuo", style = MaterialTheme.typography.bodyMedium)
            }

            if (state.isManualEntryActive) {
                ManualEntryField(state, onAction)
            }
        }
    }
}

@Composable
private fun ManualEntryField(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        OutlinedTextField(
            value = state.manualInput,
            onValueChange = { onAction(ScannerAction.ManualInputChanged(it)) },
            label = { Text("Código") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(ScannerAction.SubmitManualInput) },
            enabled = state.manualInput.isNotBlank(),
        ) {
            Text("Enviar")
        }
    }
}

/**
 * Ficha de un motor. Se renderiza **entera desde el descriptor**: ni un solo `when` sobre el id.
 * Es la prueba de que las capacidades declarativas hacen genérica a la UI.
 */
@Composable
private fun EngineCard(
    status: EngineStatus,
    selected: Boolean,
    active: Boolean,
    onSelect: () -> Unit,
) {
    val descriptor = status.descriptor
    val capabilities = descriptor.capabilities

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(descriptor.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status.availability.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (status.isUsable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = buildString {
                    append("${capabilities.supportedFormats.size} formatos")
                    if (capabilities.supportsContinuousScan) append(" · continuo")
                    if (capabilities.supportsMultipleCodes) append(" · múltiple")
                    if (capabilities.supportsTorch) append(" · linterna")
                    if (!capabilities.requiresCameraPermission) append(" · sin permisos")
                },
                style = MaterialTheme.typography.labelSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = onSelect,
                    enabled = status.isUsable,
                    label = { Text(if (selected) "Elegido" else "Elegir") },
                )
                if (active) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("En uso") })
                }
            }
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(detection.barcode.rawValue, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${detection.barcode.format.displayName} · ${detection.engineId.id}" +
                    (detection.latencyMillis?.let { " · ${it} ms" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun EngineAvailability.label(): String = when (this) {
    is EngineAvailability.Available -> "Disponible"
    is EngineAvailability.RequiresPermission -> "Requiere permiso de ${permission.displayName}"
    is EngineAvailability.RequiresDownload -> "Requiere descargar su modelo"
    is EngineAvailability.Unsupported -> reason
    is EngineAvailability.NotImplemented -> "Planificado para la fase $plannedPhase"
    is EngineAvailability.Failed -> "No se pudo comprobar"
}
