package com.testscanner.core.scanner

import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId

/**
 * Eventos de una sesión de escaneo.
 *
 * Un fallo es un **elemento del stream**, no una excepción lanzada: un frame corrupto no debe
 * apagar la cámara. Solo tras un [ScanError.isFatal] la sesión termina con [SessionEnded].
 *
 * ### Por qué casi todos los eventos llevan el motor
 * En una sesión normal el motor es obvio: hay uno solo. Pero el comparador
 * ([com.testscanner.core.model.ScannerEngineId] múltiples en paralelo) fusiona los streams de
 * varios motores en uno, y ahí un evento sin autor es un dato que se pierde. [Detected] ya lo
 * llevaba dentro de cada [Detection]; [FrameAnalyzed] y [Failed] no, y por eso las métricas de
 * frames y de fallos por motor nunca se podían calcular.
 */
sealed interface ScanEvent {

    /** El motor al que pertenece el evento, o `null` si no lo produjo ninguno en concreto. */
    val engineId: ScannerEngineId?

    /** La sesión arrancó: cámara abierta y motor listo. */
    data class SessionStarted(override val engineId: ScannerEngineId) : ScanEvent

    /** Uno o más códigos reconocidos en el mismo frame. */
    data class Detected(val detections: List<Detection>) : ScanEvent {
        // Cada detección ya viene firmada; el evento hereda la autoría de la primera, que en la
        // práctica es siempre la misma porque un motor no reporta lecturas ajenas.
        override val engineId: ScannerEngineId? get() = detections.firstOrNull()?.engineId
    }

    /**
     * Un frame se analizó sin resultado. Alimenta las métricas de FPS que sustentan la comparación
     * entre motores: un motor que analiza 30 frames por segundo y no lee nada dice algo distinto
     * que uno que analiza 3.
     */
    data class FrameAnalyzed(
        override val engineId: ScannerEngineId,
        val analyzedAtMillis: Long,
    ) : ScanEvent

    /**
     * Error dentro de la sesión. Si [ScanError.isFatal], le sigue [SessionEnded].
     *
     * [engineId] es `null` cuando el fallo no es de un motor sino de la sesión entera — que no haya
     * ninguno disponible, o que venza el plazo de la cadena completa.
     */
    data class Failed(
        val error: ScanError,
        override val engineId: ScannerEngineId? = null,
    ) : ScanEvent

    /**
     * La cadena de fallback cambió de motor. La UI lo comunica al usuario en lugar de mostrar un
     * error: la degradación debe ser visible pero no alarmante.
     */
    data class EngineSwitched(
        val from: ScannerEngineId,
        val to: ScannerEngineId,
        val reason: ScanError,
    ) : ScanEvent {
        override val engineId: ScannerEngineId get() = from
    }

    /** La sesión terminó: cámara liberada. Siempre es el último evento. */
    data class SessionEnded(override val engineId: ScannerEngineId) : ScanEvent
}
