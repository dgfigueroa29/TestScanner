package com.testscanner.core.domain

import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio de motores controlable, sin cámara ni plataforma real.
 *
 * Acepta cualquier [BarcodeScannerEngine] y no solo [FakeScannerEngine] porque hay tests que
 * necesitan motores con capacidades opcionales —decodificar imágenes, por ejemplo— y obligarles a
 * pasar por el fake genérico convertiría cada caso nuevo en una modificación de esta clase.
 */
class FakeScannerEngineRepository(
    override val platform: ScannerPlatform = ScannerPlatform.Android,
    engines: List<BarcodeScannerEngine> = emptyList(),
) : ScannerEngineRepository {

    private val byId: Map<ScannerEngineId, BarcodeScannerEngine> = engines.associateBy { it.id }
    private val catalog = MutableStateFlow(engines.map { it.initialStatus() })

    var refreshCount: Int = 0
        private set

    override fun observeCatalog(): Flow<List<EngineStatus>> = catalog.asStateFlow()

    override suspend fun refresh() {
        refreshCount++
    }

    override fun engine(id: ScannerEngineId): BarcodeScannerEngine? = byId[id]

    override suspend fun status(id: ScannerEngineId): EngineStatus? =
        catalog.value.firstOrNull { it.id == id }

    fun setCatalog(statuses: List<EngineStatus>) {
        catalog.value = statuses
    }
}

/** Un [FakeScannerEngine] trae su disponibilidad declarada; cualquier otro se asume disponible. */
private fun BarcodeScannerEngine.initialStatus(): EngineStatus = when (this) {
    is FakeScannerEngine -> status()
    else -> EngineStatus(descriptor, EngineAvailability.Available, installed = true)
}
