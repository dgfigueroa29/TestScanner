package com.whyscan.feature.history

import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import kotlinx.datetime.TimeZone

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

    /**
     * Lo visible, agrupado por día.
     *
     * Toma la zona horaria en lugar de leerla del sistema para que la agrupación sea **pura**: con
     * `TimeZone.currentSystemDefault()` dentro, un test pasaría o fallaría según dónde corriera el
     * runner. La pantalla le pasa la del dispositivo y los tests una fija.
     */
    fun visibleGroups(timeZone: TimeZone): List<HistoryGroup> = visible.groupByDay(timeZone)
}

/**
 * Búsqueda por subcadena sobre el valor y la nota, **sin distinguir mayúsculas ni acentos**.
 *
 * Sigue siendo deliberadamente simple —sin tokenizar y sin puntuación—: un historial son unos
 * cientos de filas y el usuario busca lo que él mismo escribió hace un rato, así que "empiezo a
 * teclear y va quedando menos" es exactamente lo que espera.
 *
 * Los acentos sí importan y por eso se quitan. En español la mitad de la gente escribe "factura" al
 * buscar lo que guardó como "Factúra", y quien busca desde un teclado sin tildes no tiene otra
 * opción. Que la búsqueda no encuentre algo que está delante es el peor fallo posible de un
 * buscador: no parece un fallo, parece que el dato no existe.
 */
private fun HistoryEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true

    val needle = query.trim().foldForSearch()
    return detection.barcode.rawValue.foldForSearch().contains(needle) ||
        note?.foldForSearch()?.contains(needle) == true
}

/**
 * Minúsculas y sin diacríticos, para comparar.
 *
 * Es un mapa a mano y no `java.text.Normalizer`, que no existe en `commonMain` — y traer una
 * librería de normalización Unicode para esto sería desproporcionado. Cubre los diacríticos de los
 * dos idiomas que la app habla, más la diéresis y la eñe, que es exactamente el alcance del
 * problema. Si algún día hay un tercer idioma, esta función es el sitio donde se ve qué falta.
 */
private fun String.foldForSearch(): String = lowercase().map { ACCENTS[it] ?: it }.joinToString("")

private val ACCENTS: Map<Char, Char> = buildMap {
    "áàäâã".forEach { put(it, 'a') }
    "éèëê".forEach { put(it, 'e') }
    "íìïî".forEach { put(it, 'i') }
    "óòöôõ".forEach { put(it, 'o') }
    "úùüû".forEach { put(it, 'u') }
    put('ç', 'c')
    // La eñe **no** se pliega a `n`: en español es una letra distinta, no una `n` con adorno, y
    // hacer que "ano" encuentre "año" sería un resultado desconcertante en el mejor de los casos.
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

    /**
     * Borra una sola lectura.
     *
     * **No pide confirmación, y por eso puede deshacerse.** Son las dos caras de la misma decisión:
     * un diálogo por cada fila que se borra convierte una tarea de limpiar veinte lecturas en veinte
     * interrupciones, mientras que un "Deshacer" en el aviso cuesta un toque solo a quien se
     * equivocó. Vaciar el historial entero sí pregunta, porque ahí no hay nada que devolver.
     */
    data class Delete(val detectionId: String) : HistoryAction

    /** Devuelve al historial la última lectura borrada. */
    data object UndoDelete : HistoryAction

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
    /**
     * @param undoable si el aviso debe ofrecer "Deshacer". Es un booleano y no otro tipo de efecto
     *   porque quien decide **qué** se deshace es el ViewModel, que guarda lo borrado; la pantalla
     *   solo necesita saber si pintar el botón.
     */
    data class ShowMessage(
        val message: HistoryMessage,
        val undoable: Boolean = false,
    ) : HistoryEffect
}
