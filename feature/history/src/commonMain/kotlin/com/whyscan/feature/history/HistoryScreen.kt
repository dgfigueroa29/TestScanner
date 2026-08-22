package com.whyscan.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whyscan.core.designsystem.CodeValueStyle
import com.whyscan.core.designsystem.LocalSnackbarHostState
import com.whyscan.core.designsystem.Spacing
import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.scan.OpenKind
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.scan.ResultActionsFactory
import com.whyscan.core.domain.scan.ShareableContent
import com.whyscan.core.model.Detection
import com.whyscan.feature.history.resources.Res
import com.whyscan.feature.history.resources.a11y_copy_value
import com.whyscan.feature.history.resources.a11y_open_value
import com.whyscan.feature.history.resources.a11y_share_value
import com.whyscan.feature.history.resources.history_clear
import com.whyscan.feature.history.resources.history_empty
import com.whyscan.feature.history.resources.history_export_csv
import com.whyscan.feature.history.resources.history_export_json
import com.whyscan.feature.history.resources.history_filter_all
import com.whyscan.feature.history.resources.history_row_latency
import com.whyscan.feature.history.resources.history_row_meta
import com.whyscan.feature.history.resources.message_copied
import com.whyscan.feature.history.resources.message_copy_failed
import com.whyscan.feature.history.resources.message_exported
import com.whyscan.feature.history.resources.message_exported_to
import com.whyscan.feature.history.resources.message_nothing_to_export
import com.whyscan.feature.history.resources.message_open_failed
import com.whyscan.feature.history.resources.message_share_failed
import com.whyscan.feature.history.resources.result_copy
import com.whyscan.feature.history.resources.result_open_email
import com.whyscan.feature.history.resources.result_open_link
import com.whyscan.feature.history.resources.result_open_map
import com.whyscan.feature.history.resources.result_open_phone
import com.whyscan.feature.history.resources.result_open_sms
import com.whyscan.feature.history.resources.result_share
import com.whyscan.feature.history.resources.share_separator
import com.whyscan.feature.history.resources.share_wifi
import com.whyscan.feature.history.resources.share_wifi_with_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(
    advancedMode: Boolean = false,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowMessage -> snackbarHostState.showSnackbar(resolve(effect.message))
            }
        }
    }

    HistoryContent(state = state, onAction = viewModel::onAction, advancedMode = advancedMode)
}

/**
 * @param advancedMode muestra los filtros por motor y la latencia de cada lectura. Filtrar por motor
 *   es lo que convierte el historial en una herramienta de comparación (G5) y no tiene sentido para
 *   quien nunca eligió uno: los chips se llaman `mlkit-camerax` y `zxing-cpp`.
 */
