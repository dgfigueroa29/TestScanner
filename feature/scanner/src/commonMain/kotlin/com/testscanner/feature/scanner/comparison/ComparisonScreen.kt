package com.testscanner.feature.scanner.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.domain.scan.EngineMetrics
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComparisonScreen(viewModel: ComparisonViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ComparisonContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun ComparisonContent(
    state: ComparisonState,
    onAction: (ComparisonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { Header(state, onAction) }

        if (state.hasResults) {
            items(state.entries, key = { it.engineId.id }) { metrics ->
                MetricsCard(metrics, isLeader = metrics.engineId == state.leader?.engineId)
            }
        }
    }
}

@Composable
private fun Header(state: ComparisonState, onAction: (ComparisonAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Comparar motores",
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.notEnoughEngines) {
                Text(
                    text = "Hacen falta al menos dos motores disponibles para comparar. " +
                        "Ahora mismo hay ${state.participants.size}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Participan: ${state.participants.joinToString { it.id }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Button(
                    onClick = { onAction(ComparisonAction.Start) },
                    enabled = !state.isRunning && !state.notEnoughEngines,
                ) {
                    Text("Comparar")
                }
                OutlinedButton(
                    onClick = { onAction(ComparisonAction.Stop) },
                    enabled = state.isRunning,
                ) {
                    Text("Detener")
                }
                OutlinedButton(onClick = { onAction(ComparisonAction.Reset) }) { Text("Reiniciar") }
            }

            if (!state.hasResults && !state.notEnoughEngines) {
                Text(
                    text = "Apuntá al mismo código con la comparación en marcha: cada motor lo " +
                        "leerá por su cuenta y el marcador mostrará quién acierta antes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(metrics: EngineMetrics, isLeader: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = if (isLeader) "${metrics.engineId.id} · mejor" else metrics.engineId.id,
                style = MaterialTheme.typography.titleSmall,
                color = if (isLeader) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "${metrics.uniqueValues} códigos distintos · " +
                    "${metrics.detections} lecturas",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = buildString {
                    append("primera: ")
                    append(metrics.firstDetectionLatencyMillis?.let { "$it ms" } ?: "—")
                    append(" · media: ")
                    append(metrics.averageLatencyMillis?.let { "$it ms" } ?: "—")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("${metrics.framesAnalyzed} frames")
                    metrics.framesPerDetection?.let { append(" · $it por lectura") }
                    if (metrics.transientFailures > 0) {
                        append(" · ${metrics.transientFailures} fallos")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
