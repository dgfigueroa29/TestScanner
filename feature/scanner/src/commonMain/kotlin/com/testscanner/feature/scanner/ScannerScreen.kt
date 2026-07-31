package com.testscanner.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla con estado. Solo obtiene el ViewModel y resuelve el preview del motor activo; toda la UI
 * real vive en [ScannerContent], que es stateless y por tanto previsualizable y testeable sin DI.
 */
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = koinViewModel(),
    previewResolver: EnginePreviewResolver = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // La disponibilidad cambia mientras la pantalla no está: el usuario concede el permiso desde
    // los ajustes, ML Kit termina de descargar su modelo, otra app suelta la cámara. Sin refrescar
    // al volver, el catálogo mostraría un estado viejo.
    LaunchedEffect(viewModel) { viewModel.onAction(ScannerAction.Refresh) }

    // Sin esto los mensajes del ViewModel se emitían a un SharedFlow que nadie escuchaba, incluido
    // el aviso de degradación de motor, que es la señal visible del objetivo G4.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    ScannerContent(
        state = state,
        onAction = viewModel::onAction,
        previewEngine = previewResolver.previewFor(state.activeEngineId),
    )
}

@Composable
fun ScannerContent(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
    previewEngine: CameraPreviewEngine? = null,
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
        if (previewEngine != null) {
            item { CameraViewfinder(previewEngine, state) }
        }

        item { SessionControls(state, onAction) }

        item { FormatFilters(state, onAction) }

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
            items(state.detections, key = { it.id }) { detection ->
                DetectionRow(detection, canShare = state.canShare, onAction = onAction)
            }
        }
    }
}

/**
 * Visor: superficie nativa del motor abajo, overlay de Compose común encima.
 *
 * La pantalla no sabe qué motor está pintando ni con qué API — solo que implementa
 * `CameraPreviewEngine`. Añadir el motor de iOS o el del navegador no toca este archivo.
 */
@Composable
private fun CameraViewfinder(previewEngine: CameraPreviewEngine, state: ScannerState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(VIEWFINDER_ASPECT_RATIO)
            .clip(RoundedCornerShape(Spacing.md))
            .background(Color.Black),
    ) {
        previewEngine.CameraPreview(Modifier.fillMaxSize())
        ScanOverlay(detections = state.detections)
    }
}

/**
 * Filtro de formatos (RF-06).
 *
 * Se dibuja desde `BarcodeFormat.known`, así que añadir una simbología al modelo la hace aparecer
 * aquí sin tocar esta pantalla — el mismo principio que la ficha de motor.
 */
@Composable
private fun FormatFilters(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Formatos (${state.formats.size} de ${BarcodeFormat.known.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Quitar formatos acelera la detección y evita lecturas cruzadas. " +
                    "Si los quitás todos vuelven todos: una petición sin formatos no es válida.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(BarcodeFormat.known.toList(), key = { it.id }) { format ->
                    FilterChip(
                        selected = format in state.formats,
                        onClick = { onAction(ScannerAction.ToggleFormat(format)) },
                        label = { Text(format.displayName) },
                    )
                }
            }
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

            state.error?.let { error ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(ScannerAction.DismissError) }) {
                        Text("Descartar")
                    }
                }
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

            // Los controles de cámara aparecen solo si el motor activo los declara: es la razón de
            // que `CameraControlEngine` sea una interfaz aparte y no métodos del contrato base.
            if (state.canControlTorch) {
                FilterChip(
                    selected = state.torchEnabled,
                    onClick = { onAction(ScannerAction.ToggleTorch) },
                    label = { Text(if (state.torchEnabled) "Linterna encendida" else "Linterna") },
                )
            }

            // Un motor bloqueado por permiso no es un error: es algo que el usuario puede
            // desbloquear. Por eso `EngineAvailability` distingue ese caso del resto.
            if (state.actionableEngines.isNotEmpty()) {
                OutlinedButton(onClick = { onAction(ScannerAction.RequestCameraPermission) }) {
                    Text("Conceder permiso de cámara")
                }
            }

            if (state.canControlZoom) {
                Text(
                    text = "Zoom ${state.zoomRatio.toInt()}x",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = state.zoomRatio,
                    onValueChange = { onAction(ScannerAction.SetZoom(it)) },
                    valueRange = MIN_ZOOM..MAX_ZOOM,
                )
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

/**
 * Resultado con sus acciones (RF-13).
 *
 * Las acciones salen de `ResultActionsFactory`, así que dependen de **qué significa** el código y no
 * de su formato: un QR con una URL ofrece "Abrir enlace" y el mismo QR con texto plano no.
 */
@Composable
private fun DetectionRow(
    detection: Detection,
    canShare: Boolean,
    onAction: (ScannerAction) -> Unit,
) {
    val actions = ResultActionsFactory.actionsFor(detection.barcode, canShare)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(detection.barcode.rawValue, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${detection.barcode.format.displayName} · ${detection.engineId.id}" +
                    (detection.latencyMillis?.let { " · $it ms" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                actions.forEach { action ->
                    TextButton(
                        onClick = { onAction(ScannerAction.RunResultAction(detection, action)) },
                    ) {
                        Text(action.label)
                    }
                }
            }
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

private const val VIEWFINDER_ASPECT_RATIO = 3f / 4f

// El rango real depende de la cámara; el motor recorta al máximo que admita el dispositivo.
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
