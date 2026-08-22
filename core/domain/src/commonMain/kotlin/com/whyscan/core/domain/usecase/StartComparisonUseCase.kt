package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.domain.scan.ComparingScannerEngine
import com.whyscan.core.domain.scan.filteringFormats
import com.whyscan.core.domain.scan.interpretingValues
import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.ScanEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Motores que participan en una comparación, o el motivo por el que no puede arrancar. */
sealed interface ComparisonPlan {
    data class Ready(val participants: List<ScannerEngineId>) : ComparisonPlan
    data class NotEnoughEngines(val available: List<ScannerEngineId>) : ComparisonPlan
}

/**
 * Arranca una comparación: varios motores sobre la misma petición, a la vez.
 *
 * Es el gemelo de [StartScanSessionUseCase] y no una variante suya con un flag. La diferencia no es
 * de configuración sino de intención: escanear busca **un** resultado probando en orden; comparar
 * busca **todos** los resultados para contrastarlos. Un solo caso de uso con un booleano tendría dos
 * comportamientos incompatibles bajo el mismo nombre.
 */
class StartComparisonUseCase(
    private val engineRepository: ScannerEngineRepository,
    private val selectEngine: SelectScannerEngineUseCase,
) {

    /** Qué motores participarían, sin arrancar nada. La UI lo usa para explicarse antes de correr. */
    suspend fun plan(request: ScanRequest): ComparisonPlan {
        val participants = selectEngine(request).chain
        return if (participants.size >= MIN_PARTICIPANTS) {
            ComparisonPlan.Ready(participants)
        } else {
            ComparisonPlan.NotEnoughEngines(participants)
        }
    }

    operator fun invoke(request: ScanRequest): Flow<ScanEvent> = flow {
        val plan = plan(request)

        if (plan !is ComparisonPlan.Ready) {
            emit(
                ScanEvent.Failed(
                    ScanError.EngineUnavailable(
                        engineId = null,
                        reason = "Se necesitan al menos $MIN_PARTICIPANTS motores disponibles",
                    ),
                ),
            )
            return@flow
        }

        val engines = plan.participants
            .mapNotNull(engineRepository::engine)
            .map { engine -> engine.filteringFormats().interpretingValues() }

        val comparing: BarcodeScannerEngine = ComparingScannerEngine(engines)
        emitAll(comparing.scan(request))
    }

    private companion object {
        const val MIN_PARTICIPANTS = 2
    }
}
