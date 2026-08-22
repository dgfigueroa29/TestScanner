package com.whyscan.core.domain.scan

import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Corre **varios motores en paralelo sobre la misma petición** y fusiona sus eventos.
 *
 * Es el objetivo G5 del producto: responder, con datos del dispositivo real, a "¿qué motor
 * funciona mejor para este código?". Cada [com.whyscan.core.model.Detection] ya viene firmada
 * con su `engineId` y su latencia, así que la fusión no pierde información: el consumidor puede
 * agrupar por motor y contrastar quién detectó qué y cuándo.
 *
 * Se distingue de [FallbackScannerEngine] en la intención, y por eso son clases separadas y no una
 * con un flag: el fallback busca **un** resultado probando en orden; el comparador busca **todos**
 * los resultados a la vez. Mezclarlos daría un objeto que hace dos cosas y ninguna con claridad.
 *
 * Aviso de uso: varios motores compitiendo por la cámara no es viable en todas las plataformas.
 * El consumidor debe combinarlo con motores que lean de una fuente compartible — imagen estática o
 * entrada manual — o con plataformas que permitan multiplexar frames.
 */
class ComparingScannerEngine(
    private val engines: List<BarcodeScannerEngine>,
) : BarcodeScannerEngine {

    init {
        require(engines.size >= MIN_ENGINES) { "Comparar exige al menos $MIN_ENGINES motores" }
    }

    override val id: ScannerEngineId = engines.first().id

    override val descriptor: ScannerEngineDescriptor = engines.first().descriptor

    /** Disponible mientras al menos dos motores lo estén: con uno solo no hay nada que comparar. */
    override suspend fun availability(): EngineAvailability {
        val usable = engines.count { it.availability().isUsable }
        return if (usable >= MIN_ENGINES) {
            EngineAvailability.Available
        } else {
            EngineAvailability.Unsupported(
                "Se necesitan $MIN_ENGINES motores disponibles para comparar, hay $usable",
            )
        }
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        val usable = engines.filter { it.availability().isUsable }

        if (usable.size < MIN_ENGINES) {
            emit(
                ScanEvent.Failed(
                    ScanError.EngineUnavailable(
                        engineId = null,
                        reason = "No hay suficientes motores disponibles para comparar",
                    ),
                ),
            )
            emit(ScanEvent.SessionEnded(id))
            return@flow
        }

        emit(ScanEvent.SessionStarted(id))

        // Los SessionStarted/SessionEnded de cada motor se suprimen: desde fuera la comparación es
        // una única sesión, igual que en la cadena de fallback.
        val merged = usable
            .map { engine -> engine.scan(request).filter { it.isNotSessionBoundary() } }
            .merge()

        merged.collect { emit(it) }

        emit(ScanEvent.SessionEnded(id))
    }

    private fun ScanEvent.isNotSessionBoundary(): Boolean =
        this !is ScanEvent.SessionStarted && this !is ScanEvent.SessionEnded

    private companion object {
        const val MIN_ENGINES = 2
    }
}

/** Envuelve dos o más motores para ejecutarlos en paralelo y comparar sus resultados. */
fun List<BarcodeScannerEngine>.comparing(): BarcodeScannerEngine = ComparingScannerEngine(this)
