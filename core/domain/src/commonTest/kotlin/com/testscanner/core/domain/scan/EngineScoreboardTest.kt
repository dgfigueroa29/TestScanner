package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.ScanEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineScoreboardTest {

    private fun detected(id: ScannerEngineId, value: String, latency: Long?) =
        ScanEvent.Detected(
            listOf(FakeScannerEngine.detection(id, value = value).copy(latencyMillis = latency)),
        )

    @Test
    fun `acumula detecciones y latencia media por motor`() {
        val scoreboard = listOf(
            detected(ScannerEngineId.MlKitCameraX, "a", 100),
            detected(ScannerEngineId.MlKitCameraX, "b", 200),
            detected(ScannerEngineId.ZXingCpp, "a", 500),
        ).fold(EngineScoreboard.Empty) { acc, event -> acc.reduce(event) }

        val mlKit = scoreboard[ScannerEngineId.MlKitCameraX]!!
        assertEquals(2, mlKit.detections)
        assertEquals(2, mlKit.uniqueValues)
        assertEquals(150L, mlKit.averageLatencyMillis)
        assertEquals(100L, mlKit.firstDetectionLatencyMillis)

        assertEquals(1, scoreboard[ScannerEngineId.ZXingCpp]!!.detections)
    }

    @Test
    fun `leer dos veces el mismo codigo no cuenta como dos valores distintos`() {
        val scoreboard = listOf(
            detected(ScannerEngineId.ZXingCpp, "mismo", 10),
            detected(ScannerEngineId.ZXingCpp, "mismo", 12),
        ).fold(EngineScoreboard.Empty) { acc, event -> acc.reduce(event) }

        val metrics = scoreboard[ScannerEngineId.ZXingCpp]!!
        assertEquals(2, metrics.detections)
        assertEquals(1, metrics.uniqueValues)
    }

    @Test
    fun `el lider es quien lee mas codigos distintos y a igualdad el mas rapido`() {
        val scoreboard = listOf(
            detected(ScannerEngineId.MlKitCameraX, "a", 300),
            detected(ScannerEngineId.ZXingCpp, "a", 50),
        ).fold(EngineScoreboard.Empty) { acc, event -> acc.reduce(event) }

        assertEquals(ScannerEngineId.ZXingCpp, scoreboard.leader?.engineId)
    }

    @Test
    fun `los fallos se atribuyen explicitamente y se separan por gravedad`() {
        val scoreboard = EngineScoreboard.Empty
            .reduce(
                ScanEvent.Failed(ScanError.DecodeFailed("frame borroso")),
                attributedTo = ScannerEngineId.ZXingCpp,
            )
            .reduce(
                ScanEvent.Failed(ScanError.CameraUnavailable("ocupada")),
                attributedTo = ScannerEngineId.ZXingCpp,
            )

        val metrics = scoreboard[ScannerEngineId.ZXingCpp]!!
        assertEquals(1, metrics.transientFailures)
        assertEquals(1, metrics.fatalFailures)
    }

    @Test
    fun `un motor sin latencia reportada no falsea la media`() {
        val scoreboard = EngineScoreboard.Empty
            .reduce(detected(ScannerEngineId.ManualInput, "a", null))

        assertNull(scoreboard[ScannerEngineId.ManualInput]!!.averageLatencyMillis)
    }

    @Test
    fun `un marcador vacio no tiene lider`() {
        assertNull(EngineScoreboard.Empty.leader)
    }
}
