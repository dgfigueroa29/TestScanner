package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FallbackScannerEngineTest {

    private val request = ScanRequest()

    @Test
    fun `usa el primer motor y no toca los siguientes cuando todo va bien`() = runTest {
        val primary = FakeScannerEngine(
            id = ScannerEngineId.GmsCodeScanner,
            events = listOf(
                ScanEvent.Detected(
                    listOf(FakeScannerEngine.detection(ScannerEngineId.GmsCodeScanner)),
                ),
            ),
        )
        val fallback = FakeScannerEngine(ScannerEngineId.ManualInput)

        val events = FallbackScannerEngine(listOf(primary, fallback)).scan(request).toList()

        assertEquals(0, fallback.scanInvocations)
        assertEquals(ScanEvent.SessionStarted(ScannerEngineId.GmsCodeScanner), events.first())
        assertEquals(ScanEvent.SessionEnded(ScannerEngineId.GmsCodeScanner), events.last())
        assertTrue(events.none { it is ScanEvent.EngineSwitched })
    }

    @Test
    fun `cambia de motor cuando el primero falla de forma fatal`() = runTest {
        val fatal = ScanError.CameraUnavailable("la cámara está ocupada")
        val primary = FakeScannerEngine(
            id = ScannerEngineId.MlKitCameraX,
            events = listOf(ScanEvent.Failed(fatal)),
        )
        val fallback = FakeScannerEngine(
            id = ScannerEngineId.ManualInput,
            events = listOf(
                ScanEvent.Detected(
                    listOf(FakeScannerEngine.detection(ScannerEngineId.ManualInput)),
                ),
            ),
        )

        val events = FallbackScannerEngine(listOf(primary, fallback)).scan(request).toList()

        val switch = events.filterIsInstance<ScanEvent.EngineSwitched>().single()
        assertEquals(ScannerEngineId.MlKitCameraX, switch.from)
        assertEquals(ScannerEngineId.ManualInput, switch.to)
        assertEquals(fatal, switch.reason)
        assertEquals(1, fallback.scanInvocations)
        assertEquals(ScanEvent.SessionEnded(ScannerEngineId.ManualInput), events.last())
    }

    @Test
    fun `un error no fatal se propaga sin degradar de motor`() = runTest {
        val transient = ScanError.DecodeFailed("frame borroso")
        val primary = FakeScannerEngine(
            id = ScannerEngineId.MlKitCameraX,
            events = listOf(ScanEvent.Failed(transient)),
        )
        val fallback = FakeScannerEngine(ScannerEngineId.ManualInput)

        val events = FallbackScannerEngine(listOf(primary, fallback)).scan(request).toList()

        assertEquals(0, fallback.scanInvocations)
        assertEquals(transient, events.filterIsInstance<ScanEvent.Failed>().single().error)
    }

    @Test
    fun `salta los motores no disponibles sin llegar a arrancarlos`() = runTest {
        val unavailable = FakeScannerEngine(
            id = ScannerEngineId.GmsCodeScanner,
            availability = EngineAvailability.NotImplemented(plannedPhase = 2),
        )
        val usable = FakeScannerEngine(ScannerEngineId.ManualInput)

        val events = FallbackScannerEngine(listOf(unavailable, usable)).scan(request).toList()

        assertEquals(0, unavailable.scanInvocations)
        assertEquals(1, usable.scanInvocations)
        assertEquals(ScannerEngineId.ManualInput, events.filterIsInstance<ScanEvent.EngineSwitched>().single().to)
    }

    @Test
    fun `la cadena entera se comporta como una unica sesion`() = runTest {
        // Los SessionEnded de los motores internos se suprimen: desde fuera solo hay uno.
        val first = FakeScannerEngine(
            id = ScannerEngineId.MlKitCameraX,
            events = listOf(ScanEvent.Failed(ScanError.CameraUnavailable("x"))),
        )
        val second = FakeScannerEngine(
            id = ScannerEngineId.ZXingCpp,
            events = listOf(ScanEvent.Failed(ScanError.CameraUnavailable("y"))),
        )
        val third = FakeScannerEngine(ScannerEngineId.ManualInput)

        val events = FallbackScannerEngine(listOf(first, second, third)).scan(request).toList()

        assertEquals(1, events.count { it is ScanEvent.SessionEnded })
        assertEquals(2, events.count { it is ScanEvent.EngineSwitched })
    }

    @Test
    fun `si ningun motor sirve emite un fallo fatal y cierra`() = runTest {
        val engines = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                availability = EngineAvailability.Unsupported("sin Play Services"),
            ),
            FakeScannerEngine(
                id = ScannerEngineId.MlKitCameraX,
                availability = EngineAvailability.NotImplemented(plannedPhase = 2),
            ),
        )

        val events = FallbackScannerEngine(engines).scan(request).toList()

        val failure = events.filterIsInstance<ScanEvent.Failed>().single()
        assertTrue(failure.error.isFatal)
        assertTrue(events.last() is ScanEvent.SessionEnded)
    }

    @Test
    fun `la disponibilidad de la cadena es la del primer motor usable`() = runTest {
        val chain = FallbackScannerEngine(
            listOf(
                FakeScannerEngine(
                    id = ScannerEngineId.GmsCodeScanner,
                    availability = EngineAvailability.Unsupported("x"),
                ),
                FakeScannerEngine(ScannerEngineId.ManualInput),
            ),
        )

        assertEquals(EngineAvailability.Available, chain.availability())
    }
}
