package com.whyscan.feature.scanner.comparison

import com.whyscan.core.domain.usecase.SelectScannerEngineUseCase
import com.whyscan.core.domain.usecase.StartComparisonUseCase
import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.feature.scanner.FakeEngine
import com.whyscan.feature.scanner.FakeEngineRepository
import com.whyscan.feature.scanner.FakePreferencesRepository
import com.whyscan.feature.scanner.detectionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg engines: FakeEngine): ComparisonViewModel {
        val repository = FakeEngineRepository(engines = engines.toList())
        return ComparisonViewModel(
            startComparison = StartComparisonUseCase(
                repository,
                SelectScannerEngineUseCase(repository),
            ),
            preferencesRepository = FakePreferencesRepository(),
        )
    }

    private fun detecting(id: ScannerEngineId, value: String, latency: Long) = FakeEngine(
        id = id,
        events = listOf(
            ScanEvent.Detected(
                listOf(detectionOf(id, value = value).copy(latencyMillis = latency)),
            ),
        ),
    )

    @Test
    fun `con un solo motor avisa en lugar de comparar consigo mismo`() = runTest {
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        assertTrue(viewModel.state.value.notEnoughEngines)
    }

    @Test
    fun `con dos motores de camara lista los participantes`() = runTest {
        val viewModel = viewModel(
            FakeEngine(ScannerEngineId.MlKitCameraX),
            FakeEngine(ScannerEngineId.ZXingCpp),
        )

        assertTrue(!viewModel.state.value.notEnoughEngines)
        assertEquals(2, viewModel.state.value.participants.size)
    }

    @Test
    fun `la entrada manual no participa en una comparacion de camara`() = runTest {
        // No es un decodificador: contrastarlo contra un motor de cámara no mide nada.
        val viewModel = viewModel(
            FakeEngine(ScannerEngineId.MlKitCameraX),
            FakeEngine(ScannerEngineId.ManualInput),
        )

        assertTrue(viewModel.state.value.notEnoughEngines)
    }

    @Test
    fun `un motor one-shot como el escaner del sistema si participa`() = runTest {
        // Exigir escaneo continuo lo dejaría fuera, y es el motor más interesante de contrastar.
        val viewModel = viewModel(
            FakeEngine(ScannerEngineId.GmsCodeScanner),
            FakeEngine(ScannerEngineId.MlKitCameraX),
        )

        assertTrue(!viewModel.state.value.notEnoughEngines)
        assertTrue(ScannerEngineId.GmsCodeScanner in viewModel.state.value.participants)
    }

    @Test
    fun `un motor no disponible no cuenta como participante`() = runTest {
        val viewModel = viewModel(
            FakeEngine(ScannerEngineId.MlKitCameraX),
            FakeEngine(
                id = ScannerEngineId.GmsCodeScanner,
                availability = EngineAvailability.NotImplemented(plannedPhase = 2),
            ),
        )

        assertTrue(viewModel.state.value.notEnoughEngines)
    }

    @Test
    fun `el marcador acumula las lecturas de cada motor por separado`() = runTest {
        val viewModel = viewModel(
            detecting(ScannerEngineId.MlKitCameraX, "codigo", latency = 300),
            detecting(ScannerEngineId.ZXingCpp, "codigo", latency = 40),
        )

        viewModel.onAction(ComparisonAction.Start)

        val state = viewModel.state.value
        assertTrue(state.hasResults)
        assertEquals(1, state.scoreboard[ScannerEngineId.MlKitCameraX]?.detections)
        assertEquals(1, state.scoreboard[ScannerEngineId.ZXingCpp]?.detections)
    }

    @Test
    fun `el lider es el mas rapido cuando ambos leen lo mismo`() = runTest {
        // Es la pregunta que el producto existe para responder (G5).
        val viewModel = viewModel(
            detecting(ScannerEngineId.MlKitCameraX, "codigo", latency = 300),
            detecting(ScannerEngineId.ZXingCpp, "codigo", latency = 40),
        )

        viewModel.onAction(ComparisonAction.Start)

        assertEquals(ScannerEngineId.ZXingCpp, viewModel.state.value.leader?.engineId)
    }

    @Test
    fun `los frames y los fallos llegan al marcador atribuidos a su motor`() = runTest {
        // Antes de que ScanEvent llevara el motor, estos contadores quedaban siempre en cero
        // porque en un stream fusionado no había forma de saber de quién venía cada evento.
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.MlKitCameraX,
                events = listOf(
                    ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 1),
                    ScanEvent.FrameAnalyzed(ScannerEngineId.MlKitCameraX, 2),
                    ScanEvent.Failed(
                        ScanError.DecodeFailed("frame borroso"),
                        ScannerEngineId.MlKitCameraX,
                    ),
                ),
            ),
            FakeEngine(
                id = ScannerEngineId.ZXingCpp,
                events = listOf(ScanEvent.FrameAnalyzed(ScannerEngineId.ZXingCpp, 1)),
            ),
        )

        viewModel.onAction(ComparisonAction.Start)

        val scoreboard = viewModel.state.value.scoreboard
        assertEquals(2, scoreboard[ScannerEngineId.MlKitCameraX]?.framesAnalyzed)
        assertEquals(1, scoreboard[ScannerEngineId.MlKitCameraX]?.transientFailures)
        assertEquals(1, scoreboard[ScannerEngineId.ZXingCpp]?.framesAnalyzed)
        assertEquals(0, scoreboard[ScannerEngineId.ZXingCpp]?.transientFailures)
    }

    @Test
    fun `un fallo transitorio no detiene la comparacion`() = runTest {
        // Que un motor pierda un frame no invalida lo que están midiendo los demás.
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.MlKitCameraX,
                events = listOf(
                    ScanEvent.Failed(
                        ScanError.DecodeFailed("borroso"),
                        ScannerEngineId.MlKitCameraX,
                    ),
                ),
            ),
            FakeEngine(ScannerEngineId.ZXingCpp),
        )

        viewModel.onAction(ComparisonAction.Start)

        assertEquals(1, viewModel.state.value.scoreboard[ScannerEngineId.MlKitCameraX]?.transientFailures)
    }

    @Test
    fun `reiniciar vacia el marcador`() = runTest {
        val viewModel = viewModel(
            detecting(ScannerEngineId.MlKitCameraX, "a", latency = 10),
            detecting(ScannerEngineId.ZXingCpp, "b", latency = 20),
        )

        viewModel.onAction(ComparisonAction.Start)
        viewModel.onAction(ComparisonAction.Reset)

        assertTrue(!viewModel.state.value.hasResults)
        assertTrue(!viewModel.state.value.isRunning)
    }

    @Test
    fun `detener corta la sesion`() = runTest {
        val viewModel = viewModel(
            FakeEngine(ScannerEngineId.MlKitCameraX),
            FakeEngine(ScannerEngineId.ZXingCpp),
        )

        viewModel.onAction(ComparisonAction.Start)
        viewModel.onAction(ComparisonAction.Stop)

        assertTrue(!viewModel.state.value.isRunning)
    }
}
