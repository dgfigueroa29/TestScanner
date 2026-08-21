package com.testscanner.feature.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.testscanner.core.designsystem.CodeValueStyle
import com.testscanner.core.designsystem.Radius
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.model.Detection
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.a11y_results_collapse
import com.testscanner.feature.scanner.resources.a11y_results_expand
import com.testscanner.feature.scanner.resources.action_scan_from_image
import com.testscanner.feature.scanner.resources.action_submit
import com.testscanner.feature.scanner.resources.detection_latency
import com.testscanner.feature.scanner.resources.detection_meta
import com.testscanner.feature.scanner.resources.manual_input_label
import com.testscanner.feature.scanner.resources.results_clear
import com.testscanner.feature.scanner.resources.results_hint_body
import com.testscanner.feature.scanner.resources.results_hint_title
import com.testscanner.feature.scanner.resources.results_more
import com.testscanner.feature.scanner.resources.results_show_less
import org.jetbrains.compose.resources.stringResource

/**
 * La hoja de resultados: lo que ocupa la parte baja de la pantalla debajo del visor.
 *
 * No es un `ModalBottomSheet` a propósito. Una hoja modal tapa la cámara y hay que arrastrarla para
 * volver a ver, que es exactamente lo contrario de lo que quiere quien escanea en serie: leer, mirar
 * el resultado y apuntar al siguiente sin tocar la pantalla. Esta empuja el visor hacia arriba en
 * lugar de taparlo, así que la cámara nunca deja de verse.
 */
@Composable
internal fun ResultsSheet(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    advancedMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Spacing.xxs,
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.md)
                // Crecer y encogerse con animación en vez de dar un salto: la hoja cambia de altura
                // cada vez que llega una lectura, y sin esto el visor da un tirón hacia arriba.
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.isManualEntryActive) {
                ManualEntryField(state, onAction)
            }

            if (state.detections.isEmpty()) {
                EmptyResults(state, onAction)
            } else {
                DetectedResults(state, onAction, advancedMode)
            }
        }
    }
}

/**
 * Lo que se ve antes de la primera lectura.
 *
 * Dice qué hacer —apuntar— y no qué está pasando. Un "Sesión detenida" o un "Escaneando…" describe
 * el estado interno de la app y no ayuda a nadie a leer un código.
 */
@Composable
private fun EmptyResults(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Text(
        text = stringResource(Res.string.results_hint_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(Res.string.results_hint_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Escanear desde imagen se ofrece solo si algún motor disponible declara la fuente (RF-07). En
    // escritorio, donde hoy solo hay entrada manual en vivo, el botón no aparece.
    if (state.canScanFromImage) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onAction(ScannerAction.ScanFromImage) },
                enabled = !state.isDecodingImage,
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.md),
                )
                Text(
                    text = stringResource(Res.string.action_scan_from_image),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (state.isDecodingImage) {
                CircularProgressIndicator(modifier = Modifier.size(Spacing.lg))
            }
        }
    }
}

