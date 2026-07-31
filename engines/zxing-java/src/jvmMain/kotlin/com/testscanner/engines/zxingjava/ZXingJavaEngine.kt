package com.testscanner.engines.zxingjava

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ImageDecodingEngine
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import com.google.zxing.Result as ZXingResult

/**
 * Decodificador de escritorio sobre `com.google.zxing:core` (deuda D13).
 *
 * Antes de este motor, escritorio solo sabía escanear tecleando: el selector de imágenes existía
 * desde RF-07, pero al elegir un archivo no había quién lo leyera. Es también el primer motor del
 * proyecto que se puede verificar de verdad sin dispositivo, porque el propio ZXing genera los
 * códigos con los que se le pone a prueba.
 *
 * **No lee de la cámara**, y no por limitación del decodificador: en escritorio no hay captura de
 * webcam en el proyecto. Por eso declara [com.testscanner.core.model.ScanSource.StaticImage] como
 * única fuente, y una sesión en vivo falla en vez de quedarse abierta sin emitir nada.
 */
class ZXingJavaEngine : BarcodeScannerEngine, ImageDecodingEngine {

    override val id: ScannerEngineId = ScannerEngineId.ZXingJava

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.zxingJava

    // El decodificador viaja dentro del jar: no hay SDK que consultar, modelo que descargar ni
    // permiso que pedir. Es lo que lo hace útil como último recurso en escritorio.
    override suspend fun availability(): EngineAvailability = EngineAvailability.Available

    /**
     * Una sesión en vivo no es posible aquí, así que termina en cuanto empieza.
     *
     * El selector no debería llegar nunca a llamarla —el descriptor no declara la cámara como
     * fuente—, pero el contrato exige que toda sesión abra con `SessionStarted` y cierre con
     * `SessionEnded`. Dejar el `Flow` colgado sería peor: la UI se quedaría esperando para siempre.
     */
    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.SessionStarted(id))
        emit(
            ScanEvent.Failed(
                ScanError.EngineUnavailable(id, "ZXing (Java) solo decodifica imágenes"),
                id,
            ),
        )
        emit(ScanEvent.SessionEnded(id))
    }

    override suspend fun decode(
        image: ScanImage,
        request: ScanRequest,
    ): Result<List<Barcode>> = runCatching {
        val decoded = ImageIO.read(ByteArrayInputStream(image.encoded))
            ?: error("Formato de imagen no soportado (${image.mimeType})")

        val bitmap = BinaryBitmap(HybridBinarizer(decoded.toLuminanceSource()))
        val reader = MultiFormatReader()
        val hints = hintsFor(request)

        try {
            // Las pistas van en cada llamada y no con `setHints`: `GenericMultipleBarcodeReader`
            // reenvía las que recibe al lector que envuelve, de modo que las guardadas se
            // sobreescribirían con las de la llamada. Pasarlas siempre evita esa asimetría.
            if (request.allowMultiple) {
                GenericMultipleBarcodeReader(reader).decodeMultiple(bitmap, hints).toList()
            } else {
                listOf(reader.decode(bitmap, hints))
            }
        } catch (@Suppress("SwallowedException") notFound: NotFoundException) {
            // La excepción se descarta a conciencia: `NotFoundException` no lleva información útil
            // —solo dice que no había código— y aquí eso no es un fallo del motor sino la
            // respuesta. Convertirlo en `Result.failure` haría que el caso de uso siguiera
            // probando motores y acabara enseñando un error donde solo hay una imagen sin código.
            emptyList()
        }.map(ZXingResult::toBarcode)
    }

    private fun hintsFor(request: ScanRequest): Map<DecodeHintType, Any> = buildMap {
        // Decodificar un archivo no compite con los frames de una cámara: aquí sí compensa que
        // ZXing pruebe rotaciones y umbrales adicionales.
        put(DecodeHintType.TRY_HARDER, true)
        ZXingJavaFormatMapper.toZXingFormats(request.formats)?.let {
            put(DecodeHintType.POSSIBLE_FORMATS, it)
        }
    }
}

private fun BufferedImage.toLuminanceSource(): RGBLuminanceSource {
    val pixels = getRGB(0, 0, width, height, null, 0, width)
    return RGBLuminanceSource(width, height, pixels)
}

/**
 * Traduce una lectura de ZXing al modelo del dominio.
 *
 * Se dejan fuera dos cosas a propósito:
 * - Las esquinas. ZXing devuelve dos puntos en los códigos lineales y cuatro en los 2D, así que no
 *   son las esquinas que el overlay espera. El descriptor declara `reportsCornerPoints = false` y
 *   esto lo respeta; inventar un rectángulo a partir de dos puntos sería dibujar una mentira.
 * - `rawBytes`. Lo que devuelve `getRawBytes()` son las palabras de código del símbolo, con su
 *   cabecera y su corrección de errores dentro, no la carga binaria decodificada que el modelo
 *   documenta. Pasarlo sería dar por buena una interpretación equivocada de esos bytes.
 */
private fun ZXingResult.toBarcode(): Barcode = Barcode(
    rawValue = text,
    format = ZXingJavaFormatMapper.toDomain(barcodeFormat),
)
