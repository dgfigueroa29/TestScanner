package com.whyscan.feature.scanner.comparison

import com.whyscan.core.domain.scan.EngineMetrics
import com.whyscan.core.domain.scan.EngineScoreboard
import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScannerEngineId

/**
 * Estado del comparador.
 *
 * Vive en `:feature:scanner` y no en un módulo propio porque comparte por completo su grafo de
 * dependencias y su dominio con el escáner: separarlo añadiría un build file y un módulo de Koin
 * sin aislar nada.
 */
data class ComparisonState(
    val isRunning: Boolean = false,
    val participants: List<ScannerEngineId> = emptyList(),
    val scoreboard: EngineScoreboard = EngineScoreboard.Empty,
    val notEnoughEngines: Boolean = false,
    val error: ScanError? = null,
) {
    val entries: List<EngineMetrics> get() = scoreboard.entries

    val leader: EngineMetrics? get() = scoreboard.leader

    val hasResults: Boolean get() = entries.any { it.detections > 0 }
}

sealed interface ComparisonAction {
    data object Start : ComparisonAction
    data object Stop : ComparisonAction
    data object Reset : ComparisonAction
}
