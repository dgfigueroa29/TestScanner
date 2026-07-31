package com.testscanner.core.domain.export

import com.testscanner.core.model.Detection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Formatos en los que se puede sacar el historial de la app (RF-11). */
enum class ExportFormat(val extension: String, val mimeType: String) {
    Csv("csv", "text/csv"),
    Json("json", "application/json"),
}

/**
 * Convierte el historial en un archivo.
 *
 * Es lógica pura sobre [Detection], así que se prueba entera sin plataforma. Escribir el archivo —
 * elegir carpeta, pedir permisos, abrir el diálogo del sistema — es cosa de `FileSaver` en
 * `:core:platform`; aquí solo se decide **qué** contiene.
 *
 * ### Los nombres de columna no están en español
 * El resto de la app sí lo está, pero esto no es interfaz: es un archivo que abre una hoja de
 * cálculo o consume un script. Nombres estables en `snake_case` evitan que traducir la app rompa a
 * quien ya tenga algo montado sobre el CSV, y hacen que las columnas y las claves JSON coincidan.
 */
object HistoryExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val COLUMNS = listOf(
        "value",
        "format",
        "engine",
        "detected_at",
        "latency_ms",
        "value_type",
        "confidence",
    )

    fun export(detections: List<Detection>, format: ExportFormat): String = when (format) {
        ExportFormat.Csv -> toCsv(detections)
        ExportFormat.Json -> toJson(detections)
    }

    /** Nombre sugerido; el diálogo del sistema es quien resuelve colisiones. */
    fun fileName(format: ExportFormat): String = "historial-escaneos.${format.extension}"

    private fun toCsv(detections: List<Detection>): String = buildString {
        appendLine(COLUMNS.joinToString(SEPARATOR))
        detections.forEach { detection ->
            appendLine(
                listOf(
                    detection.barcode.rawValue,
                    detection.barcode.format.id,
                    detection.engineId.id,
                    detection.detectedAtMillis.toString(),
                    detection.latencyMillis?.toString().orEmpty(),
                    detection.barcode.valueType::class.simpleName.orEmpty(),
                    detection.barcode.confidence?.toString().orEmpty(),
                ).joinToString(SEPARATOR) { it.asCsvField() },
            )
        }
    }

    private fun toJson(detections: List<Detection>): String =
        json.encodeToString(ExportedHistory(detections.map { it.toExported() }))

    /**
     * Un campo CSV según RFC 4180, con una precaución añadida que no es cosmética.
     *
     * **El contenido de un código escaneado es texto que viene de fuera.** Excel, Numbers y Sheets
     * ejecutan como fórmula cualquier celda que empiece por `=`, `+`, `-` o `@`, así que un QR con
     * `=HYPERLINK(...)` dentro se convertiría en código corriendo en la máquina de quien abra el
     * archivo. Anteponer una comilla simple lo neutraliza: la hoja lo trata como texto literal.
     *
     * El precio es que el valor exportado no es byte a byte el escaneado en esos casos. Se asume:
     * el JSON sí lo conserva intacto, y ahí no hay nada que ejecute nada.
     */
    private fun String.asCsvField(): String {
        val guarded = if (firstOrNull() in FORMULA_STARTERS) "'$this" else this
        val needsQuotes = guarded.any { it in QUOTING_TRIGGERS } ||
            guarded != guarded.trim()

        return if (needsQuotes) "\"${guarded.replace("\"", "\"\"")}\"" else guarded
    }

    private fun Detection.toExported() = ExportedDetection(
        value = barcode.rawValue,
        format = barcode.format.id,
        engine = engineId.id,
        detectedAt = detectedAtMillis,
        latencyMs = latencyMillis,
        valueType = barcode.valueType::class.simpleName.orEmpty(),
        confidence = barcode.confidence,
    )

    private const val SEPARATOR = ","

    /** Caracteres con los que una hoja de cálculo interpreta la celda como fórmula. */
    private val FORMULA_STARTERS = setOf('=', '+', '-', '@', '\t', '\r')

    private val QUOTING_TRIGGERS = setOf('"', ',', '\n', '\r')
}

/**
 * Sobre de la exportación JSON.
 *
 * Lleva `count` aunque sea redundante con el tamaño de la lista: un archivo que dice cuántos
 * elementos debería tener permite detectar una exportación truncada, que es el fallo típico cuando
 * el destino se queda sin espacio.
 */
@Serializable
private data class ExportedHistory(
    val detections: List<ExportedDetection>,
) {
    val count: Int = detections.size
}

/**
 * Una detección tal y como sale al archivo.
 *
 * Es un DTO propio y no el [Detection] del modelo marcado como `@Serializable`, por dos razones:
 * el modelo no debe cargar con anotaciones de un formato de salida, y `Barcode` tiene un `ByteArray`
 * y un `BarcodeFormat` sellado que no se serializan solos. Además, así el formato del archivo es
 * una decisión explícita y estable en lugar de un reflejo del modelo interno: refactorizar el
 * dominio no rompe los archivos que alguien ya tenga.
 *
 * `detectedAt` va en milisegundos desde época y no en ISO-8601 porque formatear fechas exigiría
 * `kotlinx-datetime`, una dependencia entera para una columna. Queda documentado aquí.
 */
@Serializable
private data class ExportedDetection(
    val value: String,
    val format: String,
    val engine: String,
    val detectedAt: Long,
    val latencyMs: Long?,
    val valueType: String,
    val confidence: Float?,
)
