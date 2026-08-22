package com.whyscan.feature.scanner

import app.cash.turbine.test
import com.whyscan.core.domain.scan.OpenKind
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.usecase.DecodeImageUseCase
import com.whyscan.core.domain.usecase.SaveDetectionUseCase
import com.whyscan.core.domain.usecase.ScanSessions
import com.whyscan.core.domain.usecase.ScanSettings
import com.whyscan.core.domain.usecase.SelectScannerEngineUseCase
import com.whyscan.core.domain.usecase.StartScanSessionUseCase
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.BarcodeValueType
import com.whyscan.core.model.Detection
import com.whyscan.core.model.ScannerEngineId
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

/** RF-13 de punta a punta: el dominio decide qué se puede hacer y la plataforma lo ejecuta. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultActionsIntegrationTest {

    private lateinit var platform: FakePlatformActions

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(canShare: Boolean = true, succeeds: Boolean = true): ScannerViewModel {
        val engines = FakeEngineRepository(engines = listOf(FakeEngine(ScannerEngineId.ManualInput)))
        val preferences = FakePreferencesRepository()
        platform = FakePlatformActions(canShare = canShare, succeeds = succeeds)

        val select = SelectScannerEngineUseCase(engines)
        return ScannerViewModel(
            settings = ScanSettings(preferences),
            sessions = ScanSessions(
                startSession = StartScanSessionUseCase(engines, select),
                decodeImage = DecodeImageUseCase(engines, select),
                saveDetection = SaveDetectionUseCase(FakeHistoryRepository()),
            ),
            engineRepository = engines,
            permissionController = FakePermissionController(),
            imagePicker = FakeImagePicker(),
            resultActions = ResultActionRunner(platform),
        )
    }

    private fun urlDetection() = Detection.of(
        barcode = Barcode(
            rawValue = "https://ejemplo.com",
            format = BarcodeFormat.QrCode,
            valueType = BarcodeValueType.Url("https://ejemplo.com"),
        ),
        engineId = ScannerEngineId.ManualInput,
        detectedAtMillis = 1,
    )

    @Test
    fun `copiar manda el valor al portapapeles`() = runTest {
        val viewModel = viewModel()
        val detection = urlDetection()

        viewModel.onAction(ScannerAction.RunResultAction(ResultAction.Copy, detection.barcode.rawValue))

        assertEquals(listOf("https://ejemplo.com"), platform.copied)
    }

    @Test
    fun `copia exactamente el texto que le da la pantalla`() = runTest {
        // Redactarlo es cosa de la UI (D15); el ViewModel no lo reinterpreta ni lo recorta.
        val viewModel = viewModel()

        viewModel.onAction(ScannerAction.RunResultAction(ResultAction.Copy, "Red: MiRed · Clave: clave"))

        assertEquals(listOf("Red: MiRed · Clave: clave"), platform.copied)
    }

    @Test
    fun `abrir usa el destino que decidio el dominio`() = runTest {
        val viewModel = viewModel()
        val detection = urlDetection()
        val open = ResultAction.Open("https://ejemplo.com", OpenKind.Link)

        viewModel.onAction(ScannerAction.RunResultAction(open, detection.barcode.rawValue))

        assertEquals(listOf("https://ejemplo.com"), platform.opened)
    }

    @Test
    fun `copiar confirma con un mensaje, porque no se ve nada`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onAction(ScannerAction.RunResultAction(ResultAction.Copy, "lo que sea"))
            assertEquals(ScannerEffect.ShowMessage(ScannerMessage.Copied), awaitItem())
        }
    }

    @Test
    fun `una accion fallida avisa`() = runTest {
        val viewModel = viewModel(succeeds = false)

        viewModel.effects.test {
            val open = ResultAction.Open("algo://raro", OpenKind.Link)
            viewModel.onAction(ScannerAction.RunResultAction(open, "lo que sea"))
            assertEquals(ScannerEffect.ShowMessage(ScannerMessage.OpenFailed), awaitItem())
        }
    }

    @Test
    fun `el estado refleja si la plataforma sabe compartir`() = runTest {
        assertTrue(viewModel(canShare = true).state.value.canShare)
        assertTrue(!viewModel(canShare = false).state.value.canShare)
    }
}
