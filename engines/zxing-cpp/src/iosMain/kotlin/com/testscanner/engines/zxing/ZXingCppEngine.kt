package com.testscanner.engines.zxing

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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithMediaType
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import zxingcpp.Barcode as ZXingBarcode
import zxingcpp.BarcodeReader

/**
 * ZXing-cpp en iOS, sobre `AVCaptureVideoDataOutput`.
 *
 * Es la otra mitad del baseline de comparación (ADR-0008): el mismo decodificador C++ que corre en
 * Android. Que ambos den lecturas distintas sobre el mismo código pasa a ser un dato del
 * dispositivo, no del motor — que es exactamente lo que G5 quiere poder afirmar.
 *
 * ### Por qué no usa `AVCaptureMetadataOutput` como el motor de Vision
 * Porque esa salida **ya trae un decodificador dentro**: devolvería códigos leídos por AVFoundation,
 * no por zxing-cpp, y el baseline dejaría de serlo. Aquí hacen falta los píxeles crudos, y eso es
 * `AVCaptureVideoDataOutput`. El precio es procesar frame a frame en la app en lugar de dejárselo
 * al sistema.
 */
@OptIn(ExperimentalForeignApi::class)
class ZXingCppEngine(
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, CameraControlEngine, ImageDecodingEngine, CameraPreviewEngine {

    internal val sessionHolder = ZXingCameraSessionHolder()

    override val id: ScannerEngineId = ScannerEngineId.ZXingCpp

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.zxingCpp

    override suspend fun availability(): EngineAvailability =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> EngineAvailability.Available

            AVAuthorizationStatusNotDetermined,
            AVAuthorizationStatusDenied,
            -> EngineAvailability.RequiresPermission(Permission.Camera)

            AVAuthorizationStatusRestricted -> EngineAvailability.Unsupported(
                "El acceso a la cámara está restringido por el dispositivo",
            )

            else -> EngineAvailability.Unsupported("Estado de autorización desconocido")
        }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = callbackFlow {
        val startedAtMillis = time.nowMillis()

        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (camera == null) {
            trySend(ScanEvent.Failed(ScanError.CameraUnavailable("No hay cámara disponible"), id))
            trySend(ScanEvent.SessionEnded(id))
            close()
            return@callbackFlow
        }

        val session = AVCaptureSession().apply { sessionPreset = AVCaptureSessionPresetHigh }

        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
        if (input == null || !session.canAddInput(input)) {
            trySend(
                ScanEvent.Failed(ScanError.CameraUnavailable("No se pudo abrir la cámara"), id),
            )
            trySend(ScanEvent.SessionEnded(id))
            close()
            return@callbackFlow
        }
        session.addInput(input)

        val output = AVCaptureVideoDataOutput().apply {
            // Formato YUV bi-planar: el plano 0 es luminancia, que es lo único que necesita el
            // decodificador. Pedir BGRA obligaría a convertir cada frame para tirar el color.
            videoSettings = mapOf(
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            )
            // Sin esto la cola de frames crece cuando la decodificación no llega, y el preview se
            // va retrasando hasta que la app parece colgada.
            alwaysDiscardsLateVideoFrames = true
        }
        if (!session.canAddOutput(output)) {
            trySend(
                ScanEvent.Failed(ScanError.CameraUnavailable("No se pudo instalar la salida"), id),
            )
            trySend(ScanEvent.SessionEnded(id))
            close()
            return@callbackFlow
        }
        session.addOutput(output)

        val reader = readerFor(request)

        val delegate = FrameDelegate { pixelBuffer ->
            val now = time.nowMillis()
            val detections = runCatching {
                val frame = pixelBuffer.toLumaImageView() ?: return@runCatching emptyList()
                reader.read(frame).mapNotNull {
                    it.toDetection(frame.width, frame.height, now, now - startedAtMillis)
                }
            }

            detections
                .onSuccess { found ->
                    if (found.isEmpty()) {
                        trySend(ScanEvent.FrameAnalyzed(id, now))
                    } else {
                        trySend(ScanEvent.Detected(found))
                    }
                }
                .onFailure { failure ->
                    // Un frame ilegible es transitorio: no apaga la cámara ni degrada de motor.
                    trySend(
                        ScanEvent.Failed(ScanError.DecodeFailed(failure.message.orEmpty()), id),
                    )
                }
        }

        // Cola propia y no la principal: aquí se decodifica de verdad en cada frame, y hacerlo en
        // el hilo de UI congelaría el preview. Es la diferencia con el motor de Vision, que solo
        // recibe metadatos ya procesados por el sistema.
        val queue = dispatch_queue_create("com.testscanner.zxingcpp.frames", null)
        output.setSampleBufferDelegate(delegate, queue)

        sessionHolder.attach(session, camera)
        session.startRunning()
        trySend(ScanEvent.SessionStarted(id))

        awaitClose {
            session.stopRunning()
            output.setSampleBufferDelegate(null, null)
            sessionHolder.detach()
        }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderZXingPreview(sessionHolder, modifier)
    }

    override suspend fun setTorch(enabled: Boolean) = sessionHolder.setTorch(enabled)

    override suspend fun setZoomRatio(ratio: Float) = sessionHolder.setZoom(ratio)

    /** Decodifica una imagen ya capturada (RF-07): es la primera vez que iOS puede hacerlo. */
    override suspend fun decode(
        image: ScanImage,
        request: ScanRequest,
    ): Result<List<Barcode>> = runCatching {
        val imageView = image.encoded.toRgbaImageView()
            ?: error("No se pudo rasterizar la imagen (${image.mimeType})")

        readerFor(request).read(imageView).mapNotNull { it.toBarcode(imageView.width, imageView.height) }
    }

    /** Mismos ajustes que en Android: el motor lento y minucioso del catálogo. */
    private fun readerFor(request: ScanRequest) = BarcodeReader().apply {
        formats = ZXingFormatMapper.toZXingFormats(request.formats)
        tryHarder = true
        tryRotate = true
        tryInvert = true
        maxNumberOfSymbols = if (request.allowMultiple) MAX_SYMBOLS else 1
    }

    private fun ZXingBarcode.toDetection(
        width: Int,
        height: Int,
        nowMillis: Long,
        latencyMillis: Long,
    ): Detection? {
        val barcode = toBarcode(width, height) ?: return null
        return Detection.of(
            barcode = barcode,
            engineId = id,
            detectedAtMillis = nowMillis,
            latencyMillis = latencyMillis,
        )
    }

    private fun ZXingBarcode.toBarcode(width: Int, height: Int): Barcode? {
        if (!isValid) return null
        val value = text ?: return null

        return Barcode(
            rawValue = value,
            format = ZXingFormatMapper.fromZXing(format),
            rawBytes = bytes,
            cornerPoints = normalizedCorners(width, height),
        )
    }

    /**
     * zxing-cpp devuelve las esquinas en píxeles del frame y el modelo las quiere en `[0, 1]`.
     *
     * El tamaño sale del propio `ImageView` que se le pasó al lector, no de la sesión de captura:
     * es el único que corresponde con seguridad a las coordenadas devueltas. Normalizar contra el
     * tamaño del preview pintaría el recuadro desplazado y parecería un fallo de detección.
     */
    private fun ZXingBarcode.normalizedCorners(width: Int, height: Int): List<Point>? {
        if (width <= 0 || height <= 0) return null
        return position.let { corners ->
            listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
                .map { Point(it.x.toFloat() / width, it.y.toFloat() / height) }
        }
    }

    private companion object {
        const val MAX_SYMBOLS = 0xff
    }
}

/**
 * Delegate de Objective-C que recibe cada frame de vídeo.
 *
 * Tiene que ser un `NSObject` que implemente el protocolo; una lambda de Kotlin no vale.
 */
@OptIn(ExperimentalForeignApi::class)
private class FrameDelegate(
    private val onFrame: (CVPixelBufferRef) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val pixelBuffer = didOutputSampleBuffer?.let(::CMSampleBufferGetImageBuffer) ?: return
        onFrame(pixelBuffer)
    }
}
