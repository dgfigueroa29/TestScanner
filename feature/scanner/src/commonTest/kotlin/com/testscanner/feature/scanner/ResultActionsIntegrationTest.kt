package com.testscanner.feature.scanner

import app.cash.turbine.test
import com.testscanner.core.domain.scan.ResultAction
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
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId
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

        return ScannerViewModel(
            observeCatalog = ObserveEngineCatalogUseCase(engines),
            observePreferences = ObserveScanPreferencesUseCase(preferences),
            setPreferredEngine = SetPreferredEngineUseCase(preferences),
            setScanFormats = SetScanFormatsUseCase(preferences),
            startScanSession = StartScanSessionUseCase(engines, SelectScannerEngineUseCase(engines)),
            saveDetection = SaveDetectionUseCase(FakeHistoryRepository()),
            preferencesRepository = preferences,
            engineRepository = engines,
            permissionController = FakePermissionController(),
            platformActions = platform,
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

    private fun wifiDetection() = Detection.of(
        barcode = Barcode(
            rawValue = "WIFI:T:WPA;S:MiRed;P:clave;;",
            format = BarcodeFormat.QrCode,
            valueType = BarcodeValueType.Wifi(
                ssid = "MiRed",
                password = "clave",
                encryption = BarcodeValueType.WifiEncryption.WPA,
            ),
        ),
        engineId = ScannerEngineId.ManualInput,
        detectedAtMillis = 1,
    )

    @Test
    fun `copiar manda el valor al portapapeles`() = runTest {
        val viewModel = viewModel()
        val detection = urlDetection()

        viewModel.onAction(ScannerAction.RunResultAction(detection, ResultAction.Copy))

        assertEquals(listOf("https://ejemplo.com"), platform.copied)
    }

    @Test
    fun `copiar un WiFi manda el texto legible, no el QR crudo`() = runTest {
        // Pegarle a alguien "WIFI:T:WPA;S:...;;" no le sirve de nada.
        val viewModel = viewModel()

        viewModel.onAction(ScannerAction.RunResultAction(wifiDetection(), ResultAction.Copy))

        assertEquals(listOf("Red: MiRed · Clave: clave"), platform.copied)
    }

    @Test
    fun `abrir usa el destino que decidio el dominio`() = runTest {
        val viewModel = viewModel()
        val detection = urlDetection()
        val open = ResultAction.Open("https://ejemplo.com", "Abrir enlace")

        viewModel.onAction(ScannerAction.RunResultAction(detection, open))

        assertEquals(listOf("https://ejemplo.com"), platform.opened)
    }

    @Test
    fun `copiar confirma con un mensaje, porque no se ve nada`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onAction(ScannerAction.RunResultAction(urlDetection(), ResultAction.Copy))
            assertEquals(ScannerEffect.ShowMessage("Copiado"), awaitItem())
        }
    }

    @Test
    fun `una accion fallida avisa`() = runTest {
        val viewModel = viewModel(succeeds = false)

        viewModel.effects.test {
            val open = ResultAction.Open("algo://raro", "Abrir")
            viewModel.onAction(ScannerAction.RunResultAction(urlDetection(), open))
            assertEquals(ScannerEffect.ShowMessage("Ninguna app puede abrir esto"), awaitItem())
        }
    }

    @Test
    fun `el estado refleja si la plataforma sabe compartir`() = runTest {
        assertTrue(viewModel(canShare = true).state.value.canShare)
        assertTrue(!viewModel(canShare = false).state.value.canShare)
    }
}
