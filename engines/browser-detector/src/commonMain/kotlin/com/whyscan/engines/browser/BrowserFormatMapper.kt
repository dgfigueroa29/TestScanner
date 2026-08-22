package com.whyscan.engines.browser

import com.whyscan.core.model.BarcodeFormat

/**
 * Traducción entre los nombres de formato de la `BarcodeDetector` API y [BarcodeFormat].
 *
 * Vive en `commonMain` aunque el módulo solo tenga target wasmJs: son cadenas y tablas, sin nada de
 * navegador, así que puede ejercitarse con tests normales. El resto del motor —que sí es interop
 * puro— no puede, y esa es exactamente la razón de separar las dos cosas.
 *
 * Los nombres salen de la especificación de la API (todos en `snake_case` minúscula), no de una
 * suposición: un navegador que devuelva otra cosa produce un [BarcodeFormat.Unknown] con el nombre
 * original, que es lo que permite verlo en el historial en vez de perderlo.
 */
object BrowserFormatMapper {

    private val toDomain: Map<String, BarcodeFormat> = mapOf(
        "aztec" to BarcodeFormat.Aztec,
        "codabar" to BarcodeFormat.Codabar,
        "code_39" to BarcodeFormat.Code39,
        "code_93" to BarcodeFormat.Code93,
        "code_128" to BarcodeFormat.Code128,
        "data_matrix" to BarcodeFormat.DataMatrix,
        "ean_8" to BarcodeFormat.Ean8,
        "ean_13" to BarcodeFormat.Ean13,
        "itf" to BarcodeFormat.Itf,
        "pdf417" to BarcodeFormat.Pdf417,
        "qr_code" to BarcodeFormat.QrCode,
        "upc_a" to BarcodeFormat.UpcA,
        "upc_e" to BarcodeFormat.UpcE,
    )

    private val toBrowser: Map<BarcodeFormat, String> =
        toDomain.entries.associate { (name, format) -> format to name }

    fun fromBrowser(name: String): BarcodeFormat =
        toDomain[name] ?: BarcodeFormat.Unknown(name.uppercase())

    /**
     * Nombres que entiende el navegador, separados por comas, o `null` si la petición no se puede
     * expresar como un filtro.
     *
     * Devolver `null` significa "detecta todo": es lo correcto cuando el usuario pide formatos que
     * esta API no conoce, porque el filtrado fino lo aplica igualmente el dominio
     * (`FormatFilteringScannerEngine`) y así el comportamiento observable no depende del motor.
     */
    fun toBrowserFilter(formats: Set<BarcodeFormat>): String? {
        val names = formats.mapNotNull { toBrowser[it] }
        return names.takeIf { it.isNotEmpty() && it.size == formats.size }
            ?.sorted()
            ?.joinToString(",")
    }
}
