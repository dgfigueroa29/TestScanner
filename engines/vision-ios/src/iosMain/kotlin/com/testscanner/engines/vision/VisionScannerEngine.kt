package com.testscanner.engines.vision

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.Detection
import com.testscanner.core.model.Permission
import com.testscanner.core.model.Point
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.CameraControlEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TimeProvider
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithMediaType
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * Escáner nativo de iOS sobre `AVCaptureSession` y `AVCaptureMetadataOutput`.
 *
 * Se usa la salida de **metadatos** y no `VNDetectBarcodesRequest` del framework Vision: para vídeo
 * en vivo, AVFoundation ya detecta los códigos dentro del pipeline de captura, sin que la app tenga
 * que procesar frame a frame. Vision es la herramienta correcta para imágenes estáticas, y ahí
 * entrará cuando llegue RF-07.
 *
 * Es el motor con menos dependencias del catálogo: todo lo que usa viene con el sistema.
 *
 * ### Reparto con la UI
 * La `AVCaptureSession` la crea el motor y la consume el preview a través de
 * [CameraSessionHolder]; el motor no guarda ninguna vista ni ningún `UIViewController`.
 */
@OptIn(ExperimentalForeignApi::class)
class VisionScannerEngine(
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, CameraControlEngine, CameraPreviewEngine {

    internal val sessionHolder = CameraSessionHolder()

    override val id: ScannerEngineId = ScannerEngineId.VisionIos

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.visionIos

    override suspend fun availability(): EngineAvailability =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> EngineAvailability.Available

            AVAuthorizationStatusNotDetermined,
            AVAuthorizationStatusDenied,
            -> EngineAvailability.RequiresPermission(Permission.Camera)

            // "Restricted" es control parental o MDM: el usuario no puede concederlo aunque quiera,
            // así que no es lo mismo que "falta permiso" y la UI no debe ofrecer pedirlo.
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
                ScanEvent.Failed(ScanError.CameraUnavailable("No se pudo abrir la cámara trasera"), id),
            )
            trySend(ScanEvent.SessionEnded(id))
            close()
            return@callbackFlow
        }
        session.addInput(input)

        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            trySend(
                ScanEvent.Failed(ScanError.CameraUnavailable("No se pudo instalar el detector"), id),
            )
            trySend(ScanEvent.SessionEnded(id))
            close()
            return@callbackFlow
        }
        session.addOutput(output)

        val delegate = MetadataDelegate { codes ->
            val now = time.nowMillis()
            val detections = codes.mapNotNull { it.toDetection(now, now - startedAtMillis) }
            if (detections.isEmpty()) {
                trySend(ScanEvent.FrameAnalyzed(id, now))
            } else {
                trySend(ScanEvent.Detected(detections))
            }
        }

        // Cola principal: AVFoundation exige que el delegate corra en una cola serie, y la sesión
        // vive ligada a la UI de todas formas.
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        // El orden importa: `availableMetadataObjectTypes` solo está poblado DESPUÉS de añadir la
        // salida a la sesión. Pedir un tipo no soportado por el dispositivo lanza una excepción.
        output.metadataObjectTypes = VisionFormatMapper.toVisionTypes(request.formats)
            .filter { it in output.availableMetadataObjectTypes }

        sessionHolder.attach(session, camera)
        session.startRunning()
        trySend(ScanEvent.SessionStarted(id))

        awaitClose {
            session.stopRunning()
            output.setMetadataObjectsDelegate(null, null)
            sessionHolder.detach()
        }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderCameraPreview(sessionHolder, modifier)
    }

    override suspend fun setTorch(enabled: Boolean) = sessionHolder.setTorch(enabled)

    override suspend fun setZoomRatio(ratio: Float) = sessionHolder.setZoom(ratio)

    /**
     * `bounds` ya viene normalizado a `[0, 1]` sobre el espacio de la captura, que es exactamente
     * el contrato de [Point]. En Android hay que dividir por el tamaño del frame; aquí no.
     */
    private fun AVMetadataMachineReadableCodeObject.toDetection(
        nowMillis: Long,
        latencyMillis: Long,
    ): Detection? {
        val value = stringValue ?: return null

        val corners = bounds.useContents {
            listOf(
                Point(origin.x.toFloat(), origin.y.toFloat()),
                Point((origin.x + size.width).toFloat(), origin.y.toFloat()),
                Point((origin.x + size.width).toFloat(), (origin.y + size.height).toFloat()),
                Point(origin.x.toFloat(), (origin.y + size.height).toFloat()),
            )
        }

        return Detection.of(
            barcode = Barcode(
                rawValue = value,
                format = VisionFormatMapper.fromVision(type),
                cornerPoints = corners,
            ),
            engineId = id,
            detectedAtMillis = nowMillis,
            latencyMillis = latencyMillis,
        )
    }
}

/**
 * Delegate de Objective-C que recibe los códigos detectados.
 *
 * Tiene que ser un `NSObject` que implemente el protocolo; no se puede usar una lambda de Kotlin.
 */
@OptIn(ExperimentalForeignApi::class)
private class MetadataDelegate(
    private val onCodes: (List<AVMetadataMachineReadableCodeObject>) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        onCodes(didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>())
    }
}
