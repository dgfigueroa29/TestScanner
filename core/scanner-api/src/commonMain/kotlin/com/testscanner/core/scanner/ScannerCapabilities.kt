package com.testscanner.core.scanner

import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource

/**
 * Lo que un motor sabe y no sabe hacer, **declarado como datos**.
 *
 * Es la pieza que permite que la UI y la política de selección sean genéricas: la pantalla de
 * catálogo se renderiza recorriendo estos campos y el selector puntúa motores comparándolos contra
 * un [ScanRequest]. Añadir un motor no añade ni una rama condicional en la UI ni en el dominio.
 */
data class ScannerCapabilities(
    val supportedFormats: Set<BarcodeFormat>,
    val sources: Set<ScanSource>,
    val supportsMultipleCodes: Boolean = false,
    val supportsContinuousScan: Boolean = false,
    /** El motor abre su propia pantalla (caso del Google Code Scanner): no admite overlay propio. */
    val providesOwnUi: Boolean = false,
    val supportsTorch: Boolean = false,
    val supportsZoom: Boolean = false,
    val reportsCornerPoints: Boolean = false,
    val reportsConfidence: Boolean = false,
    val requiresCameraPermission: Boolean = true,
    val requiresNetwork: Boolean = false,
    /** El motor descarga su modelo la primera vez que se usa (ML Kit *unbundled*). */
    val requiresRuntimeDownload: Boolean = false,
) {
    /** Formatos que el motor cubre de los que pide la petición. */
    fun coveredFormats(request: ScanRequest): Set<BarcodeFormat> =
        request.formats intersect supportedFormats

    /** Formatos pedidos que este motor **no** puede leer. La UI los muestra como advertencia. */
    fun uncoveredFormats(request: ScanRequest): Set<BarcodeFormat> =
        request.formats - supportedFormats

    /**
     * Si el motor puede atender la petición en absoluto. No mide calidad — eso lo hace la
     * puntuación del selector — sino viabilidad: fuente compatible, algún formato cubierto y las
     * capacidades explícitamente exigidas.
     */
    fun satisfies(request: ScanRequest): Boolean = when {
        request.source !in sources -> false
        coveredFormats(request).isEmpty() -> false
        request.continuous && !supportsContinuousScan -> false
        request.allowMultiple && !supportsMultipleCodes -> false
        request.requireTorchControl && !supportsTorch -> false
        else -> true
    }
}
