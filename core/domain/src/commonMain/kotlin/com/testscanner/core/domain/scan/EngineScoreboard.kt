package com.testscanner.core.domain.scan

import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.ScanEvent

/** Métricas acumuladas de un motor durante una sesión. */
data class EngineMetrics(
    val engineId: ScannerEngineId,
    val detections: Int = 0,
    /** Valores distintos leídos: dos lecturas del mismo código no cuentan dos veces. */
    val distinctValues: Set<String> = emptySet(),
    val framesAnalyzed: Int = 0,
    val transientFailures: Int = 0,
    val fatalFailures: Int = 0,
    val firstDetectionLatencyMillis: Long? = null,
    val totalLatencyMillis: Long = 0,
    val latencySamples: Int = 0,
) {
    val uniqueValues: Int get() = distinctValues.size

    /** Latencia media de detección, o `null` si el motor aún no detectó nada con latencia medida. */
    val averageLatencyMillis: Long?
        get() = if (latencySamples == 0) null else totalLatencyMillis / latencySamples
}

/**
 * Reduce un stream de [ScanEvent] a métricas por motor.
 *
 * Es una función pura sobre eventos, no un observador con estado escondido: alimentarlo con la
 * misma secuencia produce siempre el mismo marcador. Eso es lo que hace que la comparación entre
 * motores sea reproducible y testeable sin dispositivo.
 */
class EngineScoreboard private constructor(
    private val metrics: Map<ScannerEngineId, EngineMetrics>,
) {

    val entries: List<EngineMetrics>
        get() = metrics.values.sortedWith(
            compareByDescending<EngineMetrics> { it.uniqueValues }
                .thenBy { it.averageLatencyMillis ?: Long.MAX_VALUE }
                .thenBy { it.engineId.id },
        )

    /** El motor que más códigos distintos detectó; a igualdad, el más rápido. */
    val leader: EngineMetrics? get() = entries.firstOrNull { it.detections > 0 }

    operator fun get(engineId: ScannerEngineId): EngineMetrics? = metrics[engineId]

    fun reduce(event: ScanEvent): EngineScoreboard = when (event) {
        is ScanEvent.Detected -> reduceDetected(event)
        is ScanEvent.FrameAnalyzed -> this
        is ScanEvent.Failed -> this
        is ScanEvent.EngineSwitched -> this
        is ScanEvent.SessionStarted -> withEntry(event.engineId) { it }
        is ScanEvent.SessionEnded -> this
    }

    /**
     * Los eventos que no llevan motor —[ScanEvent.Failed], [ScanEvent.FrameAnalyzed]— se atribuyen
     * explícitamente. En una comparación en paralelo no hay forma de deducir de quién vienen, y
     * adivinarlo falsearía el marcador.
     */
    fun reduce(event: ScanEvent, attributedTo: ScannerEngineId): EngineScoreboard = when (event) {
        is ScanEvent.FrameAnalyzed -> withEntry(attributedTo) {
            it.copy(framesAnalyzed = it.framesAnalyzed + 1)
        }

        is ScanEvent.Failed -> withEntry(attributedTo) {
            if (event.error.isFatal) {
                it.copy(fatalFailures = it.fatalFailures + 1)
            } else {
                it.copy(transientFailures = it.transientFailures + 1)
            }
        }

        else -> reduce(event)
    }

    private fun reduceDetected(event: ScanEvent.Detected): EngineScoreboard =
        event.detections.fold(this) { scoreboard, detection ->
            scoreboard.withEntry(detection.engineId) { current ->
                current.copy(
                    detections = current.detections + 1,
                    distinctValues = current.distinctValues + detection.barcode.rawValue,
                    firstDetectionLatencyMillis = current.firstDetectionLatencyMillis
                        ?: detection.latencyMillis,
                    totalLatencyMillis = current.totalLatencyMillis +
                        (detection.latencyMillis ?: 0L),
                    latencySamples = current.latencySamples +
                        if (detection.latencyMillis != null) 1 else 0,
                )
            }
        }

    private fun withEntry(
        engineId: ScannerEngineId,
        transform: (EngineMetrics) -> EngineMetrics,
    ): EngineScoreboard {
        val current = metrics[engineId] ?: EngineMetrics(engineId)
        return EngineScoreboard(metrics + (engineId to transform(current)))
    }

    companion object {
        val Empty = EngineScoreboard(emptyMap())
    }
}
