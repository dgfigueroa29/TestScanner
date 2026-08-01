package com.testscanner.feature.history

import com.testscanner.core.domain.export.ExportFormat
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId

data class HistoryState(
    val isLoading: Boolean = true,
    val detections: List<Detection> = emptyList(),
    /** `null` = sin filtro. Filtrar por motor es lo que hace comparable el historial (G5). */
    val engineFilter: ScannerEngineId? = null,
    val canShare: Boolean = false,
    /** Hay una exportación en curso: bloquea los botones para no abrir dos diálogos a la vez. */
    val isExporting: Boolean = false,
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

    /** Lleva el texto ya redactado: ver la nota gemela en `ScannerAction.RunResultAction`. */
    data class RunResultAction(val action: ResultAction, val text: String) : HistoryAction

    /** Sacar el historial a un archivo (RF-11). */
    data class Export(val format: ExportFormat) : HistoryAction
    data object Clear : HistoryAction
}

/** Eventos de una sola vez del historial. */
sealed interface HistoryEffect {
    data class ShowMessage(val message: HistoryMessage) : HistoryEffect
}
