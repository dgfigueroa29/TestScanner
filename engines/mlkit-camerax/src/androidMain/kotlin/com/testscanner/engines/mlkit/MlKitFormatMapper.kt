package com.testscanner.engines.mlkit

import com.testscanner.core.model.BarcodeFormat
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/**
 * Traducción entre las constantes de ML Kit y [BarcodeFormat].
 *
 * Está duplicado respecto a `:engines:gms-code-scanner` a sabiendas: extraerlo a un módulo común
 * acoplaría dos motores que deben poder eliminarse por separado, que es justamente lo que el SPI
 * intenta preservar. Son 15 líneas de tabla; el acoplamiento costaría más.
 */
internal object MlKitFormatMapper {

    private val toDomain: Map<Int, BarcodeFormat> = mapOf(
        MlKitBarcode.FORMAT_EAN_13 to BarcodeFormat.Ean13,
        MlKitBarcode.FORMAT_EAN_8 to BarcodeFormat.Ean8,
        MlKitBarcode.FORMAT_UPC_A to BarcodeFormat.UpcA,
        MlKitBarcode.FORMAT_UPC_E to BarcodeFormat.UpcE,
        MlKitBarcode.FORMAT_CODE_39 to BarcodeFormat.Code39,
        MlKitBarcode.FORMAT_CODE_93 to BarcodeFormat.Code93,
        MlKitBarcode.FORMAT_CODE_128 to BarcodeFormat.Code128,
        MlKitBarcode.FORMAT_CODABAR to BarcodeFormat.Codabar,
        MlKitBarcode.FORMAT_ITF to BarcodeFormat.Itf,
        MlKitBarcode.FORMAT_QR_CODE to BarcodeFormat.QrCode,
        MlKitBarcode.FORMAT_DATA_MATRIX to BarcodeFormat.DataMatrix,
        MlKitBarcode.FORMAT_AZTEC to BarcodeFormat.Aztec,
        MlKitBarcode.FORMAT_PDF417 to BarcodeFormat.Pdf417,
    )

    private val toMlKit: Map<BarcodeFormat, Int> =
        toDomain.entries.associate { (mlKit, domain) -> domain to mlKit }

    fun fromMlKit(format: Int): BarcodeFormat =
        toDomain[format] ?: BarcodeFormat.Unknown("MLKIT_$format")

    fun toMlKitFormats(formats: Set<BarcodeFormat>): IntArray? {
        val mapped = formats.mapNotNull { toMlKit[it] }
        return if (mapped.isEmpty()) null else mapped.toIntArray()
    }
}
