package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.FakeScannerEngine
import com.whyscan.core.domain.FakeScannerEngineRepository
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.BarcodeValueType
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.FakeTimeProvider
import com.whyscan.core.scanner.ImageDecodingEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerCapabilities
import com.whyscan.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecodeImageUseCaseTest {

    /** Motor que sabe decodificar imágenes y devuelve lo que se le diga. */
    private class FakeDecoder(
        override val id: ScannerEngineId,
        private val result: Result<List<Barcode>>,
    ) : BarcodeScannerEngine, ImageDecodingEngine {

        var decodeInvocations: Int = 0
            private set

        override val descriptor = ScannerEngineDescriptor(
            id = id,
            displayName = id.id,
            vendor = "test",
            description = "decodificador de prueba",
            platforms = setOf(ScannerPlatform.Android),
            capabilities = ScannerCapabilities(
                supportedFormats = BarcodeFormat.all,
                sources = setOf(ScanSource.StaticImage),
            ),
            plannedPhase = 1,
            strength = "-",
            limitation = "-",
        )

        override suspend fun availability(): EngineAvailability = EngineAvailability.Available

        override fun scan(request: ScanRequest): Flow<ScanEvent> = emptyFlow()

        override suspend fun decode(image: ScanImage, request: ScanRequest): Result<List<Barcode>> {
            decodeInvocations++
            return result
        }
    }

    private val image = ScanImage(encoded = byteArrayOf(1, 2, 3), mimeType = "image/png")

    private val request = ScanRequest(source = ScanSource.StaticImage)

    private fun repositoryOf(vararg engines: BarcodeScannerEngine) =
        FakeScannerEngineRepository(engines = engines.toList())

    private fun useCase(repository: FakeScannerEngineRepository) = DecodeImageUseCase(
        engineRepository = repository,
        selectEngine = SelectScannerEngineUseCase(repository),
        time = FakeTimeProvider(now = 500L),
    )

    @Test
    fun `atribuye la lectura al motor que la hizo`() = runTest {
        // Es el dato que este producto existe para dar: sin él, el historial no distingue motores.
        val decoder = FakeDecoder(
            ScannerEngineId.MlKitCameraX,
            Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
        )
        val repository = repositoryOf(decoder)

        val detections = useCase(repository)(image, request).getOrThrow()

        assertEquals(1, detections.size)
        assertEquals(ScannerEngineId.MlKitCameraX, detections.first().engineId)
    }

    @Test
    fun `interpreta el contenido igual que la camara`() = runTest {
        // Un QR con una URL debe ofrecer "Abrir enlace" venga de la cámara o de una foto. Sin esto,
        // el mismo código daría resultados distintos según de dónde saliera.
        val decoder = FakeDecoder(
            ScannerEngineId.MlKitCameraX,
            Result.success(listOf(Barcode("https://ejemplo.com", BarcodeFormat.QrCode))),
        )

        val detections = useCase(repositoryOf(decoder))(image, request).getOrThrow()

        assertTrue(
            detections.first().barcode.valueType is BarcodeValueType.Url,
            "el valor quedó sin interpretar: ${detections.first().barcode.valueType}",
        )
    }

    @Test
    fun `si el primer motor no lee nada prueba el siguiente`() = runTest {
        // Es el caso del OCR: un código dañado que el decodificador no ve y cuyo número impreso sí
        // es legible. Sin cadena, ese motor no llegaría a ejecutarse nunca.
        val primero = FakeDecoder(ScannerEngineId.MlKitCameraX, Result.success(emptyList()))
        val segundo = FakeDecoder(
            ScannerEngineId.MlKitOcr,
            Result.success(listOf(Barcode("7501234567893", BarcodeFormat.Ean13))),
        )

        val detections = useCase(repositoryOf(primero, segundo))(image, request).getOrThrow()

        assertEquals(ScannerEngineId.MlKitOcr, detections.single().engineId)
        assertEquals(1, primero.decodeInvocations)
    }

    @Test
    fun `si el primer motor falla prueba el siguiente`() = runTest {
        val primero = FakeDecoder(ScannerEngineId.MlKitCameraX, Result.failure(RuntimeException("boom")))
        val segundo = FakeDecoder(
            ScannerEngineId.MlKitOcr,
            Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
        )

        val detections = useCase(repositoryOf(primero, segundo))(image, request).getOrThrow()

        assertEquals(ScannerEngineId.MlKitOcr, detections.single().engineId)
    }

    @Test
    fun `deja de probar en cuanto uno lee`() = runTest {
        val primero = FakeDecoder(
            ScannerEngineId.MlKitCameraX,
            Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
        )
        val segundo = FakeDecoder(ScannerEngineId.MlKitOcr, Result.success(emptyList()))

        useCase(repositoryOf(primero, segundo))(image, request).getOrThrow()

        assertEquals(0, segundo.decodeInvocations)
    }

    @Test
    fun `una imagen sin codigo no es un error`() = runTest {
        // Distinguirlo importa: la UI dice "no se encontró ningún código", no "algo falló".
        val decoder = FakeDecoder(ScannerEngineId.MlKitCameraX, Result.success(emptyList()))

        val result = useCase(repositoryOf(decoder))(image, request)

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun `si todos fallan devuelve el fallo`() = runTest {
        val decoder = FakeDecoder(ScannerEngineId.MlKitCameraX, Result.failure(RuntimeException("boom")))

        val result = useCase(repositoryOf(decoder))(image, request)

        assertTrue(result.isFailure)
    }

    @Test
    fun `respeta el filtro de formatos`() = runTest {
        val decoder = FakeDecoder(
            ScannerEngineId.MlKitCameraX,
            Result.success(
                listOf(
                    Barcode("hola", BarcodeFormat.QrCode),
                    Barcode("7501234567893", BarcodeFormat.Ean13),
                ),
            ),
        )
        val soloQr = ScanRequest(source = ScanSource.StaticImage, formats = setOf(BarcodeFormat.QrCode))

        val detections = useCase(repositoryOf(decoder))(image, soloQr).getOrThrow()

        assertEquals(listOf(BarcodeFormat.QrCode), detections.map { it.barcode.format })
    }

    @Test
    fun `sin ningun motor que decodifique imagenes falla con un motivo`() = runTest {
        val soloCamara = FakeScannerEngine(ScannerEngineId.GmsCodeScanner)
        val repository = FakeScannerEngineRepository(engines = listOf(soloCamara))

        val result = useCase(repository)(image, request)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("decodificar") == true,
            "el motivo no explica nada: ${result.exceptionOrNull()?.message}",
        )
    }

    @Test
    fun `mide la latencia de la decodificacion`() = runTest {
        val decoder = FakeDecoder(
            ScannerEngineId.MlKitCameraX,
            Result.success(listOf(Barcode("hola", BarcodeFormat.QrCode))),
        )

        val detections = useCase(repositoryOf(decoder))(image, request).getOrThrow()

        // El reloj falso no avanza, así que la latencia es 0 — pero está medida, no ausente.
        assertEquals(0L, detections.single().latencyMillis)
        assertEquals(500L, detections.single().detectedAtMillis)
    }
}
