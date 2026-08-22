package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.model.EngineSelection
import com.whyscan.core.domain.model.EngineStatus
import com.whyscan.core.domain.model.RejectedEngine
import com.whyscan.core.domain.model.RejectionReason
import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.domain.scan.EnginePriorityPolicy
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import kotlinx.coroutines.flow.first

/**
 * Decide qué motores pueden atender una petición y en qué orden probarlos.
 *
 * Es lógica pura sobre datos declarativos ([com.whyscan.core.scanner.ScannerCapabilities] y
 * [com.whyscan.core.scanner.EngineAvailability]), sin ninguna referencia a un motor concreto.
 * Por eso se testea entera en `commonTest` sin cámara ni dispositivo.
 *
 * Algoritmo:
 * 1. Descartar los que no están usables ahora → `rejected` con el motivo.
 * 2. Descartar los que no satisfacen la petición → `rejected` con lo que les falta.
 * 3. Ordenar los supervivientes por prioridad de plataforma y, a igualdad, por cobertura de
 *    formatos.
 * 4. Si el usuario fijó un motor y sobrevivió, promoverlo al frente sin alterar el resto.
 */
class SelectScannerEngineUseCase(
    private val repository: ScannerEngineRepository,
) {

    suspend operator fun invoke(
        request: ScanRequest,
        preferredEngineId: ScannerEngineId? = null,
    ): EngineSelection {
        val catalog = repository.observeCatalog().first()
        return select(catalog, request, preferredEngineId, repository.platform)
    }

    /** Expuesto por separado para poder testear la política sin repositorio. */
    fun select(
        catalog: List<EngineStatus>,
        request: ScanRequest,
        preferredEngineId: ScannerEngineId?,
        platform: ScannerPlatform,
    ): EngineSelection {
        val rejected = mutableListOf<RejectedEngine>()
        val eligible = mutableListOf<EngineStatus>()

        catalog.forEach { status ->
            when {
                !status.isUsableFor(request) -> rejected += RejectedEngine(
                    id = status.id,
                    reason = RejectionReason.NotAvailable(status.availability),
                )

                !status.descriptor.capabilities.satisfies(request) -> rejected += RejectedEngine(
                    id = status.id,
                    reason = RejectionReason.DoesNotSatisfyRequest(
                        uncoveredFormats = status.descriptor.capabilities.uncoveredFormats(request),
                        missingCapabilities = missingCapabilities(status, request),
                    ),
                )

                else -> eligible += status
            }
        }

        val ordered = eligible
            .sortedWith(
                compareBy<EngineStatus> { EnginePriorityPolicy.rank(platform, it.id) }
                    .thenByDescending { it.descriptor.capabilities.coveredFormats(request).size }
                    .thenBy { it.descriptor.displayName },
            )
            .map { it.id }

        val chain = promote(ordered, preferredEngineId)
        return EngineSelection(chain = chain, rejected = rejected)
    }

    /**
     * Coloca el motor elegido por el usuario al frente **sin descartar los demás**: la elección
     * manual cambia la preferencia, no renuncia al fallback (G4).
     */
    private fun promote(
        ordered: List<ScannerEngineId>,
        preferredEngineId: ScannerEngineId?,
    ): List<ScannerEngineId> = when {
        preferredEngineId == null || preferredEngineId !in ordered -> ordered
        else -> listOf(preferredEngineId) + ordered.filterNot { it == preferredEngineId }
    }

    /**
     * Decodificar una imagen no necesita cámara, así que un motor bloqueado por ese permiso sigue
     * sirviendo para ello. La regla vive en [EngineStatus.canDecodeImages] para que la UI aplique
     * exactamente la misma y no ofrezca un botón que aquí se descartaría.
     */
    private fun EngineStatus.isUsableFor(request: ScanRequest): Boolean =
        if (request.source == ScanSource.StaticImage) canDecodeImages else isUsable

    private fun missingCapabilities(status: EngineStatus, request: ScanRequest): List<String> {
        val capabilities = status.descriptor.capabilities
        return buildList {
            if (request.source !in capabilities.sources) add("fuente ${request.source.displayName}")
            if (request.continuous && !capabilities.supportsContinuousScan) add("escaneo continuo")
            if (request.allowMultiple && !capabilities.supportsMultipleCodes) add("múltiples códigos")
            if (request.requireTorchControl && !capabilities.supportsTorch) add("linterna")
            if (capabilities.coveredFormats(request).isEmpty()) add("formatos solicitados")
        }
    }
}
