package com.whyscan.core.domain.scan

import com.whyscan.core.domain.FakeScannerEngine
import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.ScanEvent
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
    fun `los fallos se atribuyen a su motor y se separan por gravedad`() {
        val scoreboard = EngineScoreboard.Empty
            .reduce(
                ScanEvent.Failed(ScanError.DecodeFailed("frame borroso"), ScannerEngineId.ZXingCpp),
            )
            .reduce(
                ScanEvent.Failed(ScanError.CameraUnavailable("ocupada"), ScannerEngineId.ZXingCpp),
            )

        val metrics = scoreboard[ScannerEngineId.ZXingCpp]!!
        assertEquals(1, metrics.transientFailures)
        assertEquals(1, metrics.fatalFailures)
    }

    @Test
    fun `un fallo sin motor no se reparte entre los participantes`() {
        // Que venza el plazo de la sesión no es culpa de ningún motor en particular.
        val scoreboard = EngineScoreboard.Empty
            .reduce(detected(ScannerEngineId.ZXingCpp, "a", 10))
            .reduce(ScanEvent.Failed(ScanError.Timeout))

        assertEquals(0, scoreboard[ScannerEngineId.ZXingCpp]!!.fatalFailures)
    }

    @Test
    fun `los frames analizados se cuentan por motor`() {
        val scoreboard = EngineScoreboard.Empty
            .reduce(ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 1))
            .reduce(ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 2))
            .reduce(ScanEvent.FrameAnalyzed(ScannerEngineId.ZXingCpp, 3))

        assertEquals(2, scoreboard[ScannerEngineId.MlKitCameraX]!!.framesAnalyzed)
        assertEquals(1, scoreboard[ScannerEngineId.ZXingCpp]!!.framesAnalyzed)
    }

    @Test
    fun `frames por lectura mide la eficiencia de cada motor`() {
        // Dos motores que leen lo mismo no son iguales si uno necesita 30 veces más frames.
        val scoreboard = listOf(
            ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 1),
            ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 2),
            ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 3),
            ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 4),
            detected(ScannerEngineId.MlKitCameraX, "a", 10),
        ).fold(EngineScoreboard.Empty) { acc, event -> acc.reduce(event) }

        assertEquals(4, scoreboard[ScannerEngineId.MlKitCameraX]!!.framesPerDetection)
    }

    @Test
    fun `sin lecturas no hay frames por lectura que calcular`() {
        val scoreboard = EngineScoreboard.Empty
            .reduce(ScanEvent.FrameAnalyzed(ScannerEngineId.ZXingCpp, 1))

        assertNull(scoreboard[ScannerEngineId.ZXingCpp]!!.framesPerDetection)
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
