package com.testscanner.core.data.repository

import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Cruza el catálogo estático con los motores realmente enlazados en este binario.
 *
 * Cada plataforma inyecta su propia lista de [BarcodeScannerEngine]; el resto del catálogo se
 * reporta como no soportado aquí o pendiente de implementar, con la fase en la que llega.
 *
 * `observeCatalog()` recalcula la disponibilidad en cada colección — y no cachea un estado
 * inicial — porque la disponibilidad cambia bajo los pies: el usuario concede el permiso de
 * cámara, ML Kit termina de descargar su modelo, o la cámara queda ocupada por otra app.
 */
class ScannerEngineRepositoryImpl(
    override val platform: ScannerPlatform,
    installedEngines: List<BarcodeScannerEngine>,
    private val catalog: List<ScannerEngineDescriptor> = ScannerEngineCatalog.all,
) : ScannerEngineRepository {

    private val engines: Map<ScannerEngineId, BarcodeScannerEngine> =
        installedEngines.associateBy { it.id }

    private val invalidations = MutableStateFlow(0)

    override fun observeCatalog(): Flow<List<EngineStatus>> =
        invalidations.map { computeStatuses() }

    override suspend fun refresh() {
        invalidations.update { it + 1 }
    }

    override fun engine(id: ScannerEngineId): BarcodeScannerEngine? = engines[id]

    override suspend fun status(id: ScannerEngineId): EngineStatus? =
        observeCatalog().first().firstOrNull { it.id == id }

    private suspend fun computeStatuses(): List<EngineStatus> {
        val statuses = mutableListOf<EngineStatus>()
        for (descriptor in catalog) {
            val engine = engines[descriptor.id]
            val availability = when {
                engine != null -> engine.availability()
                !descriptor.runsOn(platform) -> EngineAvailability.Unsupported(
                    "No disponible en ${platform.displayName}",
                )

                else -> EngineAvailability.NotImplemented(descriptor.plannedPhase)
            }
            statuses.add(
                EngineStatus(
                    descriptor = descriptor,
                    installed = engine != null,
                    availability = availability,
                ),
            )
        }
        return statuses
    }
}
