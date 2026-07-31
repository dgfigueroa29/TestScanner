package com.testscanner.engines.manual

import app.cash.turbine.test
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.FakeTimeProvider
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManualInputScannerEngineTest {

    private val time = FakeTimeProvider(now = 1_000L)
    private val engine = ManualInputScannerEngine(time)
    private val request = ScanRequest(source = ScanSource.ManualInput)

    @Test
    fun `siempre esta disponible`() = runTest {
        assertEquals(EngineAvailability.Available, engine.availability())
    }

    @Test
    fun `emite inicio, deteccion y fin ante un valor tecleado`() = runTest {
        engine.scan(request).test {
            assertEquals(ScanEvent.SessionStarted(ScannerEngineId.ManualInput), awaitItem())

            time.advanceBy(millis = 250)
            engine.submit("https://ejemplo.com")

            val detected = assertIs<ScanEvent.Detected>(awaitItem())
            val detection = detected.detections.single()
            assertEquals("https://ejemplo.com", detection.barcode.rawValue)
            assertEquals(ScannerEngineId.ManualInput, detection.engineId)
            assertEquals(ScanSource.ManualInput, detection.source)
            assertEquals(250L, detection.latencyMillis)

            assertEquals(ScanEvent.SessionEnded(ScannerEngineId.ManualInput), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `en modo continuo la sesion sigue viva tras una deteccion`() = runTest {
        engine.scan(request.copy(continuous = true)).test {
            awaitItem() // SessionStarted

            engine.submit("primero")
            assertIs<ScanEvent.Detected>(awaitItem())

            engine.submit("segundo")
            val second = assertIs<ScanEvent.Detected>(awaitItem())
            assertEquals("segundo", second.detections.single().barcode.rawValue)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rechaza un valor cuyo formato no esta entre los solicitados`() = runTest {
        val onlyEan = request.copy(formats = setOf(BarcodeFormat.Ean13))

        engine.scan(onlyEan).test {
            awaitItem() // SessionStarted

            engine.submit("esto es un QR")

            val failure = assertIs<ScanEvent.Failed>(awaitItem())
            assertTrue(!failure.error.isFatal, "un formato rechazado no debe matar la sesión")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un valor vacio produce un fallo transitorio`() = runTest {
        engine.scan(request).test {
            awaitItem() // SessionStarted

            engine.submit("   ")

            val failure = assertIs<ScanEvent.Failed>(awaitItem())
            assertTrue(!failure.error.isFatal)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el descriptor coincide con el del catalogo`() {
        assertEquals(ScannerEngineId.ManualInput, engine.id)
        assertEquals(ScannerEngineId.ManualInput, engine.descriptor.id)
        assertTrue(ScanSource.ManualInput in engine.descriptor.capabilities.sources)
    }
}
