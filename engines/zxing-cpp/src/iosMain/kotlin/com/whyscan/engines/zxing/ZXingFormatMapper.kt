package com.whyscan.engines.zxing

import com.whyscan.core.model.BarcodeFormat
import zxingcpp.BarcodeFormat as ZXingFormat

/**
 * Traducción entre los formatos de zxing-cpp y [BarcodeFormat] en iOS.
 *
 * Es el gemelo del mapa de `androidMain`. Están duplicados porque **las dos publicaciones de
 * zxing-cpp no comparten API**: aquí `BarcodeFormat` es un enum de primer nivel con nombres en
 * *CamelCase*, y en Android es un enum anidado en `BarcodeReader` con nombres en mayúsculas. El
 * núcleo C++ que decodifica sí es el mismo, y eso es lo que hace justa la comparación (ADR-0008).
 */
internal object ZXingFormatMapper {

    private val toDomain: Map<ZXingFormat, BarcodeFormat> = mapOf(
        ZXingFormat.EAN13 to BarcodeFormat.Ean13,
        ZXingFormat.EAN8 to BarcodeFormat.Ean8,
        ZXingFormat.UPCA to BarcodeFormat.UpcA,
        ZXingFormat.UPCE to BarcodeFormat.UpcE,
        ZXingFormat.Code39 to BarcodeFormat.Code39,
        ZXingFormat.Code93 to BarcodeFormat.Code93,
        ZXingFormat.Code128 to BarcodeFormat.Code128,
        ZXingFormat.Codabar to BarcodeFormat.Codabar,
        ZXingFormat.ITF to BarcodeFormat.Itf,
        ZXingFormat.QRCode to BarcodeFormat.QrCode,
        ZXingFormat.DataMatrix to BarcodeFormat.DataMatrix,
        ZXingFormat.Aztec to BarcodeFormat.Aztec,
        ZXingFormat.PDF417 to BarcodeFormat.Pdf417,
        ZXingFormat.DataBar to BarcodeFormat.DataBar,
        ZXingFormat.MaxiCode to BarcodeFormat.MaxiCode,
        ZXingFormat.MicroQRCode to BarcodeFormat.MicroQrCode,
        ZXingFormat.RMQRCode to BarcodeFormat.RectangularMicroQrCode,
    )

    private val toZXing: Map<BarcodeFormat, ZXingFormat> =
        toDomain.entries.associate { (zxing, domain) -> domain to zxing }

    fun fromZXing(format: ZXingFormat): BarcodeFormat =
        toDomain[format] ?: BarcodeFormat.Unknown(format.name)

    /** Vacío significa "todos" para la librería: ver la nota del mapa de Android. */
    fun toZXingFormats(formats: Set<BarcodeFormat>): Set<ZXingFormat> {
        val mapped = formats.mapNotNull { toZXing[it] }.toSet()
        return if (mapped.size == formats.size) mapped else emptySet()
    }
}
