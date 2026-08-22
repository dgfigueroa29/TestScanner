package com.whyscan.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId

/**
 * Fila del historial.
 *
 * Guarda **el valor decodificado y nunca la imagen**: es la garantía de privacidad RNF-03 y sigue
 * siéndolo ahora que el almacén es persistente. Tampoco se guardan los `cornerPoints`, que solo
 * tienen sentido durante la sesión en la que se detectó el código.
 *
 * Los enums se persisten por su `id` estable y no por `name` ni por ordinal: renombrar una constante
 * de Kotlin no debe invalidar el historial del usuario.
 */
@Entity(tableName = "detections")
data class DetectionEntity(
    @PrimaryKey val id: String,
    val rawValue: String,
    val formatId: String,
    val engineId: String,
    val sourceName: String,
    val detectedAtMillis: Long,
    val latencyMillis: Long?,
    /**
     * Nota del usuario, o `null`. Es el único campo de esta tabla que **no** lo escribe un motor,
     * y por eso `save()` no lo toca: sobrescribir la fila al reinsertar la misma lectura borraría lo
     * que el usuario escribió.
     */
    val note: String? = null,
)

/**
 * `note` no aparece: guardar una detección es un hecho del motor y no sabe nada de notas.
 *
 * Que falte aquí es lo que hace correcto el `upsert` con `REPLACE`. Si este mapeo pusiera `null`,
 * volver a leer el mismo código —mismo motor, mismo milisegundo, mismo valor— reemplazaría la fila y
 * se llevaría la nota por delante. El DAO lo resuelve conservando la nota existente al reinsertar.
 */
fun Detection.toEntity(): DetectionEntity = DetectionEntity(
    id = id,
    rawValue = barcode.rawValue,
    formatId = barcode.format.id,
    engineId = engineId.id,
    sourceName = source.name,
    detectedAtMillis = detectedAtMillis,
    latencyMillis = latencyMillis,
)

/**
 * Devuelve `null` si la fila referencia un motor que ya no existe en el código.
 *
 * Ocurre de verdad: si se elimina un motor del catálogo, las filas que dejó siguen en la base. Es
 * preferible ignorarlas al leer que hacer crashear el historial o inventar un motor.
 */
fun DetectionEntity.toDomain(): HistoryEntry? {
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
        // Normalizado también al leer: una fila escrita por una versión anterior podría traer `""`.
        note = HistoryEntry.normalizeNote(note),
    )
}
