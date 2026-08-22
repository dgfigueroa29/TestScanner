package com.whyscan.feature.history

import app.cash.turbine.test
import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.usecase.ScanHistory
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.platform.SaveFileResult
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private class FakeHistory(initial: List<Detection>) : ScanHistoryRepository {
        private val state = MutableStateFlow(initial.map { HistoryEntry(it) })

        val entries: List<HistoryEntry> get() = state.value

        override fun observeHistory(): Flow<List<HistoryEntry>> = state.asStateFlow()

        override suspend fun save(detection: Detection) =
            state.update { listOf(HistoryEntry(detection)) + it }

        override suspend fun restore(entry: HistoryEntry) = state.update { current ->
            (current.filterNot { it.id == entry.id } + entry)
                .sortedByDescending { it.detection.detectedAtMillis }
        }

        override suspend fun setNote(detectionId: String, note: String?) = state.update { current ->
            current.map { if (it.id == detectionId) it.copy(note = note) else it }
        }

        override suspend fun delete(detectionId: String) = state.update { current ->
            current.filterNot { it.id == detectionId }
        }

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
            history = ScanHistory(repository),
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

        assertEquals(listOf(zxing), viewModel.state.value.visible.map { it.detection })
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
        assertTrue(repository.entries.isEmpty())
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
            history = ScanHistory(repository),
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
        assertEquals(listOf(mlKit), viewModel.state.value.visible.map { it.detection })
    }

    // --- Notas del usuario ---

    @Test
    fun `una nota se guarda y queda en la entrada`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "factura de marzo"))

        assertEquals("factura de marzo", repository.entries.single().note)
    }

    @Test
    fun `una nota en blanco borra la que hubiera`() = runTest {
        // Un campo de texto devuelve "" al borrarlo y espacios cuando se escapa la barra. Ninguna
        // de las dos cosas es una nota, y si se guardaran la fila diría tener una y estaría vacía.
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "algo"))
        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "   "))

        assertNull(repository.entries.single().note)
    }

    @Test
    fun `la nota se guarda sin espacios de sobra`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "  pedido 42  "))

        assertEquals("pedido 42", repository.entries.single().note)
    }

    @Test
    fun `guardar la nota cierra el campo`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.EditNote(mlKit.id))
        assertEquals(mlKit.id, viewModel.state.value.editingNoteFor)

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "algo"))
        assertNull(viewModel.state.value.editingNoteFor)
    }

    @Test
    fun `abrir la nota de otra fila cierra la anterior`() = runTest {
        // Dos campos de texto abiertos a la vez sobre una lista es escribir en el sitio equivocado.
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.EditNote(mlKit.id))
        viewModel.onAction(HistoryAction.EditNote(zxing.id))

        assertEquals(zxing.id, viewModel.state.value.editingNoteFor)
    }

    @Test
    fun `poner una nota y quitarla dicen cosas distintas`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.effects.test {
            viewModel.onAction(HistoryAction.SetNote(mlKit.id, "algo"))
            assertEquals(HistoryEffect.ShowMessage(HistoryMessage.NoteSaved), awaitItem())

            viewModel.onAction(HistoryAction.SetNote(mlKit.id, ""))
            assertEquals(HistoryEffect.ShowMessage(HistoryMessage.NoteRemoved), awaitItem())
        }
    }

    // --- Búsqueda ---

    @Test
    fun `la busqueda filtra por el valor leido`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing, manual))

        viewModel.onAction(HistoryAction.Search("b"))

        assertEquals(listOf(zxing), viewModel.state.value.visible.map { it.detection })
    }

    @Test
    fun `la busqueda encuentra tambien por la nota`() = runTest {
        // Es la mitad del sentido de poder anotar: nadie recuerda una tirada de dígitos.
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.SetNote(zxing.id, "factura de marzo"))
        viewModel.onAction(HistoryAction.Search("marzo"))

        assertEquals(listOf(zxing), viewModel.state.value.visible.map { it.detection })
    }

    @Test
    fun `la busqueda no distingue mayusculas`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "Factura"))
        viewModel.onAction(HistoryAction.Search("FACTURA"))

        assertEquals(1, viewModel.state.value.visible.size)
    }

    @Test
    fun `la busqueda se combina con el filtro de motor`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.FilterByEngine(ScannerEngineId.MlKitCameraX))
        viewModel.onAction(HistoryAction.Search("b"))

        assertTrue(viewModel.state.value.visible.isEmpty())
    }

    @Test
    fun `un historial lleno sin coincidencias no es un historial vacio`() = runTest {
        // Decir "todavía no escaneaste nada" con cien lecturas detrás es mentirle al usuario.
        val (viewModel, _) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.Search("no existe"))

        assertTrue(!viewModel.state.value.isEmpty)
        assertTrue(viewModel.state.value.isFilteredEmpty)
    }

    @Test
    fun `exportar respeta tambien la busqueda`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit, zxing, manual))

        viewModel.onAction(HistoryAction.Search("c"))
        viewModel.onAction(HistoryAction.Export(ExportFormat.Csv))

        val rows = saver.savedContent!!.trim().lines()
        assertEquals(2, rows.size, saver.savedContent!!)
    }

    @Test
    fun `la nota sale en el archivo exportado`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "pedido 42"))
        viewModel.onAction(HistoryAction.Export(ExportFormat.Csv))

        assertTrue(saver.savedContent!!.contains("pedido 42"), saver.savedContent!!)
    }

    // --- Borrado ---

    @Test
    fun `borrar una entrada deja las demas`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.Delete(mlKit.id))

        assertEquals(listOf(zxing), repository.entries.map { it.detection })
    }

    @Test
    fun `vaciar el historial pide confirmacion antes de borrar nada`() = runTest {
        // Es la única acción irreversible de la app y no hay copia en ninguna parte.
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.ConfirmClear)

        assertTrue(viewModel.state.value.isConfirmingClear)
        assertEquals(2, repository.entries.size)
    }

    @Test
    fun `cancelar la confirmacion no borra nada`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.ConfirmClear)
        viewModel.onAction(HistoryAction.DismissClear)

        assertTrue(!viewModel.state.value.isConfirmingClear)
        assertEquals(2, repository.entries.size)
    }

    // --- Deshacer un borrado (Ronda 4) ---

    @Test
    fun `deshacer devuelve la lectura borrada`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing))

        viewModel.onAction(HistoryAction.Delete(mlKit.id))
        viewModel.onAction(HistoryAction.UndoDelete)

        assertEquals(
            listOf(mlKit, zxing).sortedByDescending { it.detectedAtMillis },
            repository.entries.map { it.detection },
        )
    }

    @Test
    fun `deshacer devuelve tambien la nota`() = runTest {
        // Restituir media fila no es restituir: la nota es lo que costó escribir.
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "factura de marzo"))
        viewModel.onAction(HistoryAction.Delete(mlKit.id))
        viewModel.onAction(HistoryAction.UndoDelete)

        assertEquals("factura de marzo", repository.entries.single().note)
    }

    @Test
    fun `la lectura restituida vuelve a su sitio por fecha, no al principio`() = runTest {
        // `manual` es la más antigua de las tres. Sin ordenar, restituirla la pondría arriba.
        val (viewModel, repository) = viewModel(listOf(mlKit, zxing, manual))

        viewModel.onAction(HistoryAction.Delete(manual.id))
        viewModel.onAction(HistoryAction.UndoDelete)

        assertEquals(manual, repository.entries.last().detection)
    }

    @Test
    fun `solo el aviso del borrado ofrece deshacer`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.effects.test {
            viewModel.onAction(HistoryAction.SetNote(mlKit.id, "algo"))
            assertEquals(false, (awaitItem() as HistoryEffect.ShowMessage).undoable)

            viewModel.onAction(HistoryAction.Delete(mlKit.id))
            assertEquals(true, (awaitItem() as HistoryEffect.ShowMessage).undoable)
        }
    }

    @Test
    fun `deshacer dos veces no duplica la lectura`() = runTest {
        // El aviso puede seguir en pantalla tras el primer toque.
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.Delete(mlKit.id))
        viewModel.onAction(HistoryAction.UndoDelete)
        viewModel.onAction(HistoryAction.UndoDelete)

        assertEquals(1, repository.entries.size)
    }

    @Test
    fun `deshacer sin nada borrado no hace nada`() = runTest {
        val (viewModel, repository) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.UndoDelete)

        assertEquals(1, repository.entries.size)
    }

    // --- Búsqueda sin acentos (Ronda 4) ---

    @Test
    fun `la busqueda encuentra sin escribir la tilde`() = runTest {
        // Media gente escribe "factura" buscando lo que guardó como "Factúra", y desde un teclado
        // sin tildes no hay otra opción.
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "Factúra de línea aérea"))
        viewModel.onAction(HistoryAction.Search("factura"))

        assertEquals(1, viewModel.state.value.visible.size)
    }

    @Test
    fun `la busqueda encuentra tambien escribiendo la tilde sobre texto sin ella`() = runTest {
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "factura"))
        viewModel.onAction(HistoryAction.Search("factúra"))

        assertEquals(1, viewModel.state.value.visible.size)
    }

    @Test
    fun `la enie no se confunde con la ene`() = runTest {
        // En español es una letra distinta, no una `n` con adorno. Que "ano" encontrara "año" sería
        // desconcertante en el mejor de los casos.
        val (viewModel, _) = viewModel(listOf(mlKit))

        viewModel.onAction(HistoryAction.SetNote(mlKit.id, "año fiscal"))
        viewModel.onAction(HistoryAction.Search("ano"))

        assertTrue(viewModel.state.value.visible.isEmpty())
    }
}
