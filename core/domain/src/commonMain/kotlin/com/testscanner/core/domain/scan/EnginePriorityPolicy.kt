package com.testscanner.core.domain.scan

import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform

/**
 * Orden por defecto de los motores en cada plataforma, cuando el usuario no ha fijado ninguno.
 *
 * Es una tabla de datos y no una cascada de `if`: el criterio de ordenación es una decisión de
 * producto documentada en `docs/ENGINES.md`, no lógica dispersa.
 *
 * [ScannerEngineId.ManualInput] cierra siempre la cadena: garantiza que nunca existe el estado
 * "no se puede escanear nada".
 */
object EnginePriorityPolicy {

    private val byPlatform: Map<ScannerPlatform, List<ScannerEngineId>> = mapOf(
        ScannerPlatform.Android to listOf(
            ScannerEngineId.GmsCodeScanner,
            ScannerEngineId.MlKitCameraX,
            ScannerEngineId.ZXingCpp,
            ScannerEngineId.MlKitOcr,
            ScannerEngineId.ManualInput,
        ),
        ScannerPlatform.Ios to listOf(
            ScannerEngineId.VisionIos,
            ScannerEngineId.ZXingCpp,
            ScannerEngineId.MlKitOcr,
            ScannerEngineId.ManualInput,
        ),
        // ZXing en Java, no el port a C++: zxing-cpp no publica artefacto JVM (ADR-0008). Solo
        // decodifica archivos, así que ante una petición de cámara en vivo el selector lo descarta
        // por capacidades y la cadena cae sola a la entrada manual.
        ScannerPlatform.Desktop to listOf(
            ScannerEngineId.ZXingJava,
            ScannerEngineId.ManualInput,
        ),
        // Tampoco hay artefacto wasmJs de zxing-cpp; en Web el respaldo del BarcodeDetector es la
        // entrada manual, no otro decodificador.
        ScannerPlatform.Web to listOf(
            ScannerEngineId.BrowserDetector,
            ScannerEngineId.ManualInput,
        ),
    )

    fun order(platform: ScannerPlatform): List<ScannerEngineId> = byPlatform[platform].orEmpty()

    /**
     * Posición de un motor en la plataforma. Los motores fuera de la tabla van al final, nunca
     * antes que uno priorizado explícitamente.
     */
    fun rank(platform: ScannerPlatform, id: ScannerEngineId): Int =
        order(platform).indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
