package com.testscanner.feature.history

import app.cash.turbine.test
import com.testscanner.core.domain.export.ExportFormat
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.usecase.ClearScanHistoryUseCase
import com.testscanner.core.domain.usecase.ObserveScanHistoryUseCase
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.platform.FileSaver
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.platform.SaveFileResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    /** Guarda en memoria lo que se le pida escribir, para poder afirmar sobre el archivo. */
    private class RecordingSaver(
        private val result: (String) -> SaveFileResult = { SaveFileResult.Saved("/tmp/$it") },
    ) : FileSaver {
        var savedName: String? = null
            private set
        var savedMimeType: String? = null
            private set
        var savedContent: String? = null
            private set

        override suspend fun save(
            suggestedName: String,
            mimeType: String,
            content: String,
        ): SaveFileResult {
            savedName = suggestedName
            savedMimeType = mimeType
            savedContent = content
            return result(suggestedName)
        }
    }

    private lateinit var actions: RecordingActions

    private lateinit var saver: RecordingSaver

    private fun viewModel(items: List<Detection>): Pair<HistoryViewModel, FakeHistory> {
        val repository = FakeHistory(items)
        actions = RecordingActions()
        saver = RecordingSaver()
        return HistoryViewModel(
            observeHistory = ObserveScanHistoryUseCase(repository),
            clearHistory = ClearScanHistoryUseCase(repository),
            platformActions = actions,
            fileSaver = saver,
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

        viewModel.onAction(HistoryAction.RunResultAction(ResultAction.Copy, "a"))

        assertEquals(listOf("a"), actions.copied)
    }

    @Test
    fun `el estado refleja si la plataforma sabe compartir`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        assertTrue(viewModel.state.value.canShare)
    }

    @Test
    fun `exportar produce un archivo con el tipo MIME del formato`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.Export(ExportFormat.Csv))

        assertEquals("historial-escaneos.csv", saver.savedName)
        assertEquals("text/csv", saver.savedMimeType)
        assertTrue(saver.savedContent!!.startsWith("value,format,engine"), saver.savedContent!!)
    }

    @Test
    fun `exportar respeta el filtro que se esta viendo`() = runTest {
        // Un archivo que no se parece a la pantalla que el usuario tiene delante es una sorpresa.
        val (viewModel, _) = viewModel(listOf(mlKit, zxing, manual))

        viewModel.onAction(HistoryAction.FilterByEngine(ScannerEngineId.ZXingCpp))
        viewModel.onAction(HistoryAction.Export(ExportFormat.Csv))

        val rows = saver.savedContent!!.trim().lines()
        assertEquals(2, rows.size, saver.savedContent!!)
        assertTrue(rows[1].contains("zxing_cpp"), rows[1])
    }

    @Test
    fun `no se exporta un historial vacio`() = runTest {
        val (viewModel, _) = viewModel(emptyList())

        viewModel.effects.test {
            viewModel.onAction(HistoryAction.Export(ExportFormat.Json))
            assertEquals(HistoryEffect.ShowMessage(HistoryMessage.NothingToExport), awaitItem())
        }
        assertEquals(null, saver.savedContent)
    }

    @Test
    fun `cancelar la exportacion no dice nada`() = runTest {
        val repository = FakeHistory(listOf(mlKit))
        actions = RecordingActions()
        saver = RecordingSaver { SaveFileResult.Cancelled }
        val viewModel = HistoryViewModel(
            observeHistory = ObserveScanHistoryUseCase(repository),
            clearHistory = ClearScanHistoryUseCase(repository),
            platformActions = actions,
            fileSaver = saver,
        )

        viewModel.onAction(HistoryAction.Export(ExportFormat.Csv))

        assertTrue(!viewModel.state.value.isExporting)
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
