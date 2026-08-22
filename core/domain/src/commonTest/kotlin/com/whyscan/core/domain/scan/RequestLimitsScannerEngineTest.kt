package com.whyscan.core.domain.scan

import com.whyscan.core.domain.FakeScannerEngine
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.ScanEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestLimitsScannerEngineTest {

    private val engineId = ScannerEngineId.MlKitCameraX

    private fun engineReporting(vararg events: ScanEvent) =
        FakeScannerEngine(id = engineId, events = events.toList())

    private fun detectedFrame(vararg values: String) = ScanEvent.Detected(
        values.map { FakeScannerEngine.detection(engineId, value = it) },
    )

    @Test
    fun `en modo puntual la sesion termina con la primera lectura`() = runTest {
        // Es lo que hacía que el interruptor de "escaneo continuo" no tuviera efecto: los motores
        // de cámara seguían emitiendo para siempre.
        val engine = engineReporting(
            detectedFrame("primero"),
            detectedFrame("segundo"),
            detectedFrame("tercero"),
        )

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = false))
            .toList()

        assertEquals(1, events.count { it is ScanEvent.Detected })
        assertEquals(ScanEvent.SessionEnded(engineId), events.last())
    }

    @Test
    fun `en modo continuo se emiten todas las lecturas`() = runTest {
        val engine = engineReporting(detectedFrame("a"), detectedFrame("b"), detectedFrame("c"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = true, allowMultiple = true))
            .toList()

        assertEquals(3, events.count { it is ScanEvent.Detected })
    }

    @Test
    fun `sin multiples codigos se recorta el frame a una sola lectura`() = runTest {
        val engine = engineReporting(detectedFrame("uno", "dos", "tres"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = true, allowMultiple = false))
            .toList()

        val detected = events.filterIsInstance<ScanEvent.Detected>().single()
        assertEquals(1, detected.detections.size)
        assertEquals("uno", detected.detections.single().barcode.rawValue)
    }

    @Test
    fun `recortar no descarta el frame entero`() = runTest {
        // Devolver cero lecturas porque el motor vio tres sería absurdo para el usuario.
        val engine = engineReporting(detectedFrame("uno", "dos"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = false, allowMultiple = false))
            .toList()

        assertTrue(events.any { it is ScanEvent.Detected })
    }

    @Test
    fun `con multiples codigos permitidos el frame llega completo`() = runTest {
        val engine = engineReporting(detectedFrame("uno", "dos", "tres"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = true, allowMultiple = true))
            .toList()

        assertEquals(3, events.filterIsInstance<ScanEvent.Detected>().single().detections.size)
    }

    @Test
    fun `un frame vacio no termina una sesion puntual`() = runTest {
        // Detected sin detecciones puede llegar tras el filtrado por formato; no es una lectura.
        val engine = engineReporting(
            ScanEvent.Detected(emptyList()),
            detectedFrame("real"),
        )

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = false))
            .toList()

        val ultima = events.filterIsInstance<ScanEvent.Detected>().last()
        assertEquals("real", ultima.detections.single().barcode.rawValue)
    }

    @Test
    fun `sin limites que aplicar el stream pasa intacto`() = runTest {
        val engine = engineReporting(detectedFrame("a"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = true, allowMultiple = true))
            .toList()

        assertEquals(ScanEvent.SessionStarted(engineId), events.first())
        assertEquals(ScanEvent.SessionEnded(engineId), events.last())
    }

    @Test
    fun `solo se emite un SessionEnded aunque el motor tambien lo emita`() = runTest {
        val engine = engineReporting(detectedFrame("a"))

        val events = RequestLimitsScannerEngine(engine)
            .scan(ScanRequest(continuous = false))
            .toList()

        assertEquals(1, events.count { it is ScanEvent.SessionEnded })
    }
}
