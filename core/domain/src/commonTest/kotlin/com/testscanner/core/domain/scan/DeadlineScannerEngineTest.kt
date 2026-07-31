package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadlineScannerEngineTest {

    /** Motor que se queda abierto sin detectar nada, como una cámara apuntando a la pared. */
    private class NeverDetectingEngine(
        override val id: ScannerEngineId = ScannerEngineId.MlKitCameraX,
    ) : BarcodeScannerEngine {

        var released = false
            private set

        override val descriptor: ScannerEngineDescriptor =
            FakeScannerEngine(id).descriptor

        override suspend fun availability(): EngineAvailability = EngineAvailability.Available

        override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
            emit(ScanEvent.SessionStarted(id))
            try {
                delay(Long.MAX_VALUE)
            } finally {
                released = true
            }
        }
    }

    @Test
    fun `sin plazo la sesion pasa intacta`() = runTest {
        val engine = FakeScannerEngine(ScannerEngineId.ManualInput)

        val events = DeadlineScannerEngine(engine).scan(ScanRequest()).toList()

        assertEquals(ScanEvent.SessionStarted(ScannerEngineId.ManualInput), events.first())
        assertEquals(ScanEvent.SessionEnded(ScannerEngineId.ManualInput), events.last())
        assertTrue(events.none { it is ScanEvent.Failed })
    }

    @Test
    fun `vencido el plazo la sesion termina con Timeout`() = runTest {
        val engine = NeverDetectingEngine()
        val request = ScanRequest(timeoutMillis = 5_000)

        val events = DeadlineScannerEngine(engine).scan(request).toList()

        assertEquals(ScanError.Timeout, events.filterIsInstance<ScanEvent.Failed>().single().error)
        assertTrue(events.last() is ScanEvent.SessionEnded)
    }

    @Test
    fun `al vencer el plazo se libera el motor`() = runTest {
        // Sin esto la cámara quedaría encendida después de que el usuario ya vio "se agotó".
        val engine = NeverDetectingEngine()

        DeadlineScannerEngine(engine).scan(ScanRequest(timeoutMillis = 5_000)).toList()

        assertTrue(engine.released)
    }

    @Test
    fun `una sesion que termina antes del plazo no dispara el Timeout`() = runTest {
        val engine = FakeScannerEngine(
            id = ScannerEngineId.ManualInput,
            events = listOf(
                ScanEvent.Detected(
                    listOf(FakeScannerEngine.detection(ScannerEngineId.ManualInput)),
                ),
            ),
        )

        val events = DeadlineScannerEngine(engine)
            .scan(ScanRequest(timeoutMillis = 60_000))
            .toList()

        assertTrue(events.none { it is ScanEvent.Failed })
        assertEquals(1, events.count { it is ScanEvent.SessionEnded })
    }

    @Test
    fun `el Timeout es fatal, para que la cadena de fallback no lo trate como transitorio`() {
        assertTrue(ScanError.Timeout.isFatal)
    }

    @Test
    fun `el decorador no altera la identidad del motor`() = runTest {
        val engine = FakeScannerEngine(ScannerEngineId.ZXingCpp)
        val decorated = DeadlineScannerEngine(engine)

        assertEquals(engine.id, decorated.id)
        assertEquals(engine.descriptor, decorated.descriptor)
        assertEquals(engine.availability(), decorated.availability())
    }
}
