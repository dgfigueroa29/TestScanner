package com.whyscan.engines.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.Detection
import com.whyscan.core.model.Permission
import com.whyscan.core.model.Point
import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ImageDecodingEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerEngineDescriptor
import com.whyscan.core.scanner.SystemTimeProvider
import com.whyscan.core.scanner.TimeProvider
import com.whyscan.core.scanner.catalog.ScannerEngineCatalog
import com.whyscan.core.scanner.ui.CameraPreviewEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Escaneo con la `BarcodeDetector` API que trae el propio navegador.
 *
 * Es el motor más barato del catálogo: no añade **nada** al bundle, porque el decodificador ya está
 * en el navegador. A cambio es el menos predecible — Chrome y los navegadores basados en Android lo
 * traen, Firefox no, y Safari solo en parte —, así que [availability] es aquí más importante que en
 * ningún otro motor: es lo único que separa "escanea" de "no pasa nada al pulsar".
 */
class BrowserDetectorEngine(
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, ImageDecodingEngine, CameraPreviewEngine {

    internal val sessionHolder = BrowserSessionHolder()

    /**
     * El `<video>` es un elemento del DOM sobre el canvas, así que tapa cualquier cosa que Compose
     * pinte encima. Ver [RenderBrowserPreview].
     */
    override val occludesOverlay: Boolean = true

    override val id: ScannerEngineId = ScannerEngineId.BrowserDetector

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.browserDetector

    override suspend fun availability(): EngineAvailability = when {
        !detectorIsAvailable() -> EngineAvailability.Unsupported(
            "Este navegador no expone la API BarcodeDetector",
        )

        !isSecureContext() -> EngineAvailability.Unsupported(
            "La cámara requiere HTTPS o localhost",
        )

        else -> EngineAvailability.Available
    }

    /**
     * El permiso de cámara no se pide aquí: en el navegador lo pide `getUserMedia` al abrir la
     * sesión, y no existe forma de consultarlo por adelantado de manera fiable. Por eso el
     * `PermissionController` de Web concede siempre y la denegación aparece como un fallo de
     * sesión — que es donde el navegador la produce de verdad.
     */
    override fun scan(request: ScanRequest): Flow<ScanEvent> = channelFlow {
        val startedAtMillis = time.nowMillis()
        send(ScanEvent.SessionStarted(id))

        val session = try {
            startCameraSession(BrowserFormatMapper.toBrowserFilter(request.formats).orEmpty()).await<JsAny>()
        } catch (cancellation: CancellationException) {
            throw cancellation
            // Se captura `Throwable` a conciencia: al otro lado hay JavaScript, que puede lanzar
            // cualquier cosa —un `DOMException`, un string suelto— y no hay tipo concreto al que
            // agarrarse. Y se descarta porque en la práctica solo hay un motivo por el que
            // `getUserMedia` falla aquí: el usuario no dio permiso, que es lo que se reporta.
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Throwable) {
            send(ScanEvent.Failed(ScanError.PermissionDenied(Permission.Camera), engineId = id))
            send(ScanEvent.SessionEnded(id))
            return@channelFlow
        }

        sessionHolder.attach(session)

        try {
            while (true) {
                val now = time.nowMillis()
                val detections = runCatching { detectFrame(session).await<JsAny>() }
                    .map { results -> results.toDetections(session, now, now - startedAtMillis) }

                detections
                    .onSuccess { found ->
                        if (found.isEmpty()) {
                            send(ScanEvent.FrameAnalyzed(id, now))
                        } else {
                            send(ScanEvent.Detected(found))
                        }
                    }
                    .onFailure { error ->
                        // Un frame que falla es transitorio —el vídeo puede no tener datos aún—;
                        // no debe cerrar la cámara ni degradar de motor.
                        send(ScanEvent.Failed(ScanError.DecodeFailed(error.message.orEmpty()), engineId = id))
                    }

                delay(FRAME_INTERVAL_MILLIS)
            }
        } finally {
            // Sin esto el indicador de cámara del navegador se queda encendido al salir.
            sessionHolder.detach()
            stopCameraSession(session)
        }
    }

    @Composable
    override fun CameraPreview(modifier: Modifier) {
        RenderBrowserPreview(sessionHolder, modifier)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun decode(
        image: ScanImage,
        request: ScanRequest,
    ): Result<List<Barcode>> = runCatching {
        val dataUrl = "data:${image.mimeType};base64,${Base64.encode(image.encoded)}"
        val filter = BrowserFormatMapper.toBrowserFilter(request.formats).orEmpty()

        val results = detectDataUrl(filter, dataUrl).await<JsAny>()
        val size = imageSize(dataUrl).await<JsAny>()

        results.toBarcodes(width = sizeWidth(size), height = sizeHeight(size))
    }

    private fun JsAny.toDetections(
        session: JsAny,
        nowMillis: Long,
        latencyMillis: Long,
    ): List<Detection> = toBarcodes(
        width = sessionFrameWidth(session),
        height = sessionFrameHeight(session),
    ).map { barcode ->
        Detection.of(
            barcode = barcode,
            engineId = id,
            detectedAtMillis = nowMillis,
            latencyMillis = latencyMillis,
        )
    }

    private fun JsAny.toBarcodes(width: Int, height: Int): List<Barcode> =
        (0 until resultCount(this)).mapNotNull { index ->
            val rawValue = resultRawValue(this, index)
            if (rawValue.isEmpty()) return@mapNotNull null

            Barcode(
                rawValue = rawValue,
                format = BrowserFormatMapper.fromBrowser(resultFormat(this, index)),
                cornerPoints = cornersAt(index, width, height),
            )
        }

    /**
     * Las esquinas llegan en píxeles del frame y el modelo las quiere normalizadas a `[0, 1]`: es
     * lo que hace que el overlay sea el mismo dibujo en las cuatro plataformas y que los puntos de
     * dos motores distintos sean comparables entre sí.
     */
    private fun JsAny.cornersAt(index: Int, width: Int, height: Int): List<Point>? {
        if (width <= 0 || height <= 0) return null
        val corners = resultCornerCount(this, index)
        if (corners == 0) return null

        return (0 until corners).map { corner ->
            Point(
                x = (resultCornerX(this, index, corner) / width).toFloat(),
                y = (resultCornerY(this, index, corner) / height).toFloat(),
            )
        }
    }

    private companion object {
        /**
         * ~15 fps. El detector del navegador corre en el hilo principal: apurar a la tasa del vídeo
         * bloquearía la UI de Compose, que en Web comparte ese hilo.
         */
        const val FRAME_INTERVAL_MILLIS = 66L
    }
}
