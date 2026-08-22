package com.whyscan.core.database

import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Historial persistente sobre Room (salda la deuda D3).
 *
 * Sustituye a `InMemoryScanHistoryRepository` sin que dominio ni UI cambien una línea: esa era la
 * razón de definir `ScanHistoryRepository` en la Fase 1 con una implementación en memoria detrás.
 */
class RoomScanHistoryRepository(
    private val dao: DetectionDao,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : ScanHistoryRepository {

    override fun observeHistory(): Flow<List<HistoryEntry>> =
        // `mapNotNull` sobre las filas: una entrada de un motor eliminado del catálogo se ignora en
        // lugar de romper el historial entero.
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun save(detection: Detection) {
        dao.insertIgnoringDuplicates(detection.toEntity())
        dao.trimTo(maxEntries)
    }

    override suspend fun setNote(detectionId: String, note: String?) = dao.setNote(detectionId, note)

    override suspend fun delete(detectionId: String) = dao.delete(detectionId)

    override suspend fun clear() = dao.clear()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
