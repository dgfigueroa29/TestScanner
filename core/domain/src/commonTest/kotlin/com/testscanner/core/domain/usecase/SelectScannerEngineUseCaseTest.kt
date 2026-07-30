package com.testscanner.core.domain.usecase

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.domain.FakeScannerEngineRepository
import com.testscanner.core.domain.model.RejectionReason
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Permission
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.EngineAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectScannerEngineUseCaseTest {

    // `select` es lógica pura: el repositorio solo hace falta para la sobrecarga `invoke`.
    private val useCase = SelectScannerEngineUseCase(FakeScannerEngineRepository())

    @Test
    fun `ordena por la prioridad de la plataforma`() {
        val catalog = listOf(
            FakeScannerEngine(ScannerEngineId.ManualInput).status(),
            FakeScannerEngine(ScannerEngineId.MlKitCameraX).status(),
            FakeScannerEngine(ScannerEngineId.GmsCodeScanner).status(),
        )

        val selection = useCase.select(
            catalog = catalog,
            request = ScanRequest(),
            preferredEngineId = null,
            platform = ScannerPlatform.Android,
        )

        assertEquals(
            listOf(
                ScannerEngineId.GmsCodeScanner,
                ScannerEngineId.MlKitCameraX,
                ScannerEngineId.ManualInput,
            ),
            selection.chain,
        )
    }

    @Test
    fun `el motor elegido por el usuario encabeza la cadena sin perder los fallbacks`() {
        val catalog = listOf(
            FakeScannerEngine(ScannerEngineId.GmsCodeScanner).status(),
            FakeScannerEngine(ScannerEngineId.MlKitCameraX).status(),
            FakeScannerEngine(ScannerEngineId.ManualInput).status(),
        )

        val selection = useCase.select(
            catalog = catalog,
            request = ScanRequest(),
            preferredEngineId = ScannerEngineId.ManualInput,
            platform = ScannerPlatform.Android,
        )

        assertEquals(ScannerEngineId.ManualInput, selection.preferred)
        assertTrue(selection.hasFallback)
        assertEquals(
            listOf(ScannerEngineId.GmsCodeScanner, ScannerEngineId.MlKitCameraX),
            selection.chain.drop(1),
        )
    }

    @Test
    fun `descarta motores no disponibles y explica el motivo`() {
        val catalog = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                availability = EngineAvailability.RequiresPermission(Permission.Camera),
            ).status(),
            FakeScannerEngine(ScannerEngineId.ManualInput).status(),
        )

        val selection = useCase.select(catalog, ScanRequest(), null, ScannerPlatform.Android)

        assertEquals(listOf(ScannerEngineId.ManualInput), selection.chain)
        val rejection = selection.rejected.single()
        assertEquals(ScannerEngineId.GmsCodeScanner, rejection.id)
        assertTrue(rejection.reason is RejectionReason.NotAvailable)
    }

    @Test
    fun `descarta motores del catalogo que no estan instalados en esta plataforma`() {
        val catalog = listOf(
            FakeScannerEngine(ScannerEngineId.VisionIos).status(installed = false),
            FakeScannerEngine(ScannerEngineId.ManualInput).status(),
        )

        val selection = useCase.select(catalog, ScanRequest(), null, ScannerPlatform.Android)

        assertEquals(listOf(ScannerEngineId.ManualInput), selection.chain)
    }

    @Test
    fun `el modo continuo descarta a los motores que no lo soportan`() {
        // Es el caso real del Google Code Scanner: abre su UI, devuelve un código y se cierra.
        val catalog = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                capabilities = FakeScannerEngine.defaultCapabilities(continuous = false),
            ).status(),
            FakeScannerEngine(ScannerEngineId.MlKitCameraX).status(),
        )

        val selection = useCase.select(
            catalog = catalog,
            request = ScanRequest(continuous = true),
            preferredEngineId = null,
            platform = ScannerPlatform.Android,
        )

        assertEquals(listOf(ScannerEngineId.MlKitCameraX), selection.chain)
        val reason = selection.rejected.single().reason
        assertTrue(reason is RejectionReason.DoesNotSatisfyRequest)
        assertTrue("escaneo continuo" in reason.missingCapabilities)
    }

    @Test
    fun `escanear desde imagen descarta a los motores que solo leen de camara`() {
        val catalog = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                capabilities = FakeScannerEngine.defaultCapabilities(
                    sources = setOf(ScanSource.LiveCamera),
                ),
            ).status(),
            FakeScannerEngine(
                id = ScannerEngineId.ZXingCpp,
                capabilities = FakeScannerEngine.defaultCapabilities(
                    sources = setOf(ScanSource.LiveCamera, ScanSource.StaticImage),
                ),
            ).status(),
        )

        val selection = useCase.select(
            catalog = catalog,
            request = ScanRequest(source = ScanSource.StaticImage),
            preferredEngineId = null,
            platform = ScannerPlatform.Android,
        )

        assertEquals(listOf(ScannerEngineId.ZXingCpp), selection.chain)
    }

    @Test
    fun `a igualdad de prioridad gana el que cubre mas formatos`() {
        val request = ScanRequest(formats = setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13))
        val catalog = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.MlKitOcr,
                capabilities = FakeScannerEngine.defaultCapabilities(
                    formats = setOf(BarcodeFormat.Ean13),
                ),
            ).status(),
            FakeScannerEngine(
                id = ScannerEngineId.BrowserDetector,
                capabilities = FakeScannerEngine.defaultCapabilities(
                    formats = setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13),
                ),
            ).status(),
        )

        // En Desktop ninguno de los dos está en la tabla de prioridad: desempata la cobertura.
        val selection = useCase.select(catalog, request, null, ScannerPlatform.Desktop)

        assertEquals(ScannerEngineId.BrowserDetector, selection.preferred)
    }

    @Test
    fun `un motor preferido pero no elegible no se promueve`() {
        val catalog = listOf(
            FakeScannerEngine(ScannerEngineId.ManualInput).status(),
            FakeScannerEngine(
                id = ScannerEngineId.GmsCodeScanner,
                availability = EngineAvailability.NotImplemented(plannedPhase = 2),
            ).status(),
        )

        val selection = useCase.select(
            catalog = catalog,
            request = ScanRequest(),
            preferredEngineId = ScannerEngineId.GmsCodeScanner,
            platform = ScannerPlatform.Android,
        )

        assertEquals(listOf(ScannerEngineId.ManualInput), selection.chain)
    }

    @Test
    fun `sin motores elegibles la cadena queda vacia`() {
        val catalog = listOf(
            FakeScannerEngine(
                id = ScannerEngineId.ManualInput,
                availability = EngineAvailability.Unsupported("test"),
            ).status(),
        )

        val selection = useCase.select(catalog, ScanRequest(), null, ScannerPlatform.Web)

        assertTrue(selection.chain.isEmpty())
        assertEquals(1, selection.rejected.size)
    }
}
