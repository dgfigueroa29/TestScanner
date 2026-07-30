package com.testscanner.core.scanner.catalog

import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.ScannerCapabilities
import com.testscanner.core.scanner.ScannerEngineDescriptor

/**
 * Fichas de **todas** las alternativas de escaneo del producto, estén implementadas o no.
 *
 * Es el reflejo en código de `docs/ENGINES.md`; un test verifica que ambos no divergen.
 *
 * Que el catálogo esté completo desde la Fase 1 es deliberado: la UI muestra las siete
 * alternativas con sus capacidades reales y en qué fase llega cada una, y el registro no cambia de
 * forma cuando un motor se implementa — solo cambia lo que responde `availability()`.
 */
object ScannerEngineCatalog {

    private val LINEAR_AND_MATRIX: Set<BarcodeFormat> =
        BarcodeFormat.oneDimensional + BarcodeFormat.twoDimensional

    val gmsCodeScanner = ScannerEngineDescriptor(
        id = ScannerEngineId.GmsCodeScanner,
        displayName = "Google Code Scanner",
        vendor = "Google Play Services",
        description = "Escáner del sistema: abre su propia pantalla y devuelve el código leído. " +
            "El procesamiento ocurre fuera de la app, por lo que no necesita permiso de cámara.",
        platforms = setOf(ScannerPlatform.Android),
        plannedPhase = 2,
        requiresDependency = "com.google.android.gms:play-services-code-scanner",
        strength = "Cero permisos y cero UI que mantener",
        limitation = "UI no personalizable, sin linterna ni modo continuo",
        capabilities = ScannerCapabilities(
            supportedFormats = LINEAR_AND_MATRIX,
            sources = setOf(ScanSource.LiveCamera),
            providesOwnUi = true,
            requiresCameraPermission = false,
            requiresRuntimeDownload = true,
        ),
    )

    val mlKitCameraX = ScannerEngineDescriptor(
        id = ScannerEngineId.MlKitCameraX,
        displayName = "ML Kit + CameraX",
        vendor = "Google",
        description = "Análisis de frames de CameraX con el detector de códigos de ML Kit. " +
            "La app controla el preview, el overlay y la cámara.",
        platforms = setOf(ScannerPlatform.Android),
        plannedPhase = 2,
        requiresDependency = "com.google.mlkit:barcode-scanning + androidx.camera",
        strength = "Control total del preview, overlay, linterna y modo continuo",
        limitation = "Añade peso al binario; el modelo unbundled se descarga en el primer uso",
        capabilities = ScannerCapabilities(
            supportedFormats = LINEAR_AND_MATRIX,
            sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
            supportsMultipleCodes = true,
            supportsContinuousScan = true,
            supportsTorch = true,
            supportsZoom = true,
            reportsCornerPoints = true,
            requiresRuntimeDownload = true,
        ),
    )

    val visionIos = ScannerEngineDescriptor(
        id = ScannerEngineId.VisionIos,
        displayName = "Vision / AVFoundation",
        vendor = "Apple",
        description = "Detector de códigos del framework Vision sobre una AVCaptureSession. " +
            "Nativo del sistema, sin dependencias externas.",
        platforms = setOf(ScannerPlatform.Ios),
        plannedPhase = 3,
        requiresDependency = null,
        strength = "Nativo, rápido y sin dependencias de terceros",
        limitation = "Solo iOS; el set de simbologías varía según la versión del sistema",
        capabilities = ScannerCapabilities(
            supportedFormats = LINEAR_AND_MATRIX + BarcodeFormat.DataBar + BarcodeFormat.MicroQrCode,
            sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
            supportsMultipleCodes = true,
            supportsContinuousScan = true,
            supportsTorch = true,
            supportsZoom = true,
            reportsCornerPoints = true,
        ),
    )

