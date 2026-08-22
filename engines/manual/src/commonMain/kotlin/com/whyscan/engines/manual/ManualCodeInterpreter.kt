package com.whyscan.engines.manual

import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.GtinChecksum

/**
 * Infiere la simbología de un valor tecleado.
 *
 * Inferir el formato es responsabilidad del **motor**: es él quien dice qué ha leído y en qué
 * simbología. Interpretar qué *significa* ese contenido es responsabilidad del dominio
 * (`SemanticParsingScannerEngine`), por eso aquí no se toca `valueType`.
 *
 * La inferencia es deliberadamente conservadora: ante la duda devuelve [BarcodeFormat.QrCode], que
 * es la simbología capaz de contener cualquier carga.
 */
object ManualCodeInterpreter {

    private const val EAN_8_LENGTH = 8
    private const val UPC_A_LENGTH = 12
    private const val EAN_13_LENGTH = 13
    private const val GTIN_14_LENGTH = 14

    fun interpret(rawValue: String): Barcode? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        return Barcode(rawValue = value, format = inferFormat(value))
    }

    fun inferFormat(value: String): BarcodeFormat {
        if (!value.all { it.isDigit() }) return BarcodeFormat.QrCode

        // Solo se afirma una simbología de producto si el dígito de control cuadra. Un número de
        // 13 dígitos con checksum inválido no es un EAN-13: es texto que parece uno.
        val checksumValid = GtinChecksum.isValid(value)
        return when {
            !checksumValid -> BarcodeFormat.QrCode
            value.length == EAN_8_LENGTH -> BarcodeFormat.Ean8
            value.length == UPC_A_LENGTH -> BarcodeFormat.UpcA
            value.length == EAN_13_LENGTH -> BarcodeFormat.Ean13
            value.length == GTIN_14_LENGTH -> BarcodeFormat.Itf
            else -> BarcodeFormat.QrCode
        }
    }
}
