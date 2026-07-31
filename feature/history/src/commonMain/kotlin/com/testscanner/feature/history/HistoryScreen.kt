package com.testscanner.feature.history

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.export.ExportFormat
import com.testscanner.core.domain.scan.OpenKind
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.model.Detection
import com.testscanner.feature.history.resources.Res
import com.testscanner.feature.history.resources.history_clear
import com.testscanner.feature.history.resources.history_empty
import com.testscanner.feature.history.resources.history_export_csv
import com.testscanner.feature.history.resources.history_export_json
import com.testscanner.feature.history.resources.history_filter_all
import com.testscanner.feature.history.resources.history_row_latency
import com.testscanner.feature.history.resources.history_row_meta
import com.testscanner.feature.history.resources.message_copied
import com.testscanner.feature.history.resources.message_copy_failed
import com.testscanner.feature.history.resources.message_exported
import com.testscanner.feature.history.resources.message_exported_to
import com.testscanner.feature.history.resources.message_nothing_to_export
import com.testscanner.feature.history.resources.message_open_failed
import com.testscanner.feature.history.resources.message_share_failed
import com.testscanner.feature.history.resources.result_copy
import com.testscanner.feature.history.resources.result_open_email
import com.testscanner.feature.history.resources.result_open_link
import com.testscanner.feature.history.resources.result_open_map
import com.testscanner.feature.history.resources.result_open_phone
import com.testscanner.feature.history.resources.result_open_sms
import com.testscanner.feature.history.resources.result_share
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowMessage -> snackbarHostState.showSnackbar(resolve(effect.message))
            }
        }
    }

    HistoryContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun HistoryContent(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
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
            EngineFilters(state, onAction)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.visible, key = { it.id }) { detection ->
                    HistoryRow(detection, canShare = state.canShare, onAction = onAction)
                }
            }
        }
    }
}

/**
 * Filtrar por motor es lo que convierte el historial en una herramienta de comparación: permite ver
 * qué leyó cada alternativa sobre los mismos códigos.
 */
@Composable
private fun EngineFilters(state: HistoryState, onAction: (HistoryAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
    onAction: (HistoryAction) -> Unit,
) {
    val actions = ResultActionsFactory.actionsFor(detection.barcode, canShare)

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
                            Res.string.history_row_meta,
                            detection.barcode.format.displayName,
                            detection.engineId.id,
                        ),
                    )
                    detection.latencyMillis?.let {
                        append(stringResource(Res.string.history_row_latency, it))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                actions.forEach { action ->
                    TextButton(
                        onClick = { onAction(HistoryAction.RunResultAction(detection, action)) },
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

/** El ViewModel dice qué pasó; aquí se le pone nombre. */
@Composable
private fun resolve(message: HistoryMessage): String = when (message) {
    HistoryMessage.Copied -> stringResource(Res.string.message_copied)
    HistoryMessage.CopyFailed -> stringResource(Res.string.message_copy_failed)
    HistoryMessage.ShareFailed -> stringResource(Res.string.message_share_failed)
    HistoryMessage.OpenFailed -> stringResource(Res.string.message_open_failed)
    HistoryMessage.NothingToExport -> stringResource(Res.string.message_nothing_to_export)

    // iOS y el navegador no revelan dónde acabó el archivo, así que hay dos mensajes: uno que dice
    // el destino y otro que solo confirma. Fingir una ruta sería peor que no darla.
    is HistoryMessage.Exported -> message.location
        ?.let { stringResource(Res.string.message_exported_to, it) }
        ?: stringResource(Res.string.message_exported)

    is HistoryMessage.ExportFailed -> message.reason
}

private fun ExportFormat.labelResource(): StringResource = when (this) {
    ExportFormat.Csv -> Res.string.history_export_csv
    ExportFormat.Json -> Res.string.history_export_json
}
