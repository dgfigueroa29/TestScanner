package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.FakeScannerEngine
import com.whyscan.core.domain.FakeScannerEngineRepository
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ImageDecodingEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerCapabilities
import com.whyscan.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La traducción de ajustes a [ScanRequest], probada donde vive.
 *
 * Estas dos reglas —de qué fuente escanea el motor manual, y que una imagen siempre admite varios
 * códigos— estaban escritas en el ViewModel de escaneo y no tenían test propio: solo se ejercitaban
 * de refilón, si algún test de la pantalla pasaba por ahí. Al agruparlas en [ScanSessions] (deuda
 * D16) se comprueban sin dispatcher ni ViewModel.
 */
class ScanSessionsTest {

    /** Decodificador que se queda con el [ScanRequest] que le llega, para poder afirmarlo. */
    private class RecordingDecoder(
        override val id: ScannerEngineId = ScannerEngineId.MlKitCameraX,
    ) : BarcodeScannerEngine, ImageDecodingEngine {

        var lastRequest: ScanRequest? = null
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
                supportsMultipleCodes = true,
            ),
            plannedPhase = 1,
            strength = "-",
            limitation = "-",
        )

        override suspend fun availability(): EngineAvailability = EngineAvailability.Available

        override fun scan(request: ScanRequest): Flow<ScanEvent> = emptyFlow()

        override suspend fun decode(image: ScanImage, request: ScanRequest): Result<List<Barcode>> {
            lastRequest = request
            return Result.success(listOf(Barcode(rawValue = "hola", format = BarcodeFormat.QrCode)))
        }
    }

    private class RecordingHistory : ScanHistoryRepository {
        val saved = mutableListOf<Detection>()
        private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())

        override fun observeHistory(): Flow<List<HistoryEntry>> = state.asStateFlow()
        override suspend fun save(detection: Detection) {
            saved += detection
        }

        override suspend fun setNote(detectionId: String, note: String?) = Unit
        override suspend fun delete(detectionId: String) = Unit
        override suspend fun clear() = saved.clear()
    }

    private val history = RecordingHistory()

    private fun sessionsOf(repository: FakeScannerEngineRepository): ScanSessions {
        val select = SelectScannerEngineUseCase(repository)
        return ScanSessions(
            startSession = StartScanSessionUseCase(repository, select),
            decodeImage = DecodeImageUseCase(repository, select),
            saveDetection = SaveDetectionUseCase(history),
        )
    }

    @Test
    fun `el motor manual escanea desde entrada manual, no desde la camara`() = runTest {
        // Si la fuente fuera siempre LiveCamera, el selector descartaría el motor manual justo
        // cuando es el único que queda, y la sesión moriría con un EngineUnavailable.
        val manual = FakeScannerEngine(
            id = ScannerEngineId.ManualInput,
            capabilities = FakeScannerEngine.defaultCapabilities(
                sources = setOf(ScanSource.ManualInput),
            ),
        )
        val sessions = sessionsOf(FakeScannerEngineRepository(engines = listOf(manual)))

        val events = sessions
            .start(ScanPreferences(preferredEngineId = ScannerEngineId.ManualInput))
            .toList()

        assertEquals(ScanEvent.SessionStarted(ScannerEngineId.ManualInput), events.first())
    }

    @Test
    fun `cualquier otro motor escanea de la camara en vivo`() = runTest {
        val camera = FakeScannerEngine(id = ScannerEngineId.MlKitCameraX)
        val sessions = sessionsOf(FakeScannerEngineRepository(engines = listOf(camera)))

        val first = sessions
            .start(ScanPreferences(preferredEngineId = ScannerEngineId.MlKitCameraX))
            .first()

        assertEquals(ScanEvent.SessionStarted(ScannerEngineId.MlKitCameraX), first)
    }

    @Test
    fun `una imagen siempre admite varios codigos, diga lo que diga el ajuste`() = runTest {
        // `allowMultiple` está pensado para el vídeo en vivo. En una foto los códigos ya están
        // todos ahí y descartarlos sería tirar trabajo hecho.
        val decoder = RecordingDecoder()
        val sessions = sessionsOf(FakeScannerEngineRepository(engines = listOf(decoder)))

        sessions.decode(
            image = ScanImage(encoded = byteArrayOf(1), mimeType = "image/png"),
            preferences = ScanPreferences(allowMultiple = false),
        )

        assertTrue(decoder.lastRequest!!.allowMultiple)
        assertEquals(ScanSource.StaticImage, decoder.lastRequest!!.source)
    }

    @Test
    fun `los formatos elegidos llegan a la peticion de imagen`() = runTest {
        val decoder = RecordingDecoder()
        val sessions = sessionsOf(FakeScannerEngineRepository(engines = listOf(decoder)))
        val only = setOf(BarcodeFormat.QrCode)

        sessions.decode(
            image = ScanImage(encoded = byteArrayOf(1), mimeType = "image/png"),
            preferences = ScanPreferences(formats = only),
        )

        assertEquals(only, decoder.lastRequest!!.formats)
    }

    @Test
    fun `guardar persiste todas las detecciones`() = runTest {
        val sessions = sessionsOf(FakeScannerEngineRepository())
        val detections = listOf(
            FakeScannerEngine.detection(ScannerEngineId.ManualInput, value = "uno"),
            FakeScannerEngine.detection(ScannerEngineId.ManualInput, value = "dos"),
        )

        sessions.save(detections)

        assertEquals(listOf("uno", "dos"), history.saved.map { it.barcode.rawValue })
    }
}
