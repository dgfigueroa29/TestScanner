package com.testscanner.feature.scanner

import com.testscanner.core.domain.usecase.DecodeImageUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.ScanSessions
import com.testscanner.core.domain.usecase.ScanSettings
import com.testscanner.core.domain.usecase.SelectScannerEngineUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Permission
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.platform.NoOpPlatformActions
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
            settings = ScanSettings(preferences),
            sessions = ScanSessions(
                startSession = StartScanSessionUseCase(engines, select),
                decodeImage = DecodeImageUseCase(engines, select),
                saveDetection = SaveDetectionUseCase(history),
            ),
            engineRepository = engines,
            permissionController = permissions,
            imagePicker = FakeImagePicker(),
            resultActions = ResultActionRunner(NoOpPlatformActions()),
        )
    }

    @Test
    fun `al aparecer la pantalla la sesion arranca sola`() = runTest {
        // Que un escáner exija pulsar "Escanear" para escanear es fricción que no gana nada: quien
        // abre la app ya dijo lo que quiere abriéndola. Se afirma sobre el resultado observable
        // —llegó una lectura— y no sobre el estado interno de la sesión.
        val detection = detectionOf(ScannerEngineId.MlKitCameraX)
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.MlKitCameraX,
                events = listOf(ScanEvent.Detected(listOf(detection))),
            ),
        )

        viewModel.onAction(ScannerAction.ScreenShown)

        assertEquals(listOf(detection), viewModel.state.value.detections)
    }

    @Test
    fun `sin permiso de camara la sesion no arranca sola`() = runTest {
        // Pedir la cámara sin que el usuario haya tocado nada es la forma más rápida de que la
        // deniegue para siempre. La pantalla enseña la explicación y espera a que él decida.
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.MlKitCameraX,
                availability = EngineAvailability.RequiresPermission(Permission.Camera),
                events = listOf(ScanEvent.Detected(listOf(detectionOf(ScannerEngineId.MlKitCameraX)))),
            ),
        )

        viewModel.onAction(ScannerAction.ScreenShown)

        assertTrue(viewModel.state.value.needsCameraPermission)
        assertTrue(viewModel.state.value.detections.isEmpty())
    }

    @Test
    fun `sin motor de camara no se arranca nada y se sabe por que`() = runTest {
        // Es el escritorio: hay entrada manual y decodificador de archivos, pero ninguna captura de
        // webcam. Sin esta distinción la pantalla mostraría un visor negro esperando algo que no
        // puede pasar; con ella enseña la salida que sí existe.
        val viewModel = viewModel(FakeEngine(ScannerEngineId.ManualInput))

        viewModel.onAction(ScannerAction.ScreenShown)

        assertTrue(!viewModel.state.value.hasLiveCameraEngine)
        assertTrue(viewModel.state.value.detections.isEmpty())
    }

    @Test
    fun `al dejar de verse la pantalla la sesion queda detenida`() = runTest {
        // El ViewModel sobrevive a la navegación: sin esto la cámara seguía capturando mientras el
        // usuario mira el historial o los ajustes.
        val viewModel = viewModel(FakeEngine(ScannerEngineId.MlKitCameraX))
        viewModel.onAction(ScannerAction.ScreenShown)

        viewModel.onAction(ScannerAction.ScreenHidden)

        assertEquals(SessionStatus.Idle, viewModel.state.value.sessionStatus)
        assertNull(viewModel.state.value.activeEngineId)
    }

    @Test
    fun `limpiar vacia los resultados en pantalla y no el historial`() = runTest {
        val detection = detectionOf(ScannerEngineId.ManualInput)
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(ScanEvent.Detected(listOf(detection))),
            ),
        )
        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.StartSession)

        viewModel.onAction(ScannerAction.ClearDetections)

        assertTrue(viewModel.state.value.detections.isEmpty())
        // Borrar el historial es otra acción, en otra pantalla y con otras consecuencias.
        assertEquals(listOf(detection), history.saved)
    }

    @Test
    fun `la lectura mas reciente encabeza la lista`() = runTest {
        // La hoja de resultados destaca `latestDetection`. Si el orden se invirtiera, destacaría la
        // primera lectura de la sesión en lugar de la que el usuario acaba de hacer.
        //
        // Hace falta el escaneo continuo para que lleguen dos: sin él, `RequestLimitsScannerEngine`
        // cierra la sesión tras la primera lectura, que es exactamente lo que debe hacer. La primera
        // versión de este test lo ignoraba y esperaba dos lecturas de una sesión puntual — el test
        // estaba mal, no el producto.
        val primera = detectionOf(ScannerEngineId.ManualInput, value = "primera")
        val segunda = detectionOf(ScannerEngineId.ManualInput, value = "segunda")
        val viewModel = viewModel(
            FakeEngine(
                id = ScannerEngineId.ManualInput,
                events = listOf(
                    ScanEvent.Detected(listOf(primera)),
                    ScanEvent.Detected(listOf(segunda)),
                ),
            ),
        )

        viewModel.onAction(ScannerAction.SelectEngine(ScannerEngineId.ManualInput))
        viewModel.onAction(ScannerAction.SetContinuous(true))
        viewModel.onAction(ScannerAction.StartSession)

        assertEquals(segunda, viewModel.state.value.latestDetection)
        assertEquals(listOf(segunda, primera), viewModel.state.value.detections)
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
