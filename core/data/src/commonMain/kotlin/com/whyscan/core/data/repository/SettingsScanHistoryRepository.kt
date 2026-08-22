package com.whyscan.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Historial persistente sobre `multiplatform-settings` (salda la deuda D9).
 *
 * ### Por qué esto y no IndexedDB
 * El roadmap pedía IndexedDB, que es la respuesta de manual para "persistir en el navegador". No es
 * la respuesta correcta aquí:
 *
 * - Un historial de escaneos son unos cientos de filas de texto. Con los campos que se guardan
 *   —los mismos que la tabla de Room— unas 500 entradas ocupan del orden de 60 KB, contra los ~5 MB
 *   que da `localStorage`. No hace falta un almacén sin límite.
 * - `ScanHistoryRepository` no consulta: observa, guarda, busca por id y vacía. No hay índices ni
 *   rangos que justifiquen una base de datos.
 * - IndexedDB son unas cien líneas de interop con callbacks **que en este proyecto no se pueden
 *   probar**. Esto es Kotlin común y corre en `commonTest` con `MapSettings`. Entre dos soluciones
 *   que nadie puede ejecutar todavía, gana la que sí se puede verificar.
 *
 * El compromiso es que cada guardado reescribe la lista entera. Con este tamaño es irrelevante, y
 * [maxEntries] pone un techo para que no deje de serlo.
 *
 * ### Qué se guarda
 * Exactamente los mismos campos que la tabla de Room, y por los mismos motivos: **nunca la imagen**
 * (RNF-03) ni los `cornerPoints`, que solo tienen sentido durante la sesión. Así el historial de
 * Web y el de las otras tres plataformas contienen lo mismo, que es lo que hace comparables las
 * exportaciones.
 */
class SettingsScanHistoryRepository(
    private val settings: Settings,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : ScanHistoryRepository {

    private val state = MutableStateFlow(load())

    override fun observeHistory(): Flow<List<HistoryEntry>> = state.asStateFlow()

    /**
     * Una lectura repetida no reemplaza a la anterior: la ignora.
     *
     * Es la misma regla que aplican Room y el historial en memoria, y por el mismo motivo — el id es
     * determinista, así que la fila en conflicto es la misma lectura, pero **puede tener una nota
     * encima**. Reemplazarla la borraría.
     */
    override suspend fun save(detection: Detection) {
        state.update { current ->
            if (current.any { it.id == detection.id }) {
                current
            } else {
                (listOf(HistoryEntry(detection)) + current).trimmedKeepingNotes(maxEntries)
            }
        }
        persist()
    }

    override suspend fun setNote(detectionId: String, note: String?) {
        state.update { current ->
            current.map { if (it.id == detectionId) it.copy(note = note) else it }
        }
        persist()
    }

    override suspend fun delete(detectionId: String) {
        state.update { current -> current.filterNot { it.id == detectionId } }
        persist()
    }

    override suspend fun clear() {
        state.value = emptyList()
        persist()
    }

    private fun load(): List<HistoryEntry> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()

        // Un historial ilegible se descarta en lugar de reventar la app. Pasa de verdad: basta con
        // que una versión anterior guardara otro formato, o con que el almacén truncara la cadena
        // al llenarse. Perder el historial es malo; no poder abrir la app es peor.
        return runCatching { json.decodeFromString(STORED_SERIALIZER, stored) }
            .getOrElse { emptyList() }
            .mapNotNull { it.toDomain() }
    }

    /**
     * Escribir puede fallar si el almacén está lleno — `localStorage` lanza al superar su cuota.
     *
     * Se traga el fallo a propósito: la detección ya está en memoria y el usuario la ve en pantalla.
     * Propagar la excepción convertiría "no cabe una entrada más" en un escaneo fallido.
     */
    private fun persist() {
        runCatching {
            settings[KEY] = json.encodeToString(STORED_SERIALIZER, state.value.map { it.toStored() })
        }
    }

    private companion object {
        const val KEY = "scan_history"

        /**
         * Techo de entradas guardadas.
         *
         * No es arbitrario: por debajo del punto en que reescribir la lista entera en cada guardado
         * empieza a notarse, y muy por debajo de la cuota de `localStorage`.
         */
        const val DEFAULT_MAX_ENTRIES = 500

        val json = Json { ignoreUnknownKeys = true }

        val STORED_SERIALIZER = ListSerializer(StoredDetection.serializer())
    }
}

/**
 * Una detección tal y como se guarda.
 *
 * Es un DTO propio y no [Detection] anotado, por lo mismo que en la exportación: el modelo no debe
 * cargar con anotaciones de un formato de almacenamiento, y así cambiar el dominio no invalida el
 * historial que el usuario ya tenga.
 *
 * Los enums se guardan por su `id` estable y no por `name` ni por ordinal: renombrar una constante
 * de Kotlin no debe borrarle el historial a nadie.
 */
@Serializable
internal data class StoredDetection(
    val id: String,
    val rawValue: String,
    val formatId: String,
    val engineId: String,
    val sourceName: String,
    val detectedAtMillis: Long,
    val latencyMillis: Long?,
    /**
     * Nota del usuario. Lleva valor por defecto **a propósito**: sin él, un historial guardado por
     * una versión anterior —donde esta clave no existía— dejaría de decodificarse entero, y `load()`
     * lo descartaría en bloque. Con el defecto, las entradas viejas se leen sin nota, que es lo
     * correcto. Es la mitad simétrica de la migración de Room: nadie pierde su historial por una
     * actualización, tampoco en el navegador.
     */
    val note: String? = null,
)

internal fun HistoryEntry.toStored() = StoredDetection(
    id = detection.id,
    rawValue = detection.barcode.rawValue,
    formatId = detection.barcode.format.id,
    engineId = detection.engineId.id,
    sourceName = detection.source.name,
    detectedAtMillis = detection.detectedAtMillis,
    latencyMillis = detection.latencyMillis,
    note = note,
)

/**
 * Devuelve `null` si la entrada referencia un motor que ya no existe en el código.
 *
 * Mismo criterio que la tabla de Room: si se elimina un motor del catálogo, sus entradas siguen
 * guardadas. Ignorarlas al leer es preferible a reventar el historial o a inventar un motor.
 */
internal fun StoredDetection.toDomain(): HistoryEntry? {
    val engine = ScannerEngineId.fromId(engineId) ?: return null

    return HistoryEntry(
        detection = Detection(
            id = id,
            barcode = Barcode(rawValue = rawValue, format = BarcodeFormat.fromId(formatId)),
            engineId = engine,
            detectedAtMillis = detectedAtMillis,
            latencyMillis = latencyMillis,
            source = ScanSource.entries.firstOrNull { it.name == sourceName } ?: ScanSource.LiveCamera,
        ),
        note = HistoryEntry.normalizeNote(note),
    )
}
