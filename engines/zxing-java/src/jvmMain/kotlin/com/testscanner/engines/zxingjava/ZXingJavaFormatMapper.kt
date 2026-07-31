package com.testscanner.engines.zxingjava

import com.testscanner.core.model.BarcodeFormat
import com.google.zxing.BarcodeFormat as ZXingFormat

/**
 * Traduce entre las constantes de ZXing y las del dominio.
 *
 * Vive dentro del módulo del motor a propósito: el dominio nunca ve una constante de ZXing, igual
 * que no ve una de ML Kit ni de Vision. Es lo que permite comparar motores sin que sus SDK se
 * filtren al modelo.
 */
internal object ZXingJavaFormatMapper {

    private val toDomain: Map<ZXingFormat, BarcodeFormat> = mapOf(
        ZXingFormat.AZTEC to BarcodeFormat.Aztec,
        ZXingFormat.CODABAR to BarcodeFormat.Codabar,
        ZXingFormat.CODE_39 to BarcodeFormat.Code39,
        ZXingFormat.CODE_93 to BarcodeFormat.Code93,
        ZXingFormat.CODE_128 to BarcodeFormat.Code128,
        ZXingFormat.DATA_MATRIX to BarcodeFormat.DataMatrix,
        ZXingFormat.EAN_8 to BarcodeFormat.Ean8,
        ZXingFormat.EAN_13 to BarcodeFormat.Ean13,
        ZXingFormat.ITF to BarcodeFormat.Itf,
        ZXingFormat.MAXICODE to BarcodeFormat.MaxiCode,
        ZXingFormat.PDF_417 to BarcodeFormat.Pdf417,
        ZXingFormat.QR_CODE to BarcodeFormat.QrCode,
        // DataBar es la marca actual de lo que ZXing sigue llamando RSS. Las dos variantes caen en
        // el mismo formato del dominio porque son la misma simbología con distinta longitud.
        ZXingFormat.RSS_14 to BarcodeFormat.DataBar,
        ZXingFormat.RSS_EXPANDED to BarcodeFormat.DataBar,
        ZXingFormat.UPC_A to BarcodeFormat.UpcA,
        ZXingFormat.UPC_E to BarcodeFormat.UpcE,
    )

    private val toZXing: Map<BarcodeFormat, List<ZXingFormat>> =
        toDomain.entries.groupBy({ it.value }, { it.key })

    /**
     * Convierte un formato de ZXing.
     *
     * `UPC_EAN_EXTENSION` —el suplemento de 2 o 5 dígitos de las revistas— no tiene equivalente en
     * el dominio y se conserva como [BarcodeFormat.Unknown] con su nombre original, en lugar de
     * forzarlo a un formato que no es.
     */
    fun toDomain(format: ZXingFormat): BarcodeFormat =
        toDomain[format] ?: BarcodeFormat.Unknown(format.name)

    /**
     * Formatos de ZXing correspondientes a los pedidos, o `null` si no hay ninguno.
     *
     * `null` significa "no restrinjas": ZXing prueba entonces todas sus simbologías y el filtrado
     * fino lo aplica el dominio, que es quien garantiza el mismo comportamiento en los ocho motores.
     */
    fun toZXingFormats(formats: Set<BarcodeFormat>): List<ZXingFormat>? =
        formats.flatMap { toZXing[it].orEmpty() }.distinct().ifEmpty { null }
}
