package com.whyscan.engines.zxing

import com.whyscan.core.model.BarcodeFormat
import zxingcpp.BarcodeReader

/**
 * Traducción entre los formatos de zxing-cpp y [BarcodeFormat] en Android.
 *
 * Hay un mapa gemelo en `iosMain` porque **las dos publicaciones de zxing-cpp no comparten API**:
 * la de Android anida un `enum Format` dentro de `BarcodeReader` y la nativa expone un
 * `BarcodeFormat` de primer nivel. El decodificador C++ sí es el mismo, que es lo que importa para
 * la comparación (ADR-0008); el binding no.
 *
 * zxing-cpp lee simbologías que ni ML Kit ni Vision conocen —DataBar, DX Film Edge, rMQR—, así que
 * este mapa es el más largo del proyecto: es justo la ventaja que el motor aporta al catálogo.
 */
internal object ZXingFormatMapper {

    private val toDomain: Map<BarcodeReader.Format, BarcodeFormat> = mapOf(
        BarcodeReader.Format.EAN_13 to BarcodeFormat.Ean13,
        BarcodeReader.Format.EAN_8 to BarcodeFormat.Ean8,
        BarcodeReader.Format.UPC_A to BarcodeFormat.UpcA,
        BarcodeReader.Format.UPC_E to BarcodeFormat.UpcE,
        BarcodeReader.Format.CODE_39 to BarcodeFormat.Code39,
        BarcodeReader.Format.CODE_93 to BarcodeFormat.Code93,
        BarcodeReader.Format.CODE_128 to BarcodeFormat.Code128,
        BarcodeReader.Format.CODABAR to BarcodeFormat.Codabar,
        BarcodeReader.Format.ITF to BarcodeFormat.Itf,
        BarcodeReader.Format.QR_CODE to BarcodeFormat.QrCode,
        BarcodeReader.Format.DATA_MATRIX to BarcodeFormat.DataMatrix,
        BarcodeReader.Format.AZTEC to BarcodeFormat.Aztec,
        BarcodeReader.Format.PDF_417 to BarcodeFormat.Pdf417,
        BarcodeReader.Format.DATA_BAR to BarcodeFormat.DataBar,
        BarcodeReader.Format.MAXI_CODE to BarcodeFormat.MaxiCode,
        BarcodeReader.Format.MICRO_QR_CODE to BarcodeFormat.MicroQrCode,
        BarcodeReader.Format.RMQR_CODE to BarcodeFormat.RectangularMicroQrCode,
    )

    private val toZXing: Map<BarcodeFormat, BarcodeReader.Format> =
        toDomain.entries.associate { (zxing, domain) -> domain to zxing }

    fun fromZXing(format: BarcodeReader.Format): BarcodeFormat =
        toDomain[format] ?: BarcodeFormat.Unknown(format.name)

    /**
     * Conjunto que entiende zxing-cpp, o vacío si la petición no se puede expresar como filtro.
     *
     * Un `Options.formats` vacío significa "todos" para la librería, y es lo correcto cuando se
     * piden formatos que este mapa no cubre: el filtrado fino lo aplica el dominio de todas formas
     * (`FormatFilteringScannerEngine`), así que restringir aquí de más solo perdería lecturas.
     */
    fun toZXingFormats(formats: Set<BarcodeFormat>): Set<BarcodeReader.Format> {
        val mapped = formats.mapNotNull { toZXing[it] }.toSet()
        return if (mapped.size == formats.size) mapped else emptySet()
    }
}
