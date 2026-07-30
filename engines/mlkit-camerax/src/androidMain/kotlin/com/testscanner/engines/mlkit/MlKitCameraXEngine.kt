package com.testscanner.engines.mlkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode
import com.google.mlkit.vision.common.InputImage
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.Detection
import com.testscanner.core.model.Permission
import com.testscanner.core.model.Point
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.CameraControlEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ImageDecodingEngine
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TimeProvider
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Análisis de frames de CameraX con el detector de códigos de ML Kit.
 *
 * A diferencia del Google Code Scanner, aquí la app controla la cámara: puede pintar overlay,
 * encender la linterna, hacer zoom y escanear en continuo. El precio es el permiso de cámara y el
 * peso del SDK — exactamente el intercambio que TestScanner existe para poner a prueba.
 *
 * ### Reparto de responsabilidades con la UI
 * El motor aporta su superficie de vídeo implementando `CameraPreviewEngine`, pero **no guarda el
 * `LifecycleOwner`**: ese lo toma el composable del árbol y lo suelta al salir de composición. Si
 * el motor lo retuviera, se quedaría con la Activity y filtraría memoria en cada rotación.
 *
 * El overlay de detección no se pinta aquí: los `cornerPoints` se reportan **normalizados a
 * [0, 1]** sobre el frame analizado, y la UI común los dibuja encima. Así el overlay es idéntico en
 * las cuatro plataformas y comparable entre motores.
 */
class MlKitCameraXEngine(
    private val context: Context,
    private val analysisExecutor: Executor,
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, CameraControlEngine, ImageDecodingEngine, CameraPreviewEngine {

    /** Controlador que la capa de Compose enlaza al ciclo de vida y al `PreviewView`. */
    val cameraController: LifecycleCameraController by lazy { LifecycleCameraController(context) }

    override val id: ScannerEngineId = ScannerEngineId.MlKitCameraX

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.mlKitCameraX

    override suspend fun availability(): EngineAvailability = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
            EngineAvailability.Unsupported("El dispositivo no tiene cámara")

        !hasCameraPermission() -> EngineAvailability.RequiresPermission(Permission.Camera)

        else -> EngineAvailability.Available
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = callbackFlow {
        val startedAtMillis = time.nowMillis()
        trySend(ScanEvent.SessionStarted(id))

        val scanner = createScanner(request)

        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
            analyze(
                imageProxy = imageProxy,
                scanner = scanner,
                startedAtMillis = startedAtMillis,
                onEvent = { trySend(it) },
            )
        }

        // `awaitClose` es lo que hace que cancelar la corrutina apague la cámara: sin él, salir de
        // la pantalla dejaría el analizador vivo consumiendo frames y batería.
        awaitClose {
            cameraController.clearImageAnalysisAnalyzer()
            scanner.close()
        }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderCameraPreview(modifier)
    }

    override suspend fun setTorch(enabled: Boolean) {
        cameraController.enableTorch(enabled)
    }

    override suspend fun setZoomRatio(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    override suspend fun decode(
        image: ScanImage,
        request: ScanRequest,
    ): Result<List<Barcode>> = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(image.encoded, 0, image.encoded.size)
            ?: error("No se pudo decodificar la imagen (${image.mimeType})")

        val scanner = createScanner(request)
        try {
            scanner.awaitProcess(InputImage.fromBitmap(bitmap, 0))
                .map { it.toBarcode(bitmap.width, bitmap.height) }
        } finally {
            scanner.close()
        }
    }

    private fun createScanner(request: ScanRequest): BarcodeScanner {
        val formats = MlKitFormatMapper.toMlKitFormats(request.formats)
        val options = BarcodeScannerOptions.Builder()
            .apply {
                // Si la petición incluye formatos que ML Kit no conoce se deja el detector en modo
                // "todos": el filtrado fino lo aplica el dominio, que es quien garantiza el mismo
                // comportamiento observable en los siete motores.
                formats?.let { setBarcodeFormats(it.first(), *it.drop(1).toIntArray()) }
            }
            .build()
        return BarcodeScanning.getClient(options)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(
        imageProxy: ImageProxy,
        scanner: BarcodeScanner,
        startedAtMillis: Long,
        onEvent: (ScanEvent) -> Unit,
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val now = time.nowMillis()
                if (barcodes.isEmpty()) {
                    onEvent(ScanEvent.FrameAnalyzed(now))
                } else {
                    onEvent(
                        ScanEvent.Detected(
                            barcodes.mapNotNull {
                        it.toDetection(
                            nowMillis = now,
                            latencyMillis = now - startedAtMillis,
                            imageWidth = input.width,
                            imageHeight = input.height,
                        )
                    },
                        ),
                    )
                }
            }
            .addOnFailureListener { error ->
                // Un frame que falla es transitorio: no debe apagar la cámara ni degradar de motor.
                onEvent(ScanEvent.Failed(ScanError.DecodeFailed(error.message.orEmpty())))
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Los puntos se normalizan contra el tamaño del frame analizado, que es lo único que el motor
     * conoce: la vista puede tener otro tamaño y otro recorte. Normalizar aquí deja el mapeo a
     * coordenadas de pantalla en manos de la UI, que sí sabe cómo se está escalando el preview.
     */
    private fun MlKitBarcode.toBarcode(imageWidth: Int, imageHeight: Int): Barcode = Barcode(
        rawValue = rawValue.orEmpty(),
        format = MlKitFormatMapper.fromMlKit(format),
        rawBytes = rawBytes,
        cornerPoints = cornerPoints
            ?.takeIf { imageWidth > 0 && imageHeight > 0 }
            ?.map { Point(it.x.toFloat() / imageWidth, it.y.toFloat() / imageHeight) },
    )

    private fun MlKitBarcode.toDetection(
        nowMillis: Long,
        latencyMillis: Long,
        imageWidth: Int,
        imageHeight: Int,
    ): Detection? {
        if (rawValue == null) return null
        return Detection.of(
            barcode = toBarcode(imageWidth, imageHeight),
            engineId = id,
            detectedAtMillis = nowMillis,
            latencyMillis = latencyMillis,
        )
    }
}

private suspend fun BarcodeScanner.awaitProcess(image: InputImage): List<MlKitBarcode> =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
