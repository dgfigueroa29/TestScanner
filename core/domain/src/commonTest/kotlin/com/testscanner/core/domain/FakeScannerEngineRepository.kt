package com.testscanner.core.domain

import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.BarcodeScannerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Repositorio de motores controlable, sin cámara ni plataforma real. */
class FakeScannerEngineRepository(
    override val platform: ScannerPlatform = ScannerPlatform.Android,
    engines: List<FakeScannerEngine> = emptyList(),
) : ScannerEngineRepository {

    private val byId: Map<ScannerEngineId, FakeScannerEngine> = engines.associateBy { it.id }
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

    fun setCatalog(statuses: List<EngineStatus>) {
        catalog.value = statuses
    }
}
