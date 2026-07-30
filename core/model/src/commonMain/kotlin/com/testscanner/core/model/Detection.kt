package com.testscanner.core.model

/**
 * El evento de haber reconocido un [Barcode] con un motor concreto en un instante concreto.
 *
 * La separación entre [Barcode] y [Detection] es lo que habilita el objetivo del producto:
 * comparar motores. El mismo código físico produce detecciones distintas — distinto motor,
 * distinta latencia — y esas diferencias son el dato interesante.
 */
data class Detection(
    val id: String,
    val barcode: Barcode,
    val engineId: ScannerEngineId,
    val detectedAtMillis: Long,
    val latencyMillis: Long? = null,
    val source: ScanSource = ScanSource.LiveCamera,
) {
    companion object {
        /**
         * Identificador estable: dos lecturas del mismo código, con el mismo motor, en el mismo
         * milisegundo, son la misma detección. Evita duplicados en modo continuo sin necesidad de
         * un generador de UUID multiplataforma.
         */
        fun idOf(engineId: ScannerEngineId, rawValue: String, detectedAtMillis: Long): String =
            "${engineId.id}:$detectedAtMillis:${rawValue.hashCode()}"

        fun of(
            barcode: Barcode,
            engineId: ScannerEngineId,
            detectedAtMillis: Long,
            latencyMillis: Long? = null,
            source: ScanSource = ScanSource.LiveCamera,
        ): Detection = Detection(
            id = idOf(engineId, barcode.rawValue, detectedAtMillis),
            barcode = barcode,
            engineId = engineId,
            detectedAtMillis = detectedAtMillis,
            latencyMillis = latencyMillis,
            source = source,
        )
    }
}
