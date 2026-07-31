package com.testscanner.engines.gms

import com.testscanner.core.model.BarcodeFormat
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/**
 * Traducción entre las constantes de ML Kit y [BarcodeFormat].
 *
 * Vive dentro del módulo del motor a propósito: el dominio nunca debe ver una constante de SDK.
 * Es el mismo mapper que reutiliza `:engines:mlkit-camerax`, porque ambos motores comparten el
 * modelo de códigos de ML Kit aunque los flujos de captura sean distintos.
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

    /** Un formato que ML Kit reporta y no modelamos se conserva por nombre, no se descarta. */
    fun fromMlKit(format: Int): BarcodeFormat =
        toDomain[format] ?: BarcodeFormat.Unknown("MLKIT_$format")

    /**
     * Máscara de formatos para configurar el detector.
     *
     * Devuelve `null` cuando la petición incluye formatos que ML Kit no conoce: en ese caso se
     * deja el detector en modo "todos" y el filtrado fino lo hace `FormatFilteringScannerEngine`
     * en el dominio, que es quien garantiza el mismo comportamiento en los siete motores.
     */
    fun toMlKitFormats(formats: Set<BarcodeFormat>): IntArray? {
        val mapped = formats.mapNotNull { toMlKit[it] }
        return if (mapped.isEmpty()) null else mapped.toIntArray()
    }
}
