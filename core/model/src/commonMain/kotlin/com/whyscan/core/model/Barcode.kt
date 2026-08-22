package com.whyscan.core.model

/**
 * Punto **normalizado a [0, 1]** sobre el frame analizado, no en píxeles.
 *
 * La normalización la hace cada motor, que es el único que conoce el tamaño real del frame; la UI
 * lo mapea después a coordenadas de pantalla sabiendo cómo se está escalando el preview. Si el
 * modelo llevara píxeles, el overlay dependería de la resolución de análisis de cada motor y dejaría
 * de ser comparable entre ellos.
 */
data class Point(val x: Float, val y: Float)

/**
 * Un código reconocido. Representa **lo que existe en el mundo**, no el acto de haberlo visto:
 * eso es [Detection].
 *
 * @param rawValue contenido decodificado tal cual lo devuelve el motor.
 * @param rawBytes bytes originales, cuando el motor los expone. Necesario para códigos binarios
 *   (por ejemplo QR con carga no UTF-8) donde [rawValue] pierde información.
 * @param cornerPoints esquinas normalizadas a [0, 1]; `null` si el motor no las reporta.
 * @param confidence confianza 0..1; `null` si el motor no la reporta. Solo el OCR la produce.
 */
class Barcode(
    val rawValue: String,
    val format: BarcodeFormat,
    val valueType: BarcodeValueType = BarcodeValueType.Text(rawValue),
    val rawBytes: ByteArray? = null,
    val cornerPoints: List<Point>? = null,
    val confidence: Float? = null,
) {
    // No es `data class` porque `ByteArray` rompería equals/hashCode: la igualdad generada sería
    // por identidad de array, de modo que dos lecturas idénticas del mismo código no serían
    // iguales. La deduplicación de detecciones depende de que esta comparación sea correcta.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Barcode) return false
        return rawValue == other.rawValue &&
            format == other.format &&
            valueType == other.valueType &&
            cornerPoints == other.cornerPoints &&
            confidence == other.confidence &&
            rawBytes.contentEqualsOrNull(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = rawValue.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + valueType.hashCode()
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + (cornerPoints?.hashCode() ?: 0)
        result = 31 * result + (confidence?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "Barcode(format=$format, rawValue='$rawValue')"

    fun copy(
        rawValue: String = this.rawValue,
        format: BarcodeFormat = this.format,
        valueType: BarcodeValueType = this.valueType,
        rawBytes: ByteArray? = this.rawBytes,
        cornerPoints: List<Point>? = this.cornerPoints,
        confidence: Float? = this.confidence,
    ): Barcode = Barcode(rawValue, format, valueType, rawBytes, cornerPoints, confidence)
}

private fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}
