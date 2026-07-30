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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.model.Detection
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                text = "Todavía no escaneaste nada",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Column(modifier = modifier.fillMaxSize().padding(Spacing.md)) {
            EngineFilters(state, onAction)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.visible, key = { it.id }) { HistoryRow(it) }
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
                    label = { Text("Todos") },
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

        OutlinedButton(onClick = { onAction(HistoryAction.Clear) }) { Text("Borrar") }
    }
}

@Composable
private fun HistoryRow(detection: Detection) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(detection.barcode.rawValue, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = buildString {
                    append(detection.barcode.format.displayName)
                    append(" · ")
                    append(detection.engineId.id)
                    detection.latencyMillis?.let { append(" · $it ms") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
