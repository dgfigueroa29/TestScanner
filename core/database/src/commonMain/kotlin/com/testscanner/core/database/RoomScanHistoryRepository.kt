package com.testscanner.core.database

import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.model.Detection
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

    override fun observeHistory(): Flow<List<Detection>> =
        // `mapNotNull` sobre las filas: una entrada de un motor eliminado del catálogo se ignora en
        // lugar de romper el historial entero.
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun save(detection: Detection) {
        dao.upsert(detection.toEntity())
        dao.trimTo(maxEntries)
    }

    override suspend fun findById(id: String): Detection? = dao.findById(id)?.toDomain()

    override suspend fun clear() = dao.clear()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
