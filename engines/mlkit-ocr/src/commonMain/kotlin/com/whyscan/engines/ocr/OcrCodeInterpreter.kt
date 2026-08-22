package com.whyscan.engines.ocr

import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.GtinChecksum
import com.whyscan.core.model.Point

/** Una línea reconocida por el OCR, con lo que la plataforma sepa decir de ella. */
data class OcrLine(
    val text: String,
    val confidence: Float? = null,
    /** Esquinas normalizadas a `[0, 1]`; `null` si la plataforma no las reporta. */
    val cornerPoints: List<Point>? = null,
)

/**
 * Convierte texto reconocido en códigos de producto.
 *
 * Este motor **no decodifica la simbología**: lee el número impreso debajo del código de barras.
 * Sirve para lo que ningún decodificador puede hacer — recuperar un EAN cuyo patrón de barras está
 * rayado, arrugado o mal impreso, pero cuyos dígitos siguen siendo legibles.
 *
 * ### Por qué el dígito de control es el corazón de este motor
 * El OCR devuelve texto plausible, no texto correcto. Sin una verificación, cualquier número de
 * trece cifras que aparezca en el envase —un teléfono de atención al cliente, un lote— se reportaría
 * como un EAN-13. El checksum GTIN es lo que convierte una conjetura en una lectura: solo se emite
 * un código si el dígito de control cuadra.
 *
 * Por eso este motor está en la Fase 4 y no antes: es el último recurso de la cadena de fallback, y
 * un último recurso que miente es peor que no tener ninguno.
 */
object OcrCodeInterpreter {

    private const val EAN_8_LENGTH = 8
    private const val UPC_A_LENGTH = 12
    private const val EAN_13_LENGTH = 13
    private const val GTIN_14_LENGTH = 14

    private val GTIN_LENGTHS = setOf(EAN_8_LENGTH, UPC_A_LENGTH, EAN_13_LENGTH, GTIN_14_LENGTH)

    /**
     * Separadores que la impresión mete entre grupos de dígitos: bajo un EAN-13 se lee
     * `7 501234 567890`, no `7501234567890`.
     */
    private val SEPARATORS = setOf(' ', '-', '.', ' ')

    /**
     * Confusiones de OCR que se intentan **solo si la lectura literal no da ningún código válido**.
     *
     * La lista es corta a propósito. Cada sustitución multiplica las combinaciones a probar, y como
     * el filtro final es un checksum de módulo 10, una de cada diez conjeturas erróneas lo pasa por
     * azar. Ampliar esta tabla no haría al motor más capaz: lo haría más mentiroso.
     */
    private val LOOKALIKES = mapOf(
        'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'l' to '1', '|' to '1',
        'S' to '5', 'B' to '8',
    )

    /** Penalización aplicada cuando el código solo aparece tras sustituir letras por dígitos. */
    private const val LOOKALIKE_CONFIDENCE_FACTOR = 0.7f

    /** Confianza asumida cuando la plataforma no reporta ninguna. */
    private const val ASSUMED_CONFIDENCE = 1f

    /**
     * Códigos encontrados en el texto, sin repetir. Puede devolver varios: una etiqueta de almacén
     * lleva a menudo el EAN del producto y el ITF-14 de la caja.
     */
    fun interpret(lines: List<OcrLine>): List<Barcode> {
        val literal = lines.flatMap { line -> candidatesIn(line, substituted = false) }
        // Las sustituciones son el plan B, no un complemento: si la lectura literal ya dio algo,
        // mezclar ambas fuentes solo añadiría ruido con peor confianza.
        val candidates = literal.ifEmpty {
            lines.flatMap { line -> candidatesIn(line, substituted = true) }
        }
        return candidates.distinctBy { it.rawValue }
    }

    private fun candidatesIn(line: OcrLine, substituted: Boolean): List<Barcode> {
        val text = if (substituted) line.text.map { LOOKALIKES[it] ?: it }.joinToString("") else line.text

        return digitRuns(text)
            .filter { it.length in GTIN_LENGTHS && GtinChecksum.isValid(it) }
            .map { digits ->
                Barcode(
                    rawValue = digits,
                    format = formatFor(digits.length),
                    cornerPoints = line.cornerPoints,
                    confidence = confidenceFor(line, substituted),
                )
            }
    }

    /**
     * Secuencias de dígitos del texto, tolerando los separadores de la impresión pero no otros
     * caracteres: `Lote: 12 34` da `1234`, mientras que `12A34` da `12` y `34` por separado.
     */
    private fun digitRuns(text: String): List<String> {
        val runs = mutableListOf<String>()
        val current = StringBuilder()

        for (character in text) {
            when {
                character.isDigit() -> current.append(character)
                character in SEPARATORS -> Unit
                else -> {
                    if (current.isNotEmpty()) runs += current.toString()
                    current.clear()
                }
            }
        }
        if (current.isNotEmpty()) runs += current.toString()

        return runs
    }

    private fun confidenceFor(line: OcrLine, substituted: Boolean): Float {
        val base = line.confidence ?: ASSUMED_CONFIDENCE
        return if (substituted) base * LOOKALIKE_CONFIDENCE_FACTOR else base
    }

    /**
     * La longitud determina la simbología porque el checksum ya se validó. No se reutiliza la
     * inferencia del motor manual: aquella cae a QR Code ante la duda, que es lo correcto para algo
     * tecleado y absurdo para un número leído bajo unas barras.
     */
    private fun formatFor(length: Int): BarcodeFormat = when (length) {
        EAN_8_LENGTH -> BarcodeFormat.Ean8
        UPC_A_LENGTH -> BarcodeFormat.UpcA
        EAN_13_LENGTH -> BarcodeFormat.Ean13
        else -> BarcodeFormat.Itf
    }
}