    val zxingCpp = ScannerEngineDescriptor(
        id = ScannerEngineId.ZXingCpp,
        displayName = "ZXing-cpp",
        vendor = "Comunidad ZXing",
        description = "Decodificador nativo portado a Kotlin Multiplatform. Al ser el mismo " +
            "algoritmo en todas las plataformas, es la referencia para comparar el resto.",
        platforms = setOf(ScannerPlatform.Android, ScannerPlatform.Ios, ScannerPlatform.Desktop),
        plannedPhase = 3,
        requiresDependency = "binding KMP de zxing-cpp",
        strength = "Cobertura de simbologías más amplia y 100 % offline",
        limitation = "Menos tolerante que ML Kit a códigos dañados o mal iluminados",
        capabilities = ScannerCapabilities(
            supportedFormats = BarcodeFormat.all,
            sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
            supportsMultipleCodes = true,
            supportsContinuousScan = true,
            supportsTorch = true,
            supportsZoom = true,
            reportsCornerPoints = true,
        ),
    )

    val browserDetector = ScannerEngineDescriptor(
        id = ScannerEngineId.BrowserDetector,
        displayName = "BarcodeDetector API",
        vendor = "Navegador",
        description = "API de detección de códigos que expone el propio navegador. Peso cero, " +
            "pero su disponibilidad depende del navegador del usuario.",
        platforms = setOf(ScannerPlatform.Web),
        plannedPhase = 4,
        requiresDependency = null,
        strength = "No añade nada al bundle: lo provee la plataforma",
        limitation = "Soporte desigual entre navegadores; requiere contexto HTTPS",
        capabilities = ScannerCapabilities(
            supportedFormats = LINEAR_AND_MATRIX,
            sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
            supportsMultipleCodes = true,
            supportsContinuousScan = true,
            reportsCornerPoints = true,
        ),
    )

    val mlKitOcr = ScannerEngineDescriptor(
        id = ScannerEngineId.MlKitOcr,
        displayName = "ML Kit Text Recognition (OCR)",
        vendor = "Google",
        description = "No decodifica la simbología: lee el número impreso bajo el código y valida " +
            "su dígito de control. Recupera códigos que ningún decodificador puede leer.",
        platforms = setOf(ScannerPlatform.Android, ScannerPlatform.Ios),
        plannedPhase = 4,
        requiresDependency = "com.google.mlkit:text-recognition",
        strength = "Última alternativa cuando el código está dañado o borroso",
        limitation = "Solo sirve para simbologías cuyo valor va impreso en texto (1D de producto)",
        capabilities = ScannerCapabilities(
            supportedFormats = BarcodeFormat.oneDimensional,
            sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
            supportsMultipleCodes = true,
            supportsContinuousScan = true,
            supportsTorch = true,
            supportsZoom = true,
            reportsCornerPoints = true,
            reportsConfidence = true,
            requiresRuntimeDownload = true,
        ),
    )

    val manualInput = ScannerEngineDescriptor(
        id = ScannerEngineId.ManualInput,
        displayName = "Entrada manual",
        vendor = "TestScanner",
        description = "El usuario teclea el código. Infiere la simbología a partir del contenido " +
            "y valida el dígito de control cuando aplica.",
        platforms = ScannerPlatform.entries.toSet(),
        plannedPhase = 1,
        requiresDependency = null,
        strength = "Siempre disponible: cierra la cadena de fallback en las cuatro plataformas",
        limitation = "Requiere que el usuario lea el código a mano",
        capabilities = ScannerCapabilities(
            supportedFormats = BarcodeFormat.all,
            sources = setOf(ScanSource.ManualInput),
            supportsContinuousScan = true,
            requiresCameraPermission = false,
        ),
    )

    /** Catálogo completo, en orden de presentación. */
    val all: List<ScannerEngineDescriptor> = listOf(
        gmsCodeScanner,
        mlKitCameraX,
        visionIos,
        zxingCpp,
        browserDetector,
        mlKitOcr,
        manualInput,
    )

    fun byId(id: ScannerEngineId): ScannerEngineDescriptor =
        all.first { it.id == id }

    fun forPlatform(platform: ScannerPlatform): List<ScannerEngineDescriptor> =
        all.filter { it.runsOn(platform) }
}
