package com.whyscan.core.scanner.catalog

import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.ScannerCapabilities
import com.whyscan.core.scanner.ScannerEngineDescriptor

/**
 * Fichas de **todas** las alternativas de escaneo del producto, estén implementadas o no.
 *
 * Es el reflejo en código de `docs/ENGINES.md`; un test verifica que ambos no divergen.
 *
 * Que el catálogo esté completo desde la Fase 1 es deliberado: la UI muestra las ocho
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
        limitation = "Solo iOS; sin UPC-A propio (lo reporta como EAN-13) y todavía sin imagen " +
            "estática, que llega con RF-07 usando el framework Vision",
        capabilities = ScannerCapabilities(
            // Sin UPC-A: AVFoundation no tiene ese tipo y devuelve los UPC-A como EAN-13 con un
            // cero delante, que es lo que son. Declararlo sería prometer lo que no da.
            supportedFormats = LINEAR_AND_MATRIX - BarcodeFormat.UpcA + BarcodeFormat.MicroQrCode,
            // Imagen estática llega en la Fase 4 con Vision; declararlo ahora sería mentir y el
            // selector elegiría este motor para peticiones que no puede atender.
            sources = setOf(ScanSource.LiveCamera),
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
        description = "El mismo decodificador nativo en Android e iOS, así que una diferencia de " +
            "lectura entre plataformas se atribuye al dispositivo y no al motor: es la referencia " +
            "para comparar el resto.",
        // Desktop y Web quedan fuera: zxing-cpp no publica artefacto JVM ni wasmJs (ADR-0008).
        platforms = setOf(ScannerPlatform.Android, ScannerPlatform.Ios),
        plannedPhase = 3,
        requiresDependency = "io.github.zxing-cpp:android / :kotlin-native",
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

    val zxingJava = ScannerEngineDescriptor(
        id = ScannerEngineId.ZXingJava,
        displayName = "ZXing (Java)",
        vendor = "Comunidad ZXing",
        description = "El ZXing original, en Java puro. Es el único decodificador de escritorio: " +
            "hasta que llegó, en esa plataforma solo se podía teclear el código a mano.",
        platforms = setOf(ScannerPlatform.Desktop),
        plannedPhase = 5,
        requiresDependency = "com.google.zxing:core",
        strength = "Java puro: no necesita binarios nativos ni SDK de plataforma",
        // Que no lea de la cámara no es una limitación del decodificador sino del proyecto: no hay
        // captura de webcam en escritorio. Se declara como límite porque es lo que el usuario ve.
        limitation = "Solo imagen estática: en escritorio no hay captura de cámara",
        capabilities = ScannerCapabilities(
            // Sin Micro QR ni rMQR: `com.google.zxing.BarcodeFormat` no tiene esas constantes.
            // El port a C++ sí las lee, y esa diferencia es justo el tipo de dato que la app busca.
            supportedFormats = LINEAR_AND_MATRIX + BarcodeFormat.DataBar + BarcodeFormat.MaxiCode,
            sources = setOf(ScanSource.StaticImage),
            supportsMultipleCodes = true,
            requiresCameraPermission = false,
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
            // `com.google.mlkit:text-recognition` es la variante *bundled*: el modelo latino viaja
            // en el APK. No hay descarga en el primer uso, a diferencia del detector de códigos.
            requiresRuntimeDownload = false,
        ),
    )

    val manualInput = ScannerEngineDescriptor(
        id = ScannerEngineId.ManualInput,
        displayName = "Entrada manual",
        vendor = "WhyScan",
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
        zxingJava,
        browserDetector,
        mlKitOcr,
        manualInput,
    )

    fun byId(id: ScannerEngineId): ScannerEngineDescriptor =
        all.first { it.id == id }

    fun forPlatform(platform: ScannerPlatform): List<ScannerEngineDescriptor> =
        all.filter { it.runsOn(platform) }
}