@Composable
fun HistoryContent(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
    advancedMode: Boolean = false,
) {
    when {
        state.isLoading -> Centered(modifier) { CircularProgressIndicator() }

        state.isEmpty -> Centered(modifier) {
            Text(
                text = stringResource(Res.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Column(modifier = modifier.fillMaxSize().padding(Spacing.md)) {
            HistoryToolbar(state, onAction, advancedMode)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.visible, key = { it.id }) { detection ->
                    HistoryRow(
                        detection = detection,
                        canShare = state.canShare,
                        advancedMode = advancedMode,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

/**
 * Barra del historial: filtros a la izquierda, exportar y borrar a la derecha.
 *
 * Filtrar por motor es lo que convierte el historial en una herramienta de comparación: permite ver
 * qué leyó cada alternativa sobre los mismos códigos. Por eso los filtros solo salen en modo
 * avanzado — sus chips se llaman `mlkit-camerax` y `zxing-cpp`, que es un vocabulario que solo tiene
 * sentido para quien eligió un motor a mano. Exportar y borrar sí están siempre: son operaciones
 * sobre *los datos del usuario*, no sobre el banco de pruebas.
 */
@Composable
private fun HistoryToolbar(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    advancedMode: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (advancedMode) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    FilterChip(
                        selected = state.engineFilter == null,
                        onClick = { onAction(HistoryAction.FilterByEngine(null)) },
                        label = { Text(stringResource(Res.string.history_filter_all)) },
                    )
                }
                items(state.presentEngines, key = { it.id }) { engineId ->
                    FilterChip(
                        selected = state.engineFilter == engineId,
                        onClick = { onAction(HistoryAction.FilterByEngine(engineId)) },
                        label = { Text(engineId.id) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Exportar lo que se está viendo, no todo: el archivo debe parecerse a la pantalla.
            ExportFormat.entries.forEach { format ->
                OutlinedButton(
                    onClick = { onAction(HistoryAction.Export(format)) },
                    enabled = !state.isExporting,
                ) {
                    Text(stringResource(format.labelResource()))
                }
            }
            OutlinedButton(onClick = { onAction(HistoryAction.Clear) }) {
                Text(stringResource(Res.string.history_clear))
            }
        }
    }
}

@Composable
private fun HistoryRow(
    detection: Detection,
    canShare: Boolean,
    advancedMode: Boolean,
    onAction: (HistoryAction) -> Unit,
) {
    val actions = ResultActionsFactory.actionsFor(detection.barcode, canShare)
    val shareable = ResultActionsFactory.shareableContent(detection.barcode).asText()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Monoespaciada por lo mismo que en la pantalla de escaneo: es un dato que se coteja
            // carácter a carácter, y en una proporcional `1`, `l` e `I` se confunden.
            Text(detection.barcode.rawValue, style = CodeValueStyle)
            Text(
                text = buildString {
                    if (advancedMode) {
                        append(
                            stringResource(
                                Res.string.history_row_meta,
                                detection.barcode.format.displayName,
                                detection.engineId.id,
                            ),
                        )
                        detection.latencyMillis?.let {
                            append(stringResource(Res.string.history_row_latency, it))
                        }
                    } else {
                        append(detection.barcode.format.displayName)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                actions.forEach { action ->
                    // El historial es una lista larga de botones que se llaman igual. Sin el valor
                    // dentro de la descripción, un lector de pantalla los hace indistinguibles.
                    val spoken = stringResource(action.spokenResource(), detection.barcode.rawValue)
                    TextButton(
                        onClick = { onAction(HistoryAction.RunResultAction(action, shareable)) },
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
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/** Cómo la anuncia un lector de pantalla, con el valor dentro para distinguir un botón de otro. */
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
 * El ViewModel dice qué pasó; aquí se le pone nombre.
 *
 * Es `suspend` y usa `getString` en lugar de ser `@Composable` con `stringResource`: se la llama
 * desde dentro de un `LaunchedEffect`, que es una corrutina y **no** un contexto composable.
 */
private suspend fun resolve(message: HistoryMessage): String = when (message) {
    HistoryMessage.Copied -> getString(Res.string.message_copied)
    HistoryMessage.CopyFailed -> getString(Res.string.message_copy_failed)
    HistoryMessage.ShareFailed -> getString(Res.string.message_share_failed)
    HistoryMessage.OpenFailed -> getString(Res.string.message_open_failed)
    HistoryMessage.NothingToExport -> getString(Res.string.message_nothing_to_export)

    // iOS y el navegador no revelan dónde acabó el archivo, así que hay dos mensajes: uno que dice
    // el destino y otro que solo confirma. Fingir una ruta sería peor que no darla.
    is HistoryMessage.Exported ->
        message.location
            ?.let { getString(Res.string.message_exported_to, it) }
            ?: getString(Res.string.message_exported)

    is HistoryMessage.ExportFailed -> message.reason
}

private fun ExportFormat.labelResource(): StringResource = when (this) {
    ExportFormat.Csv -> Res.string.history_export_csv
    ExportFormat.Json -> Res.string.history_export_json
}

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

    is ShareableContent.Wifi ->
        password
            ?.let { stringResource(Res.string.share_wifi_with_password, ssid, it) }
            ?: stringResource(Res.string.share_wifi, ssid)

    is ShareableContent.Contact -> parts.joinToString(stringResource(Res.string.share_separator))
}
