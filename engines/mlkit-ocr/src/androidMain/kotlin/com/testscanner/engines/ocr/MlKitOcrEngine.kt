package com.testscanner.engines.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Último recurso de la cadena de fallback: lee el número impreso bajo el código (RF-12).
 *
 * No decodifica barras. Reconoce texto con ML Kit y le pasa las líneas a [OcrCodeInterpreter], que
 * es quien decide si algo de lo leído es realmente un código —validando su dígito de control— o
 * solo un número que había en el envase. Toda la inteligencia del motor está ahí, en `commonMain`
 * y cubierta por tests; este archivo es el cableado con la cámara.
 *
 * ### Por qué duplica el pipeline de CameraX de `:engines:mlkit-camerax`
 * Porque son módulos independientes a propósito (RNF-06, RNF-07): una app que no quiera OCR lo
 * quita de `settings.gradle.kts` y no arrastra el modelo de texto, que no es pequeño. Factorizar el
 * pipeline crearía una dependencia entre motores y convertiría "quitar un motor" en refactor.
 */
class MlKitOcrEngine(
    private val context: Context,
    private val analysisExecutor: Executor,
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, CameraControlEngine, ImageDecodingEngine, CameraPreviewEngine {

    /** Controlador que la capa de Compose enlaza al ciclo de vida y al `PreviewView`. */
    val cameraController: LifecycleCameraController by lazy { LifecycleCameraController(context) }

    override val id: ScannerEngineId = ScannerEngineId.MlKitOcr

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.mlKitOcr

    override suspend fun availability(): EngineAvailability = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
            EngineAvailability.Unsupported("El dispositivo no tiene cámara")

        !hasCameraPermission() -> EngineAvailability.RequiresPermission(Permission.Camera)

        else -> EngineAvailability.Available
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = callbackFlow {
        val startedAtMillis = time.nowMillis()
        trySend(ScanEvent.SessionStarted(id))

        val recognizer = createRecognizer()

        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
            analyze(
                imageProxy = imageProxy,
                recognizer = recognizer,
                startedAtMillis = startedAtMillis,
                onEvent = { trySend(it) },
            )
        }

        awaitClose {
            cameraController.clearImageAnalysisAnalyzer()
            recognizer.close()
        }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderOcrPreview(modifier)
    }

    override suspend fun setTorch(enabled: Boolean) {
        cameraController.enableTorch(enabled)
    }

    override suspend fun setZoomRatio(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    /**
     * Es la fuente donde este motor rinde de verdad: sobre una foto quieta y bien enfocada de una
     * etiqueta dañada, que es justo el caso en el que los decodificadores fallan.
     */
    override suspend fun decode(
        image: ScanImage,
        request: ScanRequest,
    ): Result<List<Barcode>> = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(image.encoded, 0, image.encoded.size)
            ?: error("No se pudo decodificar la imagen (${image.mimeType})")

        val recognizer = createRecognizer()
        try {
            val text = recognizer.awaitProcess(InputImage.fromBitmap(bitmap, 0))
            OcrCodeInterpreter.interpret(text.toOcrLines(bitmap.width, bitmap.height))
        } finally {
            recognizer.close()
        }
    }

    // El reconocedor latino bundled: el modelo viaja en el APK, así que no hay descarga en el
    // primer uso ni dependencia de Play Services.
    private fun createRecognizer(): TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(
        imageProxy: ImageProxy,
        recognizer: TextRecognizer,
        startedAtMillis: Long,
        onEvent: (ScanEvent) -> Unit,
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(input)
            .addOnSuccessListener { text ->
                val now = time.nowMillis()
                val codes = OcrCodeInterpreter.interpret(text.toOcrLines(input.width, input.height))

                // Un frame con texto pero sin ningún checksum válido es un frame analizado sin
                // resultado, no un fallo: es el caso normal mientras se enfoca la etiqueta.
                if (codes.isEmpty()) {
                    onEvent(ScanEvent.FrameAnalyzed(id, now))
                } else {
                    onEvent(
                        ScanEvent.Detected(
                            codes.map { barcode ->
                                Detection.of(
                                    barcode = barcode,
                                    engineId = id,
                                    detectedAtMillis = now,
                                    latencyMillis = now - startedAtMillis,
                                )
                            },
                        ),
                    )
                }
            }
            .addOnFailureListener { error ->
                onEvent(ScanEvent.Failed(ScanError.DecodeFailed(error.message.orEmpty()), engineId = id))
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

/**
 * Aplana el resultado de ML Kit a líneas.
 *
 * Se trabaja por **línea** y no por bloque ni por palabra: el número de un EAN va impreso en una
 * sola línea, mientras que un bloque puede juntar el código con el texto de al lado —pegando cifras
 * que no van juntas— y una palabra lo parte por los espacios de la impresión.
 */
private fun Text.toOcrLines(imageWidth: Int, imageHeight: Int): List<OcrLine> =
    textBlocks.flatMap { block -> block.lines }.map { line ->
        OcrLine(
            text = line.text,
            confidence = line.confidence,
            cornerPoints = line.cornerPoints
                ?.takeIf { imageWidth > 0 && imageHeight > 0 }
                ?.map { Point(it.x.toFloat() / imageWidth, it.y.toFloat() / imageHeight) },
        )
    }

private suspend fun TextRecognizer.awaitProcess(image: InputImage): Text =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
