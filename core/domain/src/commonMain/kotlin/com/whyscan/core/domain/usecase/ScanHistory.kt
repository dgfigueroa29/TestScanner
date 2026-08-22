package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * El historial, como **un solo colaborador**.
 *
 * ### Por qué existe, y por qué no son cuatro casos de uso
 * Es la misma decisión que tomó [ScanSettings] al saldar la deuda D16, aplicada antes de repetir el
 * error en vez de después. Añadir la nota y el borrado por fila pedía dos clases nuevas de una línea
 * al lado de `ObserveScanHistoryUseCase` y `ClearScanHistoryUseCase`, que ya delegaban sin añadir
 * nada: cuatro nombres, cuatro registros en Koin y cuatro parámetros en el constructor del ViewModel
 * para una sola idea. Los dos que había se borraron y su trabajo está aquí.
 *
 * `SaveDetectionUseCase` **se queda fuera** y no es una inconsistencia: lo usa el escáner al leer un
 * código, que es un camino distinto y no quiere arrastrar el borrado ni las notas. Guardar es del
 * motor; anotar y borrar son del usuario.
 *
 * La única regla que hay vive en [setNote], y es justo el tipo de cosa que se pierde cuando cada
 * operación es un `class` que delega: normalizar en un sitio para que las tres plataformas guarden
 * lo mismo.
 */
class ScanHistory(
    private val repository: ScanHistoryRepository,
) {

    /** Más reciente primero. */
    fun observe(): Flow<List<HistoryEntry>> = repository.observeHistory()

    /**
     * Asocia una nota a una lectura, o la borra pasando `null`.
     *
     * Un campo de texto devuelve `""` cuando el usuario borra lo que había, y espacios cuando se le
     * escapa la barra espaciadora. Ninguna de las dos cosas es una nota: se normalizan a `null` aquí
     * —una vez, para las tres implementaciones— de modo que "tiene nota" signifique lo mismo en
     * Android, en el navegador y en los tests.
     */
    suspend fun setNote(detectionId: String, note: String?) {
        repository.setNote(detectionId, HistoryEntry.normalizeNote(note))
    }

    /** Borra una lectura del historial. Se puede deshacer con [restore]. */
    suspend fun delete(detectionId: String) = repository.delete(detectionId)

    /** Devuelve al historial una lectura borrada, con su nota y en su sitio por fecha. */
    suspend fun restore(entry: HistoryEntry) = repository.restore(entry)

    /** Vacía el historial entero. */
    suspend fun clear() = repository.clear()
}
