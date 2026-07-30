package com.testscanner.feature.history

import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId

data class HistoryState(
    val isLoading: Boolean = true,
    val detections: List<Detection> = emptyList(),
    /** `null` = sin filtro. Filtrar por motor es lo que hace comparable el historial (G5). */
    val engineFilter: ScannerEngineId? = null,
) {
    val visible: List<Detection>
        get() = engineFilter?.let { id -> detections.filter { it.engineId == id } } ?: detections

    /** Motores que aparecen en el historial, para ofrecer solo filtros con resultados. */
    val presentEngines: List<ScannerEngineId>
        get() = detections.map { it.engineId }.distinct().sortedBy { it.id }

    val isEmpty: Boolean get() = !isLoading && detections.isEmpty()
}

sealed interface HistoryAction {
    data class FilterByEngine(val id: ScannerEngineId?) : HistoryAction
    data object Clear : HistoryAction
}
