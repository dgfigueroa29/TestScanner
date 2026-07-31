package com.testscanner.feature.scanner

import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferences
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.Permission
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.permissions.PermissionStatus
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
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
) : BarcodeScannerEngine {

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.byId(id)

    override suspend fun availability(): EngineAvailability = availability

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.SessionStarted(id))
        events.forEach { emit(it) }
        emit(ScanEvent.SessionEnded(id))
    }

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

    private val state = MutableStateFlow<List<Detection>>(emptyList())

    val saved: List<Detection> get() = state.value

    override fun observeHistory(): Flow<List<Detection>> = state.asStateFlow()

    override suspend fun save(detection: Detection) {
        state.update { listOf(detection) + it }
    }

    override suspend fun findById(id: String): Detection? = state.value.firstOrNull { it.id == id }

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
