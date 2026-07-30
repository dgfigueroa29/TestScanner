package com.testscanner.core.model

/** De dónde salen los frames o el contenido que analiza un motor. */
enum class ScanSource(val displayName: String) {
    LiveCamera("Cámara en vivo"),
    StaticImage("Imagen"),
    ManualInput("Entrada manual"),
}

/**
 * Lo que el usuario pide escanear. Es la entrada de la política de selección: el selector compara
 * este objeto contra las capacidades declaradas de cada motor y descarta los que no encajan.
 *
 * @param continuous el motor no se detiene tras la primera detección.
 * @param allowMultiple se aceptan varios códigos dentro del mismo frame.
 * @param requireTorchControl descarta motores que no permiten controlar la linterna.
 */
data class ScanRequest(
    val formats: Set<BarcodeFormat> = BarcodeFormat.all,
    val source: ScanSource = ScanSource.LiveCamera,
    val continuous: Boolean = false,
    val allowMultiple: Boolean = false,
    val requireTorchControl: Boolean = false,
    val timeoutMillis: Long? = null,
) {
    init {
        require(formats.isNotEmpty()) { "Un ScanRequest debe pedir al menos un formato" }
        require(timeoutMillis == null || timeoutMillis > 0) { "timeoutMillis debe ser positivo" }
    }
}

/**
 * Imagen ya capturada, para decodificación estática (RF-07).
 *
 * Se transporta codificada (JPEG/PNG) y no como bitmap de plataforma para que el tipo viva en
 * `commonMain`. Cada motor la convierte a su representación nativa dentro de su módulo.
 */
class ScanImage(
    val encoded: ByteArray,
    val mimeType: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
) {
    override fun toString(): String =
        "ScanImage(mimeType=$mimeType, bytes=${encoded.size}, size=${widthPx}x$heightPx)"
}
