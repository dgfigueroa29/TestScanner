package com.testscanner.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
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

    override fun observeHistory(): Flow<List<Detection>> = state.asStateFlow()

    override suspend fun save(detection: Detection) {
        state.update { (listOf(detection) + it).take(maxEntries) }
        persist()
    }

    override suspend fun findById(id: String): Detection? =
        state.value.firstOrNull { it.id == id }

    override suspend fun clear() {
        state.value = emptyList()
        persist()
    }

    private fun load(): List<Detection> {
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
)

internal fun Detection.toStored() = StoredDetection(
    id = id,
    rawValue = barcode.rawValue,
    formatId = barcode.format.id,
    engineId = engineId.id,
    sourceName = source.name,
    detectedAtMillis = detectedAtMillis,
    latencyMillis = latencyMillis,
)

/**
 * Devuelve `null` si la entrada referencia un motor que ya no existe en el código.
 *
 * Mismo criterio que la tabla de Room: si se elimina un motor del catálogo, sus entradas siguen
 * guardadas. Ignorarlas al leer es preferible a reventar el historial o a inventar un motor.
 */
internal fun StoredDetection.toDomain(): Detection? {
    val engine = ScannerEngineId.fromId(engineId) ?: return null

    return Detection(
        id = id,
        barcode = Barcode(rawValue = rawValue, format = BarcodeFormat.fromId(formatId)),
        engineId = engine,
        detectedAtMillis = detectedAtMillis,
        latencyMillis = latencyMillis,
        source = ScanSource.entries.firstOrNull { it.name == sourceName } ?: ScanSource.LiveCamera,
    )
}
