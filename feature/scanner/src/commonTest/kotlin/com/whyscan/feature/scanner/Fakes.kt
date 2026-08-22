package com.whyscan.feature.scanner

import com.whyscan.core.domain.model.EngineStatus
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.Permission
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.permissions.PermissionStatus
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PickImageResult
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ImageDecodingEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerEngineDescriptor
import com.whyscan.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

class FakeEngine(
    override val id: ScannerEngineId,
    private val availability: EngineAvailability = EngineAvailability.Available,
    private val events: List<ScanEvent> = emptyList(),
    /** Qué devuelve al decodificar una imagen. `null` = este motor no sabe hacerlo. */
    private val decoded: Result<List<Barcode>>? = null,
) : BarcodeScannerEngine, ImageDecodingEngine {

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.byId(id)

    override suspend fun availability(): EngineAvailability = availability

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.SessionStarted(id))
        events.forEach { emit(it) }
        emit(ScanEvent.SessionEnded(id))
    }

    override suspend fun decode(image: ScanImage, request: ScanRequest): Result<List<Barcode>> =
        decoded ?: Result.failure(IllegalStateException("$id no decodifica imágenes"))

    fun status(): EngineStatus = EngineStatus(descriptor, availability, installed = true)
}

class FakeEngineRepository(
    override val platform: ScannerPlatform = ScannerPlatform.Android,
    engines: List<FakeEngine> = emptyList(),
) : ScannerEngineRepository {

    private val byId = engines.associateBy { it.id }
    private val catalog = MutableStateFlow(engines.map { it.status() })

    var refreshCount: Int = 0
        private set

    override fun observeCatalog(): Flow<List<EngineStatus>> = catalog.asStateFlow()

    override suspend fun refresh() {
        refreshCount++
    }

    override fun engine(id: ScannerEngineId): BarcodeScannerEngine? = byId[id]

    override suspend fun status(id: ScannerEngineId): EngineStatus? =
        catalog.value.firstOrNull { it.id == id }
}

class FakePreferencesRepository(
    initial: ScanPreferences = ScanPreferences(),
) : ScanPreferencesRepository {

    private val state = MutableStateFlow(initial)

    override fun observePreferences(): Flow<ScanPreferences> = state.asStateFlow()
    override suspend fun current(): ScanPreferences = state.first()

    override suspend fun setPreferredEngine(id: ScannerEngineId?) {
        state.update { it.copy(preferredEngineId = id) }
    }

    override suspend fun setFormats(formats: Set<BarcodeFormat>) {
        state.update { it.copy(formats = formats) }
    }

    override suspend fun setContinuous(enabled: Boolean) {
        state.update { it.copy(continuous = enabled) }
    }

    override suspend fun setAllowMultiple(enabled: Boolean) {
        state.update { it.copy(allowMultiple = enabled) }
    }
}

class FakeHistoryRepository : ScanHistoryRepository {

    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())

    val saved: List<Detection> get() = state.value.map { it.detection }

    override fun observeHistory(): Flow<List<HistoryEntry>> = state.asStateFlow()

    override suspend fun save(detection: Detection) {
        state.update { listOf(HistoryEntry(detection)) + it }
    }

    override suspend fun setNote(detectionId: String, note: String?) {
        state.update { current -> current.map { if (it.id == detectionId) it.copy(note = note) else it } }
    }

    override suspend fun delete(detectionId: String) {
        state.update { current -> current.filterNot { it.id == detectionId } }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

class FakePermissionController(
    private var result: PermissionStatus = PermissionStatus.Granted,
) : PermissionController {

    var requests: Int = 0
        private set

    override suspend fun status(permission: Permission): PermissionStatus = result

    override suspend fun request(permission: Permission): PermissionStatus {
        requests++
        return result
    }

    override fun openAppSettings() = Unit

    fun willReturn(status: PermissionStatus) {
        result = status
    }
}

fun detectionOf(
    engineId: ScannerEngineId,
    value: String = "hola",
    atMillis: Long = 1_000L,
): Detection = Detection.of(
    barcode = Barcode(rawValue = value, format = BarcodeFormat.QrCode),
    engineId = engineId,
    detectedAtMillis = atMillis,
)

/** Selector de imágenes controlable: devuelve lo que se le diga, sin tocar la plataforma. */
class FakeImagePicker(
    private val result: PickImageResult = PickImageResult.Cancelled,
) : ImagePicker {

    var invocations: Int = 0
        private set

    override suspend fun pickImage(): PickImageResult {
        invocations++
        return result
    }
}

/** Registra qué se pidió hacer, para poder afirmarlo en los tests sin plataforma. */
class FakePlatformActions(
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
