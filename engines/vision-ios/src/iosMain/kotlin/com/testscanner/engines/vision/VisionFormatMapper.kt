package com.testscanner.engines.vision

import com.testscanner.core.model.BarcodeFormat
import platform.AVFoundation.AVMetadataObjectTypeAztecCode
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeCode93Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypePDF417Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode

/**
 * Traducción entre los tipos de metadato de AVFoundation y [BarcodeFormat].
 *
 * Dos particularidades de iOS que el mapa deja explícitas:
 * - **No existe un tipo UPC-A.** AVFoundation devuelve los UPC-A como EAN-13 con un cero delante,
 *   que es lo que son. El dominio los verá como `Ean13`; convertirlos a `UpcA` sería inventar
 *   información que el sistema no da.
 * - **ITF-14 e Interleaved 2 of 5 son tipos distintos** y ambos corresponden a `Itf`.
 */
internal object VisionFormatMapper {

    /**
     * Las constantes `AVMetadataObjectType*` llegan de cinterop como `String?`: el binding no puede
     * saber que Apple no las declara nulas nunca. Se descartan las nulas en lugar de forzarlas con
     * `!!` — si alguna faltara, ese formato simplemente no se ofrece, en vez de tumbar el motor al
     * cargarlo.
     */
    private val toDomain: Map<String, BarcodeFormat> = mapOf(
        AVMetadataObjectTypeEAN13Code to BarcodeFormat.Ean13,
        AVMetadataObjectTypeEAN8Code to BarcodeFormat.Ean8,
        AVMetadataObjectTypeUPCECode to BarcodeFormat.UpcE,
        AVMetadataObjectTypeCode39Code to BarcodeFormat.Code39,
        AVMetadataObjectTypeCode93Code to BarcodeFormat.Code93,
        AVMetadataObjectTypeCode128Code to BarcodeFormat.Code128,
        AVMetadataObjectTypeITF14Code to BarcodeFormat.Itf,
        AVMetadataObjectTypeInterleaved2of5Code to BarcodeFormat.Itf,
        AVMetadataObjectTypeQRCode to BarcodeFormat.QrCode,
        AVMetadataObjectTypeDataMatrixCode to BarcodeFormat.DataMatrix,
        AVMetadataObjectTypeAztecCode to BarcodeFormat.Aztec,
        AVMetadataObjectTypePDF417Code to BarcodeFormat.Pdf417,
    ).entries.mapNotNull { (type, format) -> type?.let { it to format } }.toMap()

    /** Todos los tipos que sabemos traducir, para configurar `metadataObjectTypes`. */
    val allSupportedTypes: List<String> = toDomain.keys.toList()

    fun fromVision(type: String): BarcodeFormat =
        toDomain[type] ?: BarcodeFormat.Unknown(type)

    /**
     * Tipos a pedirle a AVFoundation para una petición.
     *
     * Si no queda ninguno se devuelven todos y el filtrado fino lo hace el dominio, igual que en
     * los motores de Android: así el comportamiento observable es el mismo en los ocho.
     */
    fun toVisionTypes(formats: Set<BarcodeFormat>): List<String> {
        val requested = toDomain.filterValues { it in formats }.keys.toList()
        return requested.ifEmpty { allSupportedTypes }
    }
}
