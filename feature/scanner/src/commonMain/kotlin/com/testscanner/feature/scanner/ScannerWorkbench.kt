package com.testscanner.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.action_auto
import com.testscanner.feature.scanner.resources.action_dismiss
import com.testscanner.feature.scanner.resources.availability_available
import com.testscanner.feature.scanner.resources.availability_failed
import com.testscanner.feature.scanner.resources.availability_planned
import com.testscanner.feature.scanner.resources.availability_requires_download
import com.testscanner.feature.scanner.resources.availability_requires_permission
import com.testscanner.feature.scanner.resources.continuous_scan
import com.testscanner.feature.scanner.resources.engine_formats_count
import com.testscanner.feature.scanner.resources.engine_in_use
import com.testscanner.feature.scanner.resources.engine_no_permissions
import com.testscanner.feature.scanner.resources.engine_select
import com.testscanner.feature.scanner.resources.engine_selected
import com.testscanner.feature.scanner.resources.engine_supports_continuous
import com.testscanner.feature.scanner.resources.engine_supports_multiple
import com.testscanner.feature.scanner.resources.engine_supports_torch
import com.testscanner.feature.scanner.resources.formats_hint
import com.testscanner.feature.scanner.resources.formats_title
import com.testscanner.feature.scanner.resources.session_error
import com.testscanner.feature.scanner.resources.session_switched_from
import org.jetbrains.compose.resources.stringResource

// El banco de motores: lo que solo se ve con el modo avanzado encendido.
//
// Está en su propio archivo y no repartido por la pantalla a propósito. Es la parte del producto que
// **no** es un lector de códigos, y tenerla junta hace evidente de un vistazo qué se está
// escondiendo por defecto y qué no.

/**
 * Filtro de formatos (RF-06).
 *
 * Se dibuja desde `BarcodeFormat.known`, así que añadir una simbología al modelo la hace aparecer
 * aquí sin tocar esta pantalla — el mismo principio que la ficha de motor.
 */
@Composable
internal fun FormatFilters(state: ScannerState, onAction: (ScannerAction) -> Unit) {
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

/**
 * Controles que solo tienen sentido con el banco abierto: escaneo continuo, volver a automático y
 * el aviso de degradación de motor.
 *
 * El resto —linterna, zoom, desde imagen— vive sobre el visor en los dos modos: son controles de
 * cámara, no de banco de pruebas.
 */
@Composable
internal fun WorkbenchControls(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
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

            TextButton(onClick = { onAction(ScannerAction.SelectEngine(null)) }) {
                Text(stringResource(Res.string.action_auto))
            }
        }
    }
}

/**
 * Ficha de un motor. Se renderiza **entera desde el descriptor**: ni un solo `when` sobre el id.
 * Es la prueba de que las capacidades declarativas hacen genérica a la UI.
 */
@Composable
internal fun EngineCard(
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
