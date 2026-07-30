package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComparingScannerEngineTest {

    private val request = ScanRequest()

    private fun detecting(id: ScannerEngineId, value: String, latency: Long) = FakeScannerEngine(
        id = id,
        events = listOf(
            ScanEvent.Detected(
                listOf(
                    FakeScannerEngine.detection(id, value = value).copy(latencyMillis = latency),
                ),
            ),
        ),
    )

    @Test
    fun `arranca todos los motores y conserva la autoria de cada deteccion`() = runTest {
        val a = detecting(ScannerEngineId.MlKitCameraX, "codigo", latency = 120)
        val b = detecting(ScannerEngineId.ZXingCpp, "codigo", latency = 340)

        val events = ComparingScannerEngine(listOf(a, b)).scan(request).toList()

        assertEquals(1, a.scanInvocations)
        assertEquals(1, b.scanInvocations)
        val authors = events.filterIsInstance<ScanEvent.Detected>()
            .flatMap { it.detections }
            .map { it.engineId }
            .toSet()
        assertEquals(setOf(ScannerEngineId.MlKitCameraX, ScannerEngineId.ZXingCpp), authors)
    }

    @Test
    fun `la comparacion se ve desde fuera como una unica sesion`() = runTest {
        val engines = listOf(
            detecting(ScannerEngineId.MlKitCameraX, "x", latency = 10),
            detecting(ScannerEngineId.ZXingCpp, "x", latency = 20),
        )

        val events = ComparingScannerEngine(engines).scan(request).toList()

        assertEquals(1, events.count { it is ScanEvent.SessionStarted })
        assertEquals(1, events.count { it is ScanEvent.SessionEnded })
        assertTrue(events.first() is ScanEvent.SessionStarted)
        assertTrue(events.last() is ScanEvent.SessionEnded)
    }

    @Test
    fun `no arranca motores no disponibles`() = runTest {
        val available = detecting(ScannerEngineId.MlKitCameraX, "x", latency = 10)
        val other = detecting(ScannerEngineId.ZXingCpp, "x", latency = 10)
        val unavailable = FakeScannerEngine(
            id = ScannerEngineId.GmsCodeScanner,
            availability = EngineAvailability.NotImplemented(plannedPhase = 2),
        )

        ComparingScannerEngine(listOf(available, other, unavailable)).scan(request).toList()

        assertEquals(0, unavailable.scanInvocations)
    }

    @Test
    fun `sin dos motores disponibles no hay nada que comparar`() = runTest {
        val engines = listOf(
            FakeScannerEngine(ScannerEngineId.ManualInput),
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                availability = EngineAvailability.Unsupported("sin Play Services"),
            ),
        )
        val comparing = ComparingScannerEngine(engines)

        assertTrue(comparing.availability() is EngineAvailability.Unsupported)

        val events = comparing.scan(request).toList()
        assertTrue(events.filterIsInstance<ScanEvent.Failed>().single().error.isFatal)
        assertTrue(events.last() is ScanEvent.SessionEnded)
    }

    @Test
    fun `comparar con un solo motor es un error de programacion`() {
        assertFailsWith<IllegalArgumentException> {
            ComparingScannerEngine(listOf(FakeScannerEngine(ScannerEngineId.ManualInput)))
        }
    }
}
