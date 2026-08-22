package com.whyscan.engines.zxingjava

import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.ScanEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.google.zxing.BarcodeFormat as ZXingFormat

class ZXingJavaEngineTest {

    private val engine = ZXingJavaEngine()

    private fun imageRequest(formats: Set<BarcodeFormat> = BarcodeFormat.all) =
        ScanRequest(formats = formats, source = ScanSource.StaticImage)

    @Test
    fun `lee un QR generado de verdad`() = runTest {
        val image = BarcodeImages.png("https://example.org/hola", ZXingFormat.QR_CODE)

        val barcodes = engine.decode(image, imageRequest()).getOrThrow()

        assertEquals(1, barcodes.size)
        assertEquals("https://example.org/hola", barcodes.single().rawValue)
        assertEquals(BarcodeFormat.QrCode, barcodes.single().format)
    }

    @Test
    fun `lee un EAN-13 y lo reporta con su formato`() = runTest {
        // El caso de un código de producto importa aparte del QR: es 1D, se binariza distinto y es
        // lo que un usuario de escritorio escanea desde una foto.
        val image = BarcodeImages.png("7501234567893", ZXingFormat.EAN_13, width = 400, height = 200)

        val barcode = engine.decode(image, imageRequest()).getOrThrow().single()

        assertEquals("7501234567893", barcode.rawValue)
        assertEquals(BarcodeFormat.Ean13, barcode.format)
    }

    @Test
    fun `lee varios codigos de la misma imagen cuando se piden`() = runTest {
        val image = BarcodeImages.sideBySide(
            BarcodeImages.bitmap("primero", ZXingFormat.QR_CODE),
            BarcodeImages.bitmap("segundo", ZXingFormat.QR_CODE),
        )

        val values = engine
            .decode(image, imageRequest().copy(allowMultiple = true))
            .getOrThrow()
            .map { it.rawValue }

        assertEquals(setOf("primero", "segundo"), values.toSet())
    }

    @Test
    fun `una imagen sin codigo devuelve vacio y no un error`() = runTest {
        // Es la diferencia entre "no hay nada que leer" y "el motor falló": si esto fuera un fallo,
        // el caso de uso seguiría probando motores y acabaría enseñando un error al usuario.
        val blank = BarcodeImages.sideBySide(
            java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB),
        )

        val result = engine.decode(blank, imageRequest())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `un archivo que no es una imagen falla en vez de devolver vacio`() = runTest {
        // Aquí sí hay un fallo real que el usuario debe ver: eligió algo que no se puede leer.
        val garbage = ScanImage(encoded = "no soy una imagen".encodeToByteArray(), mimeType = "image/png")

        assertTrue(engine.decode(garbage, imageRequest()).isFailure)
    }

    @Test
    fun `respeta el filtro de formatos de la peticion`() = runTest {
        // Sin esto, filtrar por QR y elegir la foto de un EAN devolvería la lectura igualmente y el
        // dominio tendría que descartarla después: trabajo hecho para nada y una latencia peor.
        val image = BarcodeImages.png("7501234567893", ZXingFormat.EAN_13, width = 400, height = 200)

        val barcodes = engine
            .decode(image, imageRequest(formats = setOf(BarcodeFormat.QrCode)))
            .getOrThrow()

        assertTrue(barcodes.isEmpty())
    }

    @Test
    fun `una sesion en vivo termina en lugar de quedarse esperando`() = runTest {
        // En escritorio no hay captura de cámara. Que el Flow se quedara abierto dejaría la pantalla
        // de escaneo esperando para siempre, que es peor que decir que no se puede.
        val events = engine.scan(ScanRequest()).toList()

        assertTrue(events.first() is ScanEvent.SessionStarted)
        assertTrue(events.last() is ScanEvent.SessionEnded)
        assertTrue(events.any { it is ScanEvent.Failed })
    }

    @Test
    fun `el motor tiene identidad propia y no la de zxing-cpp`() {
        // Son proyectos distintos con el mismo linaje. Confundirlos falsearía la comparación entre
        // motores, que es justo para lo que existe la app.
        assertEquals(ScannerEngineId.ZXingJava, engine.id)
        assertEquals(ScannerEngineId.ZXingJava, engine.descriptor.id)
    }
}
