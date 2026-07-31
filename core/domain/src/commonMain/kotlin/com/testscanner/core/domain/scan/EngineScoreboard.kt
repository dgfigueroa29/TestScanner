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

    /**
     * Frames analizados por cada código distinto leído, o `null` si aún no leyó ninguno.
     *
     * Es la medida de eficiencia que hace interesante la comparación: dos motores pueden leer lo
     * mismo, pero el que necesita 3 frames es mejor que el que necesita 90.
     */
    val framesPerDetection: Int?
        get() = if (uniqueValues == 0) null else framesAnalyzed / uniqueValues
}

/**
 * Reduce un stream de [ScanEvent] a métricas por motor.
 *
 * Es una función pura sobre eventos, no un observador con estado escondido: alimentarlo con la
 * misma secuencia produce siempre el mismo marcador. Eso es lo que hace que la comparación entre
 * motores sea reproducible y testeable sin dispositivo.
 *
 * La atribución sale del propio evento ([ScanEvent.engineId]). Antes había una sobrecarga que
 * recibía el motor por parámetro, porque `FrameAnalyzed` y `Failed` no lo llevaban: el resultado
 * era que en el comparador — donde nadie puede saber de quién viene cada evento de un stream
 * fusionado — los contadores de frames y de fallos quedaban siempre en cero.
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

        is ScanEvent.FrameAnalyzed -> withEntry(event.engineId) {
            it.copy(framesAnalyzed = it.framesAnalyzed + 1)
        }

        is ScanEvent.Failed -> reduceFailed(event)

        // Un cambio de motor no es mérito ni demérito de ninguno: lo que importa es lo que cada
        // uno leyó, y eso ya viene en sus propios eventos.
        is ScanEvent.EngineSwitched -> this

        is ScanEvent.SessionStarted -> withEntry(event.engineId) { it }

        is ScanEvent.SessionEnded -> this
    }

    private fun reduceFailed(event: ScanEvent.Failed): EngineScoreboard {
        // Un fallo sin motor es de la sesión entera — no hay ninguno disponible, o venció el plazo
        // de la cadena — y repartirlo entre los participantes falsearía el marcador.
        val engineId = event.engineId ?: return this

        return withEntry(engineId) {
            if (event.error.isFatal) {
                it.copy(fatalFailures = it.fatalFailures + 1)
            } else {
                it.copy(transientFailures = it.transientFailures + 1)
            }
        }
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
