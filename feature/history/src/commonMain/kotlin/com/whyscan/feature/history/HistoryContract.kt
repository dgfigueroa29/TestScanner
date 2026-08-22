package com.whyscan.feature.history

import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId

data class HistoryState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    /** `null` = sin filtro. Filtrar por motor es lo que hace comparable el historial (G5). */
    val engineFilter: ScannerEngineId? = null,
    /** Lo que el usuario escribió en el buscador. Vacío = sin búsqueda. */
    val query: String = "",
    /** Id de la fila cuya nota se está editando, o `null` si no hay ninguna abierta. */
    val editingNoteFor: String? = null,
    val canShare: Boolean = false,
    /** Hay una exportación en curso: bloquea los botones para no abrir dos diálogos a la vez. */
    val isExporting: Boolean = false,
    /** Está pidiendo confirmación para vaciar el historial entero. */
    val isConfirmingClear: Boolean = false,
) {
    /**
     * Lo que se ve: filtro de motor y búsqueda, en ese orden.
     *
     * La búsqueda mira el valor **y la nota**, que es la mitad del sentido de poder anotar: quien
     * escribe "factura marzo" sobre un código lo escribe justamente para poder encontrarlo luego por
     * ahí y no por una tirada de dígitos que nadie recuerda.
     */
    val visible: List<HistoryEntry>
        get() = entries
            .filter { engineFilter == null || it.detection.engineId == engineFilter }
            .filter { it.matches(query) }

    /** Motores que aparecen en el historial, para ofrecer solo filtros con resultados. */
    val presentEngines: List<ScannerEngineId>
        get() = entries.map { it.detection.engineId }.distinct().sortedBy { it.id }

    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()

    /** Hay historial, pero el filtro o la búsqueda no dejan nada. Es un vacío distinto del otro. */
    val isFilteredEmpty: Boolean get() = !isLoading && entries.isNotEmpty() && visible.isEmpty()
}

/**
 * Búsqueda por subcadena, sin distinguir mayúsculas, sobre el valor y la nota.
 *
 * Es deliberadamente tonta: sin tokenizar, sin normalizar acentos y sin puntuación. Un historial son
 * unos cientos de filas y el usuario busca lo que él mismo escribió hace un rato, así que "empieza a
 * teclear y va quedando menos" es exactamente el comportamiento esperado. Cualquier cosa más lista
 * sorprendería más de lo que ayuda.
 */
private fun HistoryEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true

    val needle = query.trim()
    return detection.barcode.rawValue.contains(needle, ignoreCase = true) ||
        note?.contains(needle, ignoreCase = true) == true
}

sealed interface HistoryAction {
    data class FilterByEngine(val id: ScannerEngineId?) : HistoryAction

    data class Search(val query: String) : HistoryAction

    /** Lleva el texto ya redactado: ver la nota gemela en `ScannerAction.RunResultAction`. */
    data class RunResultAction(val action: ResultAction, val text: String) : HistoryAction

    /** Abre —o cierra, con `null`— el campo de nota de una fila. */
    data class EditNote(val detectionId: String?) : HistoryAction

    /** Guarda la nota. Una cadena vacía o en blanco borra la que hubiera. */
    data class SetNote(val detectionId: String, val note: String) : HistoryAction

    /** Borra una sola lectura. No pide confirmación: es una fila y se ve cuál. */
    data class Delete(val detectionId: String) : HistoryAction

    /** Sacar el historial a un archivo (RF-11). */
    data class Export(val format: ExportFormat) : HistoryAction

    /**
     * Vaciar el historial pasa por confirmación, y [Clear] es solo el segundo paso.
     *
     * Borrar hasta quinientas lecturas —con sus notas— es irreversible y no hay copia en ninguna
     * parte: ni cuenta, ni nube, ni papelera. Que un toque en un botón de una barra hiciera eso
     * directamente era la única acción destructiva y silenciosa que quedaba en la app.
     */
    data object ConfirmClear : HistoryAction
    data object DismissClear : HistoryAction
    data object Clear : HistoryAction
}

/** Eventos de una sola vez del historial. */
sealed interface HistoryEffect {
    data class ShowMessage(val message: HistoryMessage) : HistoryEffect
}
