package com.whyscan.core.model

/**
 * Familia de simbologías. Se usa para agrupar en la UI y para razonar sobre qué motores tienen
 * sentido ante un [ScanRequest] concreto.
 */
enum class BarcodeFamily(val displayName: String) {
    ProductLinear("1D producto"),
    IndustrialLinear("1D industrial"),
    Matrix2D("2D matricial"),
    PostalOrSpecial("Postal / especial"),
    Extended("Extendido"),
    Unknown("Desconocido"),
}

/**
 * Simbología de un código.
 *
 * Es una jerarquía sellada y no un `enum` porque necesitamos representar formatos que un motor
 * reporta y el dominio todavía no modela ([Unknown]). Un `enum` obligaría a perder esa información
 * o a inventar una constante genérica sin el nombre original.
 *
 * Cada motor traduce sus constantes de SDK a estos valores dentro de su propio módulo: el dominio
 * nunca ve una constante de ML Kit, Vision o ZXing.
 */
sealed class BarcodeFormat(
    val id: String,
    val displayName: String,
    val family: BarcodeFamily,
) {
    // --- 1D producto ---
    data object Ean13 : BarcodeFormat("EAN_13", "EAN-13", BarcodeFamily.ProductLinear)
    data object Ean8 : BarcodeFormat("EAN_8", "EAN-8", BarcodeFamily.ProductLinear)
    data object UpcA : BarcodeFormat("UPC_A", "UPC-A", BarcodeFamily.ProductLinear)
    data object UpcE : BarcodeFormat("UPC_E", "UPC-E", BarcodeFamily.ProductLinear)

    // --- 1D industrial ---
    data object Code39 : BarcodeFormat("CODE_39", "Code 39", BarcodeFamily.IndustrialLinear)
    data object Code93 : BarcodeFormat("CODE_93", "Code 93", BarcodeFamily.IndustrialLinear)
    data object Code128 : BarcodeFormat("CODE_128", "Code 128", BarcodeFamily.IndustrialLinear)
    data object Codabar : BarcodeFormat("CODABAR", "Codabar", BarcodeFamily.IndustrialLinear)
    data object Itf : BarcodeFormat("ITF", "ITF", BarcodeFamily.IndustrialLinear)

    // --- 2D matricial ---
    data object QrCode : BarcodeFormat("QR_CODE", "QR Code", BarcodeFamily.Matrix2D)
    data object DataMatrix : BarcodeFormat("DATA_MATRIX", "Data Matrix", BarcodeFamily.Matrix2D)
    data object Aztec : BarcodeFormat("AZTEC", "Aztec", BarcodeFamily.Matrix2D)
    data object Pdf417 : BarcodeFormat("PDF_417", "PDF417", BarcodeFamily.Matrix2D)

    // --- Postal / especial ---
    data object DataBar : BarcodeFormat("DATA_BAR", "DataBar / RSS", BarcodeFamily.PostalOrSpecial)
    data object MaxiCode : BarcodeFormat("MAXICODE", "MaxiCode", BarcodeFamily.PostalOrSpecial)

    // --- Extendido ---
    data object MicroQrCode : BarcodeFormat("MICRO_QR", "Micro QR", BarcodeFamily.Extended)
    data object RectangularMicroQrCode : BarcodeFormat("RMQR", "rMQR", BarcodeFamily.Extended)

    /** Formato reportado por un motor que el dominio aún no modela. Conserva el nombre original. */
    data class Unknown(val rawName: String) :
        BarcodeFormat(rawName, rawName, BarcodeFamily.Unknown)

    override fun toString(): String = id

    companion object {
        /**
         * Todas las simbologías modeladas, en orden de presentación.
         *
         * Es `by lazy` y no un `val` directo por una razón que no es cosmética: los `data object`
         * de arriba son clases anidadas cuya inicialización dispara la del propio [BarcodeFormat],
         * y esta la del companion. Con un `val` inicializado ansiosamente, el conjunto se
         * construiría **mientras** las instancias aún son `null`, y quedaría lleno de nulos. Con
         * `lazy` la evaluación ocurre en el primer acceso, cuando ya existen todas.
         */
        val known: Set<BarcodeFormat> by lazy {
            setOf(
                Ean13, Ean8, UpcA, UpcE,
                Code39, Code93, Code128, Codabar, Itf,
                QrCode, DataMatrix, Aztec, Pdf417,
                DataBar, MaxiCode,
                MicroQrCode, RectangularMicroQrCode,
            )
        }

        /** Conjunto por defecto de un [ScanRequest]: todo lo que sabemos nombrar. */
        val all: Set<BarcodeFormat> get() = known

        /** Solo las simbologías bidimensionales (QR y familia). */
        val twoDimensional: Set<BarcodeFormat> by lazy {
            known.filterTo(mutableSetOf()) { it.family == BarcodeFamily.Matrix2D }
        }

        /** Solo las simbologías lineales (códigos de barras clásicos). */
        val oneDimensional: Set<BarcodeFormat> by lazy {
            known.filterTo(mutableSetOf()) {
                it.family == BarcodeFamily.ProductLinear ||
                    it.family == BarcodeFamily.IndustrialLinear
            }
        }

        fun fromId(id: String): BarcodeFormat = known.firstOrNull { it.id == id } ?: Unknown(id)
    }
}
