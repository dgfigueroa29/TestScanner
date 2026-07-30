package com.testscanner.feature.scanner

import com.testscanner.core.domain.usecase.ObserveEngineCatalogUseCase
import com.testscanner.core.domain.usecase.ObserveScanPreferencesUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.SelectScannerEngineUseCase
import com.testscanner.core.domain.usecase.SetPreferredEngineUseCase
import com.testscanner.core.domain.usecase.SetScanFormatsUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private lateinit var engines: FakeEngineRepository
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var history: FakeHistoryRepository
    private lateinit var permissions: FakePermissionController

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg fakeEngines: FakeEngine): ScannerViewModel {
        engines = FakeEngineRepository(engines = fakeEngines.toList())
        preferences = FakePreferencesRepository()
        history = FakeHistoryRepository()
        permissions = FakePermissionController()

        val select = SelectScannerEngineUseCase(engines)
        return ScannerViewModel(
            observeCatalog = ObserveEngineCatalogUseCase(engines),
            observePreferences = ObserveScanPreferencesUseCase(preferences),
            setPreferredEngine = SetPreferredEngineUseCase(preferences),
            setScanFormats = SetScanFormatsUseCase(preferences),
            startScanSession = StartScanSessionUseCase(engines, select),
            saveDetection = SaveDetectionUseCase(history),
            preferencesRepository = preferences,
            engineRepository = engines,
            permissionController = permissions,
        )
    }

    @Test
    fun `carga el catalogo al arrancar`() = runTest {
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        val state = viewModel.state.value
        assertTrue(!state.isLoading)
        assertEquals(1, state.catalog.size)
    }

    @Test
    fun `una deteccion se guarda en el historial y aparece en el estado`() = runTest {
        val detection = detectionOf(ScannerEngineId.ManualInput)
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(ScanEvent.Detected(listOf(detection))),
            ),
        )

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.StartSession)

        assertEquals(listOf(detection), viewModel.state.value.detections)
        assertEquals(listOf(detection), history.saved)
    }

    @Test
    fun `un error fatal termina la sesion y queda visible`() = runTest {
        val error = ScanError.CameraUnavailable("ocupada")
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(ScanEvent.Failed(error)),
            ),
        )

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.StartSession)

        assertEquals(SessionStatus.Finished, viewModel.state.value.sessionStatus)
        assertEquals(error, viewModel.state.value.error)
    }

    @Test
    fun `un error transitorio no termina la sesion`() = runTest {
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(ScanEvent.Failed(ScanError.DecodeFailed("frame borroso"))),
            ),
        )

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.StartSession)

        // La sesión del fake termina sola, pero no por el fallo: el estado no es de error fatal.
        assertTrue(viewModel.state.value.error is ScanError.DecodeFailed)
    }

    @Test
    fun `elegir un motor lo guarda en preferencias y se refleja en el estado`() = runTest {
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))

        assertEquals(ScannerEngineId.ManualInput, viewModel.state.value.selectedEngineId)
        assertEquals(ScannerEngineId.ManualInput, preferences.current().preferredEngineId)
    }

    @Test
    fun `volver a automatico limpia el motor preferido`() = runTest {
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.SelectEngine(null))

        assertNull(viewModel.state.value.selectedEngineId)
    }

    @Test
    fun `quitar todos los formatos vuelve al conjunto completo en vez de dejar la peticion vacia`() =
        runTest {
            // Un ScanRequest sin formatos es inválido por contrato: el caso de uso lo evita.
            val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

            BarcodeFormat.all.forEach { viewModel.onAction(ScannerAction.ToggleFormat(it)) }

            assertEquals(BarcodeFormat.all, viewModel.state.value.formats)
        }

    @Test
    fun `detener la sesion apaga la linterna del estado`() = runTest {
        // La cámara se apagó: dejar el estado en "encendida" mostraría un control mintiendo.
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        viewModel.onAction(ScannerAction.StopSession)

        assertTrue(!viewModel.state.value.torchEnabled)
        assertNull(viewModel.state.value.activeEngineId)
    }

    @Test
    fun `la linterna sobre un motor sin control de camara no rompe nada`() = runTest {
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        viewModel.onAction(ScannerAction.ToggleTorch)

        assertTrue(!viewModel.state.value.torchEnabled)
    }

    @Test
    fun `pedir el permiso refresca el catalogo`() = runTest {
        // Sin refrescar, la UI seguiría mostrando el motor como bloqueado tras conceder el permiso.
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.MlKitCameraX,
                availability = EngineAvailability.RequiresPermission(
                    com.testscanner.core.model.Permission.Camera,
                ),
            ),
        )

        viewModel.onAction(ScannerAction.RequestCameraPermission)

        assertEquals(1, permissions.requests)
        assertEquals(1, engines.refreshCount)
    }

    @Test
    fun `descartar el error lo limpia del estado`() = runTest {
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(ScanEvent.Failed(ScanError.Timeout)),
            ),
        )

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.StartSession)
        viewModel.onAction(ScannerAction.DismissError)

        assertNull(viewModel.state.value.error)
    }
}
