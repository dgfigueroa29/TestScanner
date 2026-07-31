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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.scan.OpenKind
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.domain.scan.ShareableContent
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.a11y_copy_value
import com.testscanner.feature.scanner.resources.a11y_detections_in_view
import com.testscanner.feature.scanner.resources.a11y_no_detections
import com.testscanner.feature.scanner.resources.a11y_open_value
import com.testscanner.feature.scanner.resources.a11y_share_value
import com.testscanner.feature.scanner.resources.a11y_viewfinder
import com.testscanner.feature.scanner.resources.a11y_zoom
import com.testscanner.feature.scanner.resources.action_auto
import com.testscanner.feature.scanner.resources.action_dismiss
import com.testscanner.feature.scanner.resources.action_grant_camera
import com.testscanner.feature.scanner.resources.action_scan
import com.testscanner.feature.scanner.resources.action_scan_from_image
import com.testscanner.feature.scanner.resources.action_stop
import com.testscanner.feature.scanner.resources.action_submit
import com.testscanner.feature.scanner.resources.availability_available
import com.testscanner.feature.scanner.resources.availability_failed
import com.testscanner.feature.scanner.resources.availability_planned
import com.testscanner.feature.scanner.resources.availability_requires_download
import com.testscanner.feature.scanner.resources.availability_requires_permission
import com.testscanner.feature.scanner.resources.continuous_scan
import com.testscanner.feature.scanner.resources.detection_latency
import com.testscanner.feature.scanner.resources.detection_meta
import com.testscanner.feature.scanner.resources.detections_title
import com.testscanner.feature.scanner.resources.engine_formats_count
import com.testscanner.feature.scanner.resources.engine_in_use
import com.testscanner.feature.scanner.resources.engine_no_permissions
import com.testscanner.feature.scanner.resources.engine_select
import com.testscanner.feature.scanner.resources.engine_selected
import com.testscanner.feature.scanner.resources.engine_supports_continuous
import com.testscanner.feature.scanner.resources.engine_supports_multiple
import com.testscanner.feature.scanner.resources.engine_supports_torch
import com.testscanner.feature.scanner.resources.engines_title
import com.testscanner.feature.scanner.resources.formats_hint
import com.testscanner.feature.scanner.resources.formats_title
import com.testscanner.feature.scanner.resources.manual_input_label
import com.testscanner.feature.scanner.resources.message_camera_permission_denied
import com.testscanner.feature.scanner.resources.message_copied
import com.testscanner.feature.scanner.resources.message_copy_failed
import com.testscanner.feature.scanner.resources.message_engine_switched
import com.testscanner.feature.scanner.resources.message_manual_input_unavailable
import com.testscanner.feature.scanner.resources.message_no_code_in_image
import com.testscanner.feature.scanner.resources.message_open_failed
import com.testscanner.feature.scanner.resources.message_share_failed
import com.testscanner.feature.scanner.resources.result_copy
import com.testscanner.feature.scanner.resources.result_open_email
import com.testscanner.feature.scanner.resources.result_open_link
import com.testscanner.feature.scanner.resources.result_open_map
import com.testscanner.feature.scanner.resources.result_open_phone
import com.testscanner.feature.scanner.resources.result_open_sms
import com.testscanner.feature.scanner.resources.result_share
import com.testscanner.feature.scanner.resources.session_error
import com.testscanner.feature.scanner.resources.session_finished
import com.testscanner.feature.scanner.resources.session_idle
import com.testscanner.feature.scanner.resources.session_scanning
import com.testscanner.feature.scanner.resources.session_starting
import com.testscanner.feature.scanner.resources.session_switched_from
import com.testscanner.feature.scanner.resources.share_separator
import com.testscanner.feature.scanner.resources.share_wifi
import com.testscanner.feature.scanner.resources.share_wifi_with_password
import com.testscanner.feature.scanner.resources.torch_off
import com.testscanner.feature.scanner.resources.torch_on
import com.testscanner.feature.scanner.resources.zoom_ratio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
                is ScannerEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(resolve(effect.message))
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
                text = stringResource(Res.string.engines_title),
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
                    text = stringResource(Res.string.detections_title),
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
    // Ni la superficie nativa ni el Canvas del overlay producen semántica: para un lector de
    // pantalla, el visor es un agujero. Describirlo con cuántos códigos hay dentro es lo que
    // convierte el encuadre en información y no en un rectángulo mudo (RNF-05).
    val inView = if (state.detections.isEmpty()) {
        stringResource(Res.string.a11y_no_detections)
    } else {
        stringResource(Res.string.a11y_detections_in_view, state.detections.size)
    }
    // El texto se arma fuera del `semantics`: su lambda no es composable y `stringResource` sí.
    val description = "${stringResource(Res.string.a11y_viewfinder)}. $inView"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(VIEWFINDER_ASPECT_RATIO)
            .clip(RoundedCornerShape(Spacing.md))
            .background(Color.Black)
            .semantics { contentDescription = description },
    ) {
        previewEngine.CameraPreview(Modifier.fillMaxSize())
        // En el navegador el vídeo es un elemento del DOM sobre el canvas: el overlay se dibujaría
        // debajo y no se vería. Mejor no pintarlo que dejar código que no produce nada.
        if (!previewEngine.occludesOverlay) {
            ScanOverlay(detections = state.detections)
        }
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
                text = stringResource(Res.string.formats_title, state.formats.size, BarcodeFormat.known.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.formats_hint),
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
            // Región viva: cuando la sesión arranca, cambia de motor o termina, el lector de
            // pantalla lo anuncia solo. Sin esto, quien no ve la pantalla no tiene forma de saber
            // que algo pasó — y la degradación de motor es justo lo que la app quiere hacer visible.
            Text(
                text = when (state.sessionStatus) {
                    SessionStatus.Idle -> stringResource(Res.string.session_idle)
                    SessionStatus.Starting -> stringResource(Res.string.session_starting)
                    SessionStatus.Scanning ->
                        stringResource(Res.string.session_scanning, state.activeEngineId?.id ?: "-")

                    SessionStatus.Finished -> stringResource(Res.string.session_finished)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            state.switchedFrom?.let {
                Text(
                    text = stringResource(Res.string.session_switched_from, it.id),
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
                        text = stringResource(Res.string.session_error, error.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(ScannerAction.DismissError) }) {
                        Text(stringResource(Res.string.action_dismiss))
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onAction(ScannerAction.StartSession) }) {
                    Text(stringResource(Res.string.action_scan))
                }
                OutlinedButton(onClick = { onAction(ScannerAction.StopSession) }) {
                    Text(stringResource(Res.string.action_stop))
                }
                OutlinedButton(onClick = { onAction(ScannerAction.SelectEngine(null)) }) {
                    Text(stringResource(Res.string.action_auto))
                }
            }

            // Escanear desde imagen se ofrece solo si algún motor disponible declara la fuente
            // (RF-07). En escritorio, donde hoy solo hay entrada manual, el botón no aparece.
            if (state.canScanFromImage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onAction(ScannerAction.ScanFromImage) },
                        enabled = !state.isDecodingImage,
                    ) {
                        Text(stringResource(Res.string.action_scan_from_image))
                    }
                    if (state.isDecodingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(Spacing.lg))
                    }
                }
            }

            // El interruptor y su etiqueta se fusionan en un solo nodo: por separado, el lector de
            // pantalla enfoca el Switch y dice "activado" sin decir activado *qué*.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Switch(
                    checked = state.continuous,
                    onCheckedChange = { onAction(ScannerAction.SetContinuous(it)) },
                )
                Text(stringResource(Res.string.continuous_scan), style = MaterialTheme.typography.bodyMedium)
            }

            // Los controles de cámara aparecen solo si el motor activo los declara: es la razón de
            // que `CameraControlEngine` sea una interfaz aparte y no métodos del contrato base.
            if (state.canControlTorch) {
                FilterChip(
                    selected = state.torchEnabled,
                    onClick = { onAction(ScannerAction.ToggleTorch) },
                    label = {
                        val torch = if (state.torchEnabled) Res.string.torch_on else Res.string.torch_off
                        Text(stringResource(torch))
                    },
                )
            }

            // Un motor bloqueado por permiso no es un error: es algo que el usuario puede
            // desbloquear. Por eso `EngineAvailability` distingue ese caso del resto.
            if (state.actionableEngines.isNotEmpty()) {
                OutlinedButton(onClick = { onAction(ScannerAction.RequestCameraPermission) }) {
                    Text(stringResource(Res.string.action_grant_camera))
                }
            }

            if (state.canControlZoom) {
                val zoomLabel = stringResource(Res.string.zoom_ratio, state.zoomRatio.toInt())
                val zoomName = stringResource(Res.string.a11y_zoom)
                Text(text = zoomLabel, style = MaterialTheme.typography.labelMedium)
                // Un Slider sin nombre se anuncia como un porcentaje suelto. Con el nombre y el
                // aumento en el estado, se lee "Zoom de la cámara, 3×" en vez de "60 %".
                Slider(
                    value = state.zoomRatio,
                    onValueChange = { onAction(ScannerAction.SetZoom(it)) },
                    valueRange = MIN_ZOOM..MAX_ZOOM,
                    modifier = Modifier.semantics {
                        contentDescription = zoomName
                        stateDescription = zoomLabel
                    },
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
            label = { Text(stringResource(Res.string.manual_input_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(ScannerAction.SubmitManualInput) },
            enabled = state.manualInput.isNotBlank(),
        ) {
            Text(stringResource(Res.string.action_submit))
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
                    append(stringResource(Res.string.engine_formats_count, capabilities.supportedFormats.size))
                    if (capabilities.supportsContinuousScan) {
                        append(stringResource(Res.string.engine_supports_continuous))
                    }
                    if (capabilities.supportsMultipleCodes) {
                        append(stringResource(Res.string.engine_supports_multiple))
                    }
                    if (capabilities.supportsTorch) append(stringResource(Res.string.engine_supports_torch))
                    if (!capabilities.requiresCameraPermission) {
                        append(stringResource(Res.string.engine_no_permissions))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = onSelect,
                    enabled = status.isUsable,
                    label = {
                        val label = if (selected) Res.string.engine_selected else Res.string.engine_select
                        Text(stringResource(label))
                    },
                )
                if (active) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(Res.string.engine_in_use)) },
                    )
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
    val shareable = ResultActionsFactory.shareableContent(detection.barcode).asText()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(detection.barcode.rawValue, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = buildString {
                    append(
                        stringResource(
                            Res.string.detection_meta,
                            detection.barcode.format.displayName,
                            detection.engineId.id,
                        ),
                    )
                    detection.latencyMillis?.let {
                        append(stringResource(Res.string.detection_latency, it))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                actions.forEach { action ->
                    // Con varios resultados en pantalla, todos los botones se llaman igual. La
                    // descripción incluye el valor para que "Copiar" diga qué se copia (RNF-05).
                    val spoken = stringResource(action.spokenResource(), detection.barcode.rawValue)
                    TextButton(
                        onClick = { onAction(ScannerAction.RunResultAction(action, shareable)) },
                        modifier = Modifier.semantics { contentDescription = spoken },
                    ) {
                        Text(stringResource(action.labelResource()))
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineAvailability.label(): String = when (this) {
    is EngineAvailability.Available -> stringResource(Res.string.availability_available)

    is EngineAvailability.RequiresPermission ->
        stringResource(Res.string.availability_requires_permission, permission.displayName)

    is EngineAvailability.RequiresDownload -> stringResource(Res.string.availability_requires_download)

    // El motivo lo redacta el motor —"este navegador no expone BarcodeDetector"— y es lo único que
    // explica el caso concreto; envolverlo en un texto genérico perdería la información.
    is EngineAvailability.Unsupported -> reason

    is EngineAvailability.NotImplemented ->
        stringResource(Res.string.availability_planned, plannedPhase)

    is EngineAvailability.Failed -> stringResource(Res.string.availability_failed)
}

/** Cómo la anuncia un lector de pantalla, con el valor dentro para poder distinguir un botón de otro. */
private fun ResultAction.spokenResource(): StringResource = when (this) {
    ResultAction.Copy -> Res.string.a11y_copy_value
    ResultAction.Share -> Res.string.a11y_share_value
    is ResultAction.Open -> Res.string.a11y_open_value
}

/** Cómo se llama en pantalla cada acción sobre el resultado (RF-13). */
private fun ResultAction.labelResource(): StringResource = when (this) {
    ResultAction.Copy -> Res.string.result_copy
    ResultAction.Share -> Res.string.result_share
    is ResultAction.Open -> when (kind) {
        OpenKind.Link -> Res.string.result_open_link
        OpenKind.Email -> Res.string.result_open_email
        OpenKind.Phone -> Res.string.result_open_phone
        OpenKind.Sms -> Res.string.result_open_sms
        OpenKind.Map -> Res.string.result_open_map
    }
}

/**
 * Traduce un mensaje del ViewModel a texto.
 *
 * Es la única pieza que conoce las dos mitades: el ViewModel dice qué pasó y aquí se le pone
 * nombre. [ScannerMessage.Raw] pasa tal cual porque su texto lo produjo la plataforma.
 */
@Composable
private fun resolve(message: ScannerMessage): String = when (message) {
    ScannerMessage.EngineSwitched -> stringResource(Res.string.message_engine_switched)
    ScannerMessage.CameraPermissionDenied ->
        stringResource(Res.string.message_camera_permission_denied)

    ScannerMessage.ManualInputUnavailable ->
        stringResource(Res.string.message_manual_input_unavailable)

    ScannerMessage.Copied -> stringResource(Res.string.message_copied)
    ScannerMessage.CopyFailed -> stringResource(Res.string.message_copy_failed)
    ScannerMessage.ShareFailed -> stringResource(Res.string.message_share_failed)
    ScannerMessage.OpenFailed -> stringResource(Res.string.message_open_failed)
    ScannerMessage.NoCodeInImage -> stringResource(Res.string.message_no_code_in_image)
    is ScannerMessage.Raw -> message.text
}

private const val VIEWFINDER_ASPECT_RATIO = 3f / 4f

// El rango real depende de la cámara; el motor recorta al máximo que admita el dispositivo.
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

/**
 * Redacta lo que se copia o se comparte.
 *
 * El dominio dice qué datos son relevantes; el texto se arma aquí, donde están los recursos
 * traducibles. Antes la frase se componía en `ResultActionsFactory`, que era español dentro del
 * dominio (deuda D15).
 */
@Composable
private fun ShareableContent.asText(): String = when (this) {
    is ShareableContent.Raw -> value

    is ShareableContent.Wifi -> password
        ?.let { stringResource(Res.string.share_wifi_with_password, ssid, it) }
        ?: stringResource(Res.string.share_wifi, ssid)

    is ShareableContent.Contact -> parts.joinToString(stringResource(Res.string.share_separator))
}
