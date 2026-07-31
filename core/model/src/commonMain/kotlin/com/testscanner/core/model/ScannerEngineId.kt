package com.testscanner.core.model

/**
 * Identidad de cada alternativa de escaneo.
 *
 * Vive en `:core:model` y no en `:core:scanner-api` porque [Detection] necesita referenciarla y el
 * modelo no puede depender del SPI sin crear un ciclo.
 *
 * Añadir un motor implica añadir aquí una constante. Es el único punto compartido: el resto del
 * motor vive aislado en su propio módulo. Ver `docs/ENGINES.md`.
 */
enum class ScannerEngineId(val id: String) {
    /** Google Code Scanner (Play Services). Android, sin permiso de cámara, UI propia. */
    GmsCodeScanner("gms_code_scanner"),

    /** ML Kit Barcode + CameraX. Android, control total del preview. */
    MlKitCameraX("mlkit_camerax"),

    /** Vision / AVFoundation. iOS nativo. */
    VisionIos("vision_ios"),

    /** ZXing-cpp. Mismo decodificador en Android e iOS: baseline de comparación. */
    ZXingCpp("zxing_cpp"),

    /**
     * ZXing en Java puro. Escritorio.
     *
     * Motor distinto de [ZXingCpp] pese al parecido del nombre, y por eso tiene id propio: es el
     * proyecto original en Java, no el port a C++. Comparten linaje y no implementación, así que
     * atribuirle las lecturas del otro falsearía justo la comparación que la app existe para hacer.
     */
    ZXingJava("zxing_java"),

    /** `BarcodeDetector` del navegador. Web. */
    BrowserDetector("browser_detector"),

    /** ML Kit Text Recognition: lee el número impreso cuando el código está dañado. */
    MlKitOcr("mlkit_ocr"),

    /** Entrada manual por teclado. Siempre disponible; cierra la cadena de fallback. */
    ManualInput("manual_input"),

    ;

    companion object {
        fun fromId(id: String): ScannerEngineId? = entries.firstOrNull { it.id == id }
    }
}

/** Plataforma de ejecución. Determina qué motores están enlazados en el binario. */
enum class ScannerPlatform(val displayName: String) {
    Android("Android"),
    Ios("iOS"),
    Desktop("Desktop"),
    Web("Web"),
}
