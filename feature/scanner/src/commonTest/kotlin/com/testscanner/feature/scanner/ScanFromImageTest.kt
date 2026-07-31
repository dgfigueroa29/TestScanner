package com.testscanner.feature.scanner

import app.cash.turbine.test
import com.testscanner.core.domain.usecase.DecodeImageUseCase
import com.testscanner.core.domain.usecase.ObserveEngineCatalogUseCase
import com.testscanner.core.domain.usecase.ObserveScanPreferencesUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.SelectScannerEngineUseCase
import com.testscanner.core.domain.usecase.SetPreferredEngineUseCase
import com.testscanner.core.domain.usecase.SetScanFormatsUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.BarcodeValueType
import com.testscanner.core.model.Permission
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.platform.NoOpPlatformActions
import com.testscanner.core.platform.PickImageResult
import com.testscanner.core.scanner.EngineAvailability
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

/** RF-07 de punta a punta: elegir una imagen, decodificarla y guardar lo que se leyó. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanFromImageTest {

    private lateinit var history: FakeHistoryRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val image = ScanImage(encoded = byteArrayOf(9), mimeType = "image/jpeg")

    private fun viewModel(
        picked: PickImageResult,
        decoded: Result<List<Barcode>>? = null,
        engineId: ScannerEngineId = ScannerEngineId.MlKitCameraX,
        availability: EngineAvailability = EngineAvailability.Available,
    ): ScannerViewModel {
        val engines = FakeEngineRepository(
            engines = listOf(FakeEngine(id = engineId, availability = availability, decoded = decoded)),
        )
        val preferences = FakePreferencesRepository()
        history = FakeHistoryRepository()
        val select = SelectScannerEngineUseCase(engines)

        return ScannerViewModel(
            observeCatalog = ObserveEngineCatalogUseCase(engines),
            observePreferences = ObserveScanPreferencesUseCase(preferences),
            setPreferredEngine = SetPreferredEngineUseCase(preferences),
            setScanFormats = SetScanFormatsUseCase(preferences),
            startScanSession = StartScanSessionUseCase(engines, select),
            saveDetection = SaveDetectionUseCase(history),
            decodeImage = DecodeImageUseCase(engines, select),
            preferencesRepository = preferences,
            engineRepository = engines,
            permissionController = FakePermissionController(),
            platformActions = NoOpPlatformActions(),
            imagePicker = FakeImagePicker(picked),
        )
    }

    @Test
    fun `escanear una imagen deja las detecciones en el estado y en el historial`() = runTest {
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.success(listOf(Barcode("7501234567893", BarcodeFormat.Ean13))),
        )

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertEquals(1, viewModel.state.value.detections.size)
        assertEquals("7501234567893", history.saved.single().barcode.rawValue)
    }

    @Test
    fun `la deteccion queda atribuida al motor que decodifico`() = runTest {
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
        )

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertEquals(ScannerEngineId.MlKitCameraX, viewModel.state.value.detections.single().engineId)
        assertEquals(ScannerEngineId.MlKitCameraX, viewModel.state.value.activeEngineId)
    }

    @Test
    fun `un codigo leido de una foto se interpreta igual que uno de la camara`() = runTest {
        // Si no, un QR con una URL escaneado desde imagen no ofrecería "Abrir enlace" (RF-13).
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.success(listOf(Barcode("https://ejemplo.com", BarcodeFormat.QrCode))),
        )

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertTrue(viewModel.state.value.detections.single().barcode.valueType is BarcodeValueType.Url)
    }

    @Test
    fun `cancelar el selector no dice nada ni cambia el estado`() = runTest {
        // Cancelar es la salida más frecuente de un selector: tratarla como error sería ruido.
        val viewModel = viewModel(picked = PickImageResult.Cancelled)

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertTrue(viewModel.state.value.detections.isEmpty())
        assertTrue(!viewModel.state.value.isDecodingImage)
    }

    @Test
    fun `una imagen sin codigo lo dice en vez de fallar`() = runTest {
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.success(emptyList()),
        )

        viewModel.effects.test {
            viewModel.onAction(ScannerAction.ScanFromImage)
            assertEquals(ScannerEffect.ShowMessage(ScannerMessage.NoCodeInImage), awaitItem())
        }
    }

    @Test
    fun `si el selector falla se avisa con su motivo`() = runTest {
        val viewModel = viewModel(picked = PickImageResult.Failed("No se pudo leer el archivo"))

        viewModel.effects.test {
            viewModel.onAction(ScannerAction.ScanFromImage)
            assertEquals(
                ScannerEffect.ShowMessage(ScannerMessage.Raw("No se pudo leer el archivo")),
                awaitItem(),
            )
        }
    }

    @Test
    fun `el indicador de progreso se apaga aunque la decodificacion falle`() = runTest {
        // Sin el `finally`, un fallo dejaría el botón deshabilitado para siempre.
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.failure(RuntimeException("imagen corrupta")),
        )

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertTrue(!viewModel.state.value.isDecodingImage)
    }

    @Test
    fun `no se ofrece escanear desde imagen si ningun motor disponible lo soporta`() = runTest {
        // La entrada manual no lee imágenes; sin esta comprobación el botón abriría un selector
        // cuyo resultado nadie sabría decodificar.
        val viewModel = viewModel(
            picked = PickImageResult.Cancelled,
            engineId = ScannerEngineId.ManualInput,
        )

        assertTrue(!viewModel.state.value.canScanFromImage)
    }

    @Test
    fun `se ofrece si el motor disponible declara la fuente`() = runTest {
        val viewModel = viewModel(picked = PickImageResult.Cancelled)

        assertTrue(viewModel.state.value.canScanFromImage)
    }

    @Test
    fun `sin permiso de camara se sigue pudiendo escanear desde imagen`() = runTest {
        // Es el momento en que la foto es la única salida. Si el permiso de cámara ocultara también
        // esta opción, la app se cerraría sobre sí misma justo cuando el usuario necesita otra vía.
        val viewModel = viewModel(
            picked = PickImageResult.Picked(image),
            decoded = Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
            availability = EngineAvailability.RequiresPermission(Permission.Camera),
        )

        assertTrue(viewModel.state.value.canScanFromImage)

        viewModel.onAction(ScannerAction.ScanFromImage)

        assertEquals(1, viewModel.state.value.detections.size)
    }

    @Test
    fun `un motor con el modelo sin descargar si queda fuera`() = runTest {
        // A diferencia del permiso de cámara, aquí no hay nada que ejecutar: la excepción es
        // estrecha a propósito.
        val viewModel = viewModel(
            picked = PickImageResult.Cancelled,
            availability = EngineAvailability.RequiresDownload(),
        )

        assertTrue(!viewModel.state.value.canScanFromImage)
    }
}
