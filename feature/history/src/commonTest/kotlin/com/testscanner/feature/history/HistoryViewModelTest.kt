package com.testscanner.feature.history

import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.usecase.ClearScanHistoryUseCase
import com.testscanner.core.domain.usecase.ObserveScanHistoryUseCase
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.platform.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
class HistoryViewModelTest {

    private class FakeHistory(initial: List<Detection>) : ScanHistoryRepository {
        private val state = MutableStateFlow(initial)
        override fun observeHistory(): Flow<List<Detection>> = state.asStateFlow()
        override suspend fun save(detection: Detection) = state.update { listOf(detection) + it }
        override suspend fun findById(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun clear() {
            state.value = emptyList()
        }
    }

    private fun detection(engineId: ScannerEngineId, value: String, at: Long) = Detection.of(
        barcode = Barcode(rawValue = value, format = BarcodeFormat.QrCode),
        engineId = engineId,
        detectedAtMillis = at,
    )

    private val mlKit = detection(ScannerEngineId.MlKitCameraX, "a", 3)
    private val zxing = detection(ScannerEngineId.ZXingCpp, "b", 2)
    private val manual = detection(ScannerEngineId.ManualInput, "c", 1)

    /** Registra qué se pidió hacer con un resultado guardado, para poder afirmarlo (RF-13). */
    private class RecordingActions(
        override val canShare: Boolean = true,
        private val succeeds: Boolean = true,
    ) : PlatformActions {
        val copied = mutableListOf<String>()
        val shared = mutableListOf<String>()
        val opened = mutableListOf<String>()

        override suspend fun copyToClipboard(text: String): Boolean {
            copied += text
            return succeeds
        }

        override suspend fun share(text: String): Boolean {
            shared += text
            return succeeds
        }

        override suspend fun openUrl(url: String): Boolean {
            opened += url
            return succeeds
        }
    }

    private lateinit var actions: RecordingActions

    private fun viewModel(items: List<Detection>): Pair<HistoryViewModel, FakeHistory> {
        val repository = FakeHistory(items)
        actions = RecordingActions()
        return HistoryViewModel(
            observeHistory = ObserveScanHistoryUseCase(repository),
            clearHistory = ClearScanHistoryUseCase(repository),
            platformActions = actions,
        ) to repository
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `carga el historial`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing, manual))

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertEquals(3, state.visible.size)
    }

    @Test
    fun `un historial vacio se distingue de uno que aun esta cargando`() = runTest {
        val (viewModel, _) = viewModel(emptyList())

        assertTrue(viewModel.state.value.isEmpty)
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `filtrar por motor deja solo sus detecciones`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing, manual))

        viewModel.onAction(HistoryAction.FilterByEngine(ScannerEngineId.ZXingCpp))

        assertEquals(listOf(zxing), viewModel.state.value.visible)
    }

    @Test
    fun `quitar el filtro devuelve todo`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.FilterByEngine(ScannerEngineId.ZXingCpp))
        viewModel.onAction(HistoryAction.FilterByEngine(null))

        assertEquals(2, viewModel.state.value.visible.size)
    }

    @Test
    fun `solo se ofrecen filtros de motores presentes en el historial`() = runTest {
        // Ofrecer un filtro que no devuelve nada es una promesa vacía para el usuario.
        val (viewModel, _) = viewModel(listOf(mlKit, manual))

        assertEquals(
            listOf(ScannerEngineId.MlKitCameraX, ScannerEngineId.ManualInput).sortedBy { it.id },
            viewModel.state.value.presentEngines,
        )
    }

    @Test
    fun `borrar vacia el almacen y el estado`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.Clear)

        assertTrue(viewModel.state.value.isEmpty)
        assertEquals(null, repository.findById(mlKit.id))
    }

    @Test
    fun `copiar una deteccion guardada la manda al portapapeles`() = runTest {
        // El motivo principal para volver al historial: pegar en otro lado algo que ya se escaneó.
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.RunResultAction(mlKit, ResultAction.Copy))

        assertEquals(listOf("a"), actions.copied)
    }

    @Test
    fun `el estado refleja si la plataforma sabe compartir`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        assertTrue(viewModel.state.value.canShare)
    }

    @Test
    fun `el filtro sobrevive a una actualizacion del historial`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.FilterByEngine(ScannerEngineId.MlKitCameraX))
        repository.save(detection(ScannerEngineId.ZXingCpp, "nuevo", 9))

        assertEquals(ScannerEngineId.MlKitCameraX, viewModel.state.value.engineFilter)
        assertEquals(listOf(mlKit), viewModel.state.value.visible)
    }
}
