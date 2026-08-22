package com.whyscan.core.data.repository

import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Preferencias en memoria (deuda D1 del roadmap).
 *
 * Se sustituirá por `multiplatform-settings` en la Fase 2. La interfaz existe desde ya para que la
 * feature se construya contra el contrato y ese cambio no toque dominio ni UI.
 */
class InMemoryScanPreferencesRepository(
    initial: ScanPreferences = ScanPreferences(),
) : ScanPreferencesRepository {

    private val state = MutableStateFlow(initial)

    override fun observePreferences(): Flow<ScanPreferences> = state.asStateFlow()

    override suspend fun current(): ScanPreferences = state.first()

    override suspend fun setPreferredEngine(id: ScannerEngineId?) {
        state.update { it.copy(preferredEngineId = id) }
    }

    override suspend fun setFormats(formats: Set<BarcodeFormat>) {
        state.update { it.copy(formats = formats) }
    }

    override suspend fun setContinuous(enabled: Boolean) {
        state.update { it.copy(continuous = enabled) }
    }

    override suspend fun setAllowMultiple(enabled: Boolean) {
        state.update { it.copy(allowMultiple = enabled) }
    }
}

/**
 * Historial en memoria (deuda D3 del roadmap), acotado a [MAX_ENTRIES] entradas.
 *
 * Guarda el **valor decodificado y nunca la imagen**, que es la garantía de privacidad RNF-03 y
 * seguirá siéndolo cuando el almacén pase a ser Room en la Fase 2.
 */
class InMemoryScanHistoryRepository : ScanHistoryRepository {

    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override fun observeHistory(): Flow<List<HistoryEntry>> = state.asStateFlow()

    override suspend fun save(detection: Detection) {
        state.update { current ->
            if (current.any { it.id == detection.id }) {
                current
            } else {
                (listOf(HistoryEntry(detection)) + current).trimmedKeepingNotes(MAX_ENTRIES)
            }
        }
    }

    override suspend fun setNote(detectionId: String, note: String?) {
        state.update { current ->
            current.map { if (it.id == detectionId) it.copy(note = note) else it }
        }
    }

    override suspend fun delete(detectionId: String) {
        state.update { current -> current.filterNot { it.id == detectionId } }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
