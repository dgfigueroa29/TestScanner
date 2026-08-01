package com.testscanner.core.domain.usecase

import com.testscanner.core.domain.model.EngineSelection
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.domain.scan.BarcodeValueParser
import com.testscanner.core.domain.scan.FallbackScannerEngine
import com.testscanner.core.domain.scan.enforcingRequestLimits
import com.testscanner.core.domain.scan.filteringFormats
import com.testscanner.core.domain.scan.interpretingValues
import com.testscanner.core.domain.scan.withDeadline
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeValueType
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.ImageDecodingEngine
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TimeProvider
import com.testscanner.core.scanner.capability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

// Los ajustes de escaneo no tienen casos de uso propios: los tenían, uno por operación, y cada uno
// delegaba al repositorio sin añadir nada. Están agrupados en `ScanSettings`, que sí guarda la única
// regla que había (ver deuda D16 en docs/ROADMAP.md). El catálogo de motores tampoco lo tiene, por
// el mismo motivo: quien lo observa usa `ScannerEngineRepository.observeCatalog()` directamente.

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

/**
 * Decodifica una imagen ya capturada, recorriendo la cadena de motores hasta que uno lea algo
 * (RF-07).
 *
 * ### Por qué recorre la cadena y no se queda con el primero
 * Es el mismo compromiso que [FallbackScannerEngine] hace con la cámara (G4): que el motor
 * preferido no sepa leer *esta* imagen no es motivo para rendirse. Y aquí importa más que en vivo,
 * porque es justo el caso del OCR — un código dañado que ML Kit no decodifica y cuyo número
 * impreso sí es legible. Sin fallback, ese motor no llegaría a ejecutarse nunca.
 *
 * ### Por qué devuelve `Detection` y no `Barcode`
 * Porque **qué motor lo leyó es el dato que este producto existe para dar**. Devolver códigos
 * sueltos obligaba a quien llamara a inventar la atribución, y el historial acabaría mintiendo.
 */
class DecodeImageUseCase(
    private val engineRepository: ScannerEngineRepository,
    private val selectEngine: SelectScannerEngineUseCase,
    private val time: TimeProvider = SystemTimeProvider,
) {

    suspend operator fun invoke(
        image: ScanImage,
        request: ScanRequest,
        preferredEngineId: ScannerEngineId? = null,
    ): Result<List<Detection>> {
        val decoders = selectEngine(request, preferredEngineId).chain
            .mapNotNull(engineRepository::engine)
            .mapNotNull { it.capability<ImageDecodingEngine>() }

        if (decoders.isEmpty()) {
            return Result.failure(
                IllegalStateException("Ningún motor disponible sabe decodificar imágenes"),
            )
        }

        val startedAtMillis = time.nowMillis()
        var lastFailure: Throwable? = null

        decoders.forEach { decoder ->
            val engineId = (decoder as BarcodeScannerEngine).id
            val decoded = decoder.decode(image, request)
                .map { barcodes -> barcodes.toDetections(engineId, startedAtMillis, request) }

            decoded
                .onSuccess { detections -> if (detections.isNotEmpty()) return Result.success(detections) }
                .onFailure { lastFailure = it }
        }

        // Ningún motor falló pero tampoco leyó nada: la imagen no tiene un código reconocible, que
        // no es un error sino una respuesta.
        return lastFailure?.let { Result.failure(it) } ?: Result.success(emptyList())
    }

    /**
     * Aplica el mismo filtrado por formato y la misma interpretación semántica que la sesión en
     * vivo. Sin esto, un QR con una URL escaneado desde una foto no ofrecería "Abrir enlace" y el
     * mismo código daría resultados distintos según de dónde viniera.
     */
    private fun List<Barcode>.toDetections(
        engineId: ScannerEngineId,
        startedAtMillis: Long,
        request: ScanRequest,
    ): List<Detection> {
        val now = time.nowMillis()
        return filter { it.format in request.formats }
            .map { barcode ->
                val interpreted = if (barcode.valueType is BarcodeValueType.Text) {
                    barcode.copy(valueType = BarcodeValueParser.parse(barcode.rawValue, barcode.format))
                } else {
                    barcode
                }
                Detection.of(
                    barcode = interpreted,
                    engineId = engineId,
                    detectedAtMillis = now,
                    latencyMillis = now - startedAtMillis,
                )
            }
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
