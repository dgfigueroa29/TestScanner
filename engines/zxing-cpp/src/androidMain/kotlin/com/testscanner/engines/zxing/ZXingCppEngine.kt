package com.testscanner.engines.zxing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import zxingcpp.BarcodeReader
import java.util.concurrent.Executor

/**
 * ZXing-cpp en Android, sobre el mismo pipeline de CameraX que usa el motor de ML Kit.
 *
 * Es el **baseline de comparación** (ADR-0008): el mismo decodificador C++ corre aquí y en iOS, así
 * que una diferencia de lectura entre las dos plataformas se atribuye al dispositivo y no al motor.
 * Sin un motor común, contrastar ML Kit contra Vision mezcla dos variables.
 *
 * Aporta además simbologías que ningún otro motor del catálogo cubre —DataBar, MaxiCode, Micro QR,
 * rMQR—, así que no solo sirve de control: amplía la cobertura real de G3.
 */
class ZXingCppEngine(
    private val context: Context,
    private val analysisExecutor: Executor,
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, CameraControlEngine, ImageDecodingEngine, CameraPreviewEngine {

    /** Controlador que la capa de Compose enlaza al ciclo de vida y al `PreviewView`. */
    val cameraController: LifecycleCameraController by lazy { LifecycleCameraController(context) }

    override val id: ScannerEngineId = ScannerEngineId.ZXingCpp

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.zxingCpp

    override suspend fun availability(): EngineAvailability = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
            EngineAvailability.Unsupported("El dispositivo no tiene cámara")

        !hasCameraPermission() -> EngineAvailability.RequiresPermission(Permission.Camera)

        else -> EngineAvailability.Available
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = callbackFlow {
        val startedAtMillis = time.nowMillis()
        trySend(ScanEvent.SessionStarted(id))

        val reader = readerFor(request)

        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
            analyze(imageProxy, reader, startedAtMillis) { trySend(it) }
        }

        awaitClose { cameraController.clearImageAnalysisAnalyzer() }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderZXingPreview(modifier)
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

        readerFor(request)
            .read(bitmap)
            .mapNotNull { it.toBarcode(bitmap.width, bitmap.height) }
    }

    /**
     * `tryHarder` y `tryRotate` se activan siempre.
     *
     * Cuestan tiempo por frame, y esa es exactamente la característica que este motor aporta a la
     * comparación: es el lento y minucioso frente a los rápidos. Desactivarlos lo haría parecerse a
     * ML Kit y el marcador dejaría de decir nada interesante.
     */
    private fun readerFor(request: ScanRequest) = BarcodeReader(
        BarcodeReader.Options(
            formats = ZXingFormatMapper.toZXingFormats(request.formats),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            maxNumberOfSymbols = if (request.allowMultiple) MAX_SYMBOLS else 1,
        ),
    )

    private fun analyze(
        imageProxy: ImageProxy,
        reader: BarcodeReader,
        startedAtMillis: Long,
        onEvent: (ScanEvent) -> Unit,
    ) {
        try {
            val now = time.nowMillis()
            val results = reader.read(imageProxy)
            val (width, height) = imageProxy.analyzedSize()

            val detections = results.mapNotNull { result ->
                result.toBarcode(width, height)?.let { barcode ->
                    Detection.of(
                        barcode = barcode,
                        engineId = id,
                        detectedAtMillis = now,
                        latencyMillis = now - startedAtMillis,
                    )
                }
            }

            if (detections.isEmpty()) {
                onEvent(ScanEvent.FrameAnalyzed(id, now))
            } else {
                onEvent(ScanEvent.Detected(detections))
            }
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // El lector lanza si el frame llega en un formato YUV que no admite. Es transitorio y
            // no debe apagar la cámara ni degradar de motor.
            onEvent(ScanEvent.Failed(ScanError.DecodeFailed(failure.message.orEmpty()), engineId = id))
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Tamaño contra el que normalizar las esquinas.
     *
     * El lector recibe el `cropRect` y el giro del `ImageProxy`, y devuelve coordenadas ya en el
     * espacio girado. Por eso se intercambian ancho y alto en 90° y 270°: normalizar contra las
     * dimensiones sin girar dejaría el overlay cruzado.
     */
    private fun ImageProxy.analyzedSize(): Pair<Int, Int> {
        val rect = cropRect
        val quarterTurn = imageInfo.rotationDegrees % HALF_TURN != 0
        return if (quarterTurn) rect.height() to rect.width() else rect.width() to rect.height()
    }

    private fun BarcodeReader.Result.toBarcode(width: Int, height: Int): Barcode? {
        // `returnErrors` está desactivado, así que un resultado con error no debería llegar; si
        // llega, es una lectura que el propio decodificador considera inválida.
        if (error != null) return null
        val value = text ?: return null

        return Barcode(
            rawValue = value,
            format = ZXingFormatMapper.fromZXing(format),
            rawBytes = bytes,
            cornerPoints = position.normalized(width, height),
        )
    }

    private fun BarcodeReader.Position.normalized(width: Int, height: Int): List<Point>? {
        if (width <= 0 || height <= 0) return null
        return listOf(topLeft, topRight, bottomRight, bottomLeft).map {
            Point(it.x.toFloat() / width, it.y.toFloat() / height)
        }
    }

    private fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_SYMBOLS = 0xff
        const val HALF_TURN = 180
    }
}
