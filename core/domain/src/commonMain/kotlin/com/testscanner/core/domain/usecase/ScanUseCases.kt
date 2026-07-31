package com.testscanner.core.domain.usecase

import com.testscanner.core.domain.model.EngineSelection
import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferences
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.domain.scan.FallbackScannerEngine
import com.testscanner.core.domain.scan.enforcingRequestLimits
import com.testscanner.core.domain.scan.filteringFormats
import com.testscanner.core.domain.scan.interpretingValues
import com.testscanner.core.domain.scan.withDeadline
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.ImageDecodingEngine
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Catálogo completo de motores con su disponibilidad actual (RF-03). */
class ObserveEngineCatalogUseCase(
    private val repository: ScannerEngineRepository,
) {
    operator fun invoke(): Flow<List<EngineStatus>> = repository.observeCatalog()
}

/** Ajustes de escaneo del usuario. */
class ObserveScanPreferencesUseCase(
    private val repository: ScanPreferencesRepository,
) {
    operator fun invoke(): Flow<ScanPreferences> = repository.observePreferences()
}

/** Fija el motor preferido, o `null` para volver a selección automática (RF-02). */
class SetPreferredEngineUseCase(
    private val repository: ScanPreferencesRepository,
) {
    suspend operator fun invoke(id: ScannerEngineId?) = repository.setPreferredEngine(id)
}

/** Cambia el conjunto de formatos a detectar (RF-06). */
class SetScanFormatsUseCase(
    private val repository: ScanPreferencesRepository,
) {
    suspend operator fun invoke(formats: Set<BarcodeFormat>) {
        repository.setFormats(formats.ifEmpty { BarcodeFormat.all })
    }
}

/**
 * Arranca una sesión de escaneo sobre la cadena de motores que decida la política de selección.
 *
 * Responsabilidad única: **construir y ejecutar la cadena**. No persiste nada — de eso se ocupa
 * [SaveDetectionUseCase] — ni conoce la UI.
 */
class StartScanSessionUseCase(
    private val engineRepository: ScannerEngineRepository,
    private val selectEngine: SelectScannerEngineUseCase,
) {

    operator fun invoke(
        request: ScanRequest,
        preferredEngineId: ScannerEngineId? = null,
    ): Flow<ScanEvent> = flow {
        val selection = selectEngine(request, preferredEngineId)
        val engines = selection.chain.mapNotNull(engineRepository::engine)

        if (engines.isEmpty()) {
            emit(ScanEvent.Failed(noEngineError(selection)))
            return@flow
        }

        // El orden de los decoradores importa, y en dos niveles distintos:
        //  - Por motor: primero se filtra por formato lo que reporta, después se aplican los
        //    límites del request (cuántos códigos y si la sesión sigue), y solo lo que sobrevive se
        //    interpreta semánticamente. Envolver la cadena entera dejaría el fallback fuera del
        //    filtrado.
        //  - Sobre la cadena: el plazo. Si fuera por motor, una cadena de tres tardaría el triple
        //    de lo que el usuario pidió.
        val chain: BarcodeScannerEngine = FallbackScannerEngine(
            engines.map { engine ->
                engine.filteringFormats().enforcingRequestLimits().interpretingValues()
            },
        ).withDeadline()

        emitAll(chain.scan(request))
    }

    private fun noEngineError(selection: EngineSelection): ScanError {
        val reason = selection.rejected
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.id.id }
            ?.let { "Motores descartados: $it" }
            ?: "No hay motores registrados en esta plataforma"
        return ScanError.EngineUnavailable(engineId = null, reason = reason)
    }
}

/** Decodifica una imagen ya capturada con el primer motor de la cadena que sepa hacerlo (RF-07). */
class DecodeImageUseCase(
    private val engineRepository: ScannerEngineRepository,
    private val selectEngine: SelectScannerEngineUseCase,
) {

    suspend operator fun invoke(
        image: ScanImage,
        request: ScanRequest,
        preferredEngineId: ScannerEngineId? = null,
    ): Result<List<Barcode>> {
        val selection = selectEngine(request, preferredEngineId)
        val decoder = selection.chain
            .mapNotNull(engineRepository::engine)
            .filterIsInstance<ImageDecodingEngine>()
            .firstOrNull()
            ?: return Result.failure(
                IllegalStateException("Ningún motor disponible sabe decodificar imágenes"),
            )

        return decoder.decode(image, request)
            .map { barcodes -> barcodes.filter { it.format in request.formats } }
    }
}

/** Persiste una detección en el historial (RF-11). */
class SaveDetectionUseCase(
    private val repository: ScanHistoryRepository,
) {
    suspend operator fun invoke(detection: Detection) = repository.save(detection)
}

/** Historial de escaneos, más reciente primero. */
class ObserveScanHistoryUseCase(
    private val repository: ScanHistoryRepository,
) {
    operator fun invoke(): Flow<List<Detection>> = repository.observeHistory()
}

/** Vacía el historial. */
class ClearScanHistoryUseCase(
    private val repository: ScanHistoryRepository,
) {
    suspend operator fun invoke() = repository.clear()
}