/** La lectura más reciente destacada, y el resto plegado detrás de un contador. */
@Composable
private fun DetectedResults(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    advancedMode: Boolean,
) {
    // La guarda va antes del `remember`: salir de un composable después de haber declarado estado
    // recordado es la clase de cosa que funciona hasta que alguien mueve una línea.
    val latest = state.latestDetection ?: return
    val older = state.detections.drop(1)
    var expanded by remember { mutableStateOf(false) }

    DetectionCard(
        detection = latest,
        canShare = state.canShare,
        advancedMode = advancedMode,
        highlighted = true,
        onAction = onAction,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (older.isNotEmpty()) {
            val spoken = stringResource(
                if (expanded) Res.string.a11y_results_collapse else Res.string.a11y_results_expand,
            )
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics { contentDescription = spoken },
            ) {
                val label = if (expanded) {
                    stringResource(Res.string.results_show_less)
                } else {
                    stringResource(Res.string.results_more, older.size)
                }
                Text(label)
            }
        }

        TextButton(onClick = { onAction(ScannerAction.ClearDetections) }) {
            Text(stringResource(Res.string.results_clear))
        }
    }

    AnimatedVisibility(
        visible = expanded && older.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        // Altura acotada: la lista puede llegar al tope de cien lecturas y sin límite se comería el
        // visor entero, que es justo lo que esta disposición existe para evitar.
        LazyColumn(
            modifier = Modifier.heightIn(max = OLDER_RESULTS_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(older, key = { it.id }) { detection ->
                DetectionCard(
                    detection = detection,
                    canShare = state.canShare,
                    advancedMode = advancedMode,
                    highlighted = false,
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * Un resultado con sus acciones (RF-13).
 *
 * Las acciones salen de `ResultActionsFactory`, así que dependen de **qué significa** el código y no
 * de su formato: un QR con una URL ofrece "Abrir enlace" y el mismo QR con texto plano no.
 *
 * @param highlighted la lectura recién llegada. Se pinta sobre el contenedor primario para que se
 *   distinga de las anteriores sin depender de la posición — quien usa un lector de pantalla no ve
 *   que está arriba del todo, y por eso además se anuncia como región viva.
 */
@Composable
internal fun DetectionCard(
    detection: Detection,
    canShare: Boolean,
    advancedMode: Boolean,
    highlighted: Boolean,
    onAction: (ScannerAction) -> Unit,
) {
    val actions = ResultActionsFactory.actionsFor(detection.barcode, canShare)
    val shareable = ResultActionsFactory.shareableContent(detection.barcode).asText()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (highlighted) liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Monoespaciada: es un dato que se coteja carácter a carácter contra una etiqueta
            // impresa, y en proporcional `1`, `l` e `I` se confunden.
            Text(
                text = detection.barcode.rawValue,
                style = CodeValueStyle,
                // Un QR puede traer un vCard entero. Tres líneas y elipsis: lo que no cabe se copia
                // o se comparte con los botones de abajo, que es lo que se hace con un valor largo.
                maxLines = CODE_VALUE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = buildString {
                    // El formato es útil siempre —saber que es un QR y no un EAN-13 orienta—; el id
                    // del motor y la latencia son medidas del banco de pruebas.
                    if (advancedMode) {
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
                    } else {
                        append(detection.barcode.format.displayName)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                actions.forEachIndexed { index, action ->
                    // Con varios resultados en pantalla, todos los botones se llaman igual. La
                    // descripción incluye el valor para que "Copiar" diga qué se copia (RNF-05).
                    val spoken = stringResource(action.spokenResource(), detection.barcode.rawValue)
                    val label = stringResource(action.labelResource())
                    val onClick = { onAction(ScannerAction.RunResultAction(action, shareable)) }
                    val semantics = Modifier.semantics { contentDescription = spoken }

                    // La primera acción de la lectura destacada va como botón relleno: es la que el
                    // usuario quiere el 90 % de las veces —abrir el enlace, copiar el número— y con
                    // tres botones de texto iguales no había ninguna pista de cuál.
                    if (index == 0 && highlighted) {
                        Button(onClick = onClick, modifier = semantics) { Text(label) }
                    } else {
                        TextButton(onClick = onClick, modifier = semantics) { Text(label) }
                    }
                }
            }
        }
    }
}

/**
 * Campo de entrada manual. Vive en la hoja porque es la forma de "leer" cuando no hay cámara.
 *
 * Trae su propia `Column` en lugar de apoyarse en la de quien lo llame: emite tres hijos, y en el
 * banco de motores se le invoca desde un `item {}` de `LazyColumn`, cuyo ámbito **no** los apila —
 * los superpondría uno encima de otro.
 */
@Composable
internal fun ManualEntryField(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        HorizontalDivider()
    }
}

private const val CODE_VALUE_MAX_LINES = 3

private val OLDER_RESULTS_MAX_HEIGHT = Spacing.xxl * 5
