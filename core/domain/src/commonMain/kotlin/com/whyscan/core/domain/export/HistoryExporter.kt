package com.whyscan.core.domain.export

import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Formatos en los que se puede sacar el historial de la app (RF-11). */
enum class ExportFormat(val extension: String, val mimeType: String) {
    Csv("csv", "text/csv"),
    Json("json", "application/json"),

    /**
     * Una lectura por línea, sin cabecera ni comillas.
     *
     * CSV y JSON son para herramientas; esto es para personas. Lo que la gente hace de verdad con
     * treinta códigos escaneados es pegarlos en un correo, en un chat o en una celda, y para eso los
     * otros dos formatos estorban: uno mete comillas y comas donde nadie las quiere y el otro es
     * ilegible sin un visor.
     */
    Text("txt", "text/plain"),
}

/**
 * Convierte el historial en un archivo.
 *
 * Es lógica pura sobre [HistoryEntry], así que se prueba entera sin plataforma. Escribir el archivo —
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
        // Última a propósito: quien tenga un script leyendo por posición no se rompe al añadirla.
        "note",
    )

    fun export(entries: List<HistoryEntry>, format: ExportFormat): String = when (format) {
        ExportFormat.Csv -> toCsv(entries)
        ExportFormat.Json -> toJson(entries)
        ExportFormat.Text -> toText(entries)
    }

    /** Nombre sugerido; el diálogo del sistema es quien resuelve colisiones. */
    fun fileName(format: ExportFormat): String = "historial-escaneos.${format.extension}"

    private fun toCsv(entries: List<HistoryEntry>): String = buildString {
        appendLine(COLUMNS.joinToString(SEPARATOR))
        entries.forEach { entry ->
            val detection = entry.detection
            appendLine(
                listOf(
                    detection.barcode.rawValue,
                    detection.barcode.format.id,
                    detection.engineId.id,
                    detection.detectedAtMillis.toString(),
                    detection.latencyMillis?.toString().orEmpty(),
                    detection.barcode.valueType.id,
                    detection.barcode.confidence?.toString().orEmpty(),
                    // La nota pasa por `asCsvField` como todo lo demás, y no es una formalidad: es
                    // texto libre que escribe una persona, así que puede empezar por `-` o `=` sin
                    // ninguna mala intención y llevar comas y saltos de línea con toda naturalidad.
                    entry.note.orEmpty(),
                ).joinToString(SEPARATOR) { it.asCsvField() },
            )
        }
    }

    /**
     * Una lectura por línea: el valor, y la nota detrás cuando la hay.
     *
     * **Sin guardado anti-fórmula**, y es deliberado: esto no lo abre una hoja de cálculo, y meter
     * una comilla delante de un valor que empieza por `-` rompería justo lo que este formato existe
     * para dar — el valor tal cual, listo para pegar. Quien lo lleve a una hoja tiene el CSV, que sí
     * lo protege. El formato dice para qué es y se comporta en consecuencia.
     *
     * Una nota con saltos de línea rompería el "una lectura por línea", así que se aplanan. Es lo
     * único que se toca.
     */
    private fun toText(entries: List<HistoryEntry>): String = buildString {
        entries.forEach { entry ->
            append(entry.detection.barcode.rawValue)
            entry.note?.let { append(NOTE_SEPARATOR).append(it.replace(NEWLINES, " ")) }
            appendLine()
        }
    }

    // Con el serializador explícito y no con la variante `reified`: esa resuelve el serializador
    // por reflexión sobre la clase, que es justo lo que R8 puede dejar sin nombre en release.
    private fun toJson(entries: List<HistoryEntry>): String = json.encodeToString(
        ExportedHistory.serializer(),
        ExportedHistory(entries.map { it.toExported() }),
    )

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

    private fun HistoryEntry.toExported() = ExportedDetection(
        value = detection.barcode.rawValue,
        format = detection.barcode.format.id,
        engine = detection.engineId.id,
        detectedAt = detection.detectedAtMillis,
        latencyMs = detection.latencyMillis,
        valueType = detection.barcode.valueType.id,
        confidence = detection.barcode.confidence,
        note = note,
    )

    private const val SEPARATOR = ","

    /** Separa el valor de la nota en el formato de texto. Legible y difícil de confundir con datos. */
    private const val NOTE_SEPARATOR = "  —  "

    private val NEWLINES = Regex("[\\r\\n]+")

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
    /** `null` cuando no hay nota. En JSON no hace falta neutralizar nada: ahí nada se ejecuta. */
    val note: String?,
)
