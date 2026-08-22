package com.whyscan.core.domain.repository

import com.whyscan.core.domain.model.EngineStatus
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.BarcodeScannerEngine
import kotlinx.coroutines.flow.Flow

/**
 * Acceso al catálogo de motores y a las instancias realmente enlazadas en este binario.
 *
 * La implementación vive en `:core:data`; el dominio solo conoce esta interfaz, de modo que la
 * política de selección se puede testear con un catálogo falso, sin cámara ni dispositivo.
 */
interface ScannerEngineRepository {
    val platform: ScannerPlatform

    /** Catálogo completo con la disponibilidad actual de cada motor. */
    fun observeCatalog(): Flow<List<EngineStatus>>

    /** Vuelve a consultar `availability()` de cada motor instalado. */
    suspend fun refresh()

    /** Instancia del motor, o `null` si no está enlazado en esta plataforma. */
    fun engine(id: ScannerEngineId): BarcodeScannerEngine?

    suspend fun status(id: ScannerEngineId): EngineStatus?
}

/** Ajustes de escaneo del usuario. */
data class ScanPreferences(
    /** `null` = selección automática. */
    val preferredEngineId: ScannerEngineId? = null,
    val formats: Set<BarcodeFormat> = BarcodeFormat.all,
    val continuous: Boolean = false,
    val allowMultiple: Boolean = false,
)

interface ScanPreferencesRepository {
    fun observePreferences(): Flow<ScanPreferences>
    suspend fun current(): ScanPreferences
    suspend fun setPreferredEngine(id: ScannerEngineId?)
    suspend fun setFormats(formats: Set<BarcodeFormat>)
    suspend fun setContinuous(enabled: Boolean)
    suspend fun setAllowMultiple(enabled: Boolean)
}

/**
 * Historial de escaneos.
 *
 * En la Fase 1 la implementación es en memoria; la interfaz existe desde ya para que la feature se
 * construya contra el contrato y el cambio a Room KMP (Fase 2) no toque dominio ni UI.
 *
 * **Entra una [Detection] y salen [HistoryEntry]s.** La asimetría es la del dominio y no un
 * descuido: guardar es un hecho de máquina —un motor leyó algo— y leer devuelve además lo que el
 * usuario haya anotado encima. El motivo largo está en `HistoryEntry`.
 */
interface ScanHistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntry>>

    suspend fun save(detection: Detection)

    /**
     * Asocia —o borra, con `null`— la nota de una fila. Si la fila no existe no hace nada: puede
     * haberse podado o haberla borrado el usuario mientras tenía el campo abierto.
     */
    suspend fun setNote(detectionId: String, note: String?)

    /** Borra una sola fila. Vaciar el historial entero es [clear]. */
    suspend fun delete(detectionId: String)

    suspend fun clear()
}
