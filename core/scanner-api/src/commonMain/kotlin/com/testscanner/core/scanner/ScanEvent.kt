package com.testscanner.core.scanner

import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId

/**
 * Eventos de una sesión de escaneo.
 *
 * Un fallo es un **elemento del stream**, no una excepción lanzada: un frame corrupto no debe
 * apagar la cámara. Solo tras un [ScanError.isFatal] la sesión termina con [SessionEnded].
 */
sealed interface ScanEvent {

    /** La sesión arrancó: cámara abierta y motor listo. */
    data class SessionStarted(val engineId: ScannerEngineId) : ScanEvent

    /** Uno o más códigos reconocidos en el mismo frame. */
    data class Detected(val detections: List<Detection>) : ScanEvent

    /**
     * Un frame se analizó sin resultado. Alimenta las métricas de FPS y latencia que sustentan la
     * comparación entre motores.
     */
    data class FrameAnalyzed(val analyzedAtMillis: Long) : ScanEvent

    /** Error dentro de la sesión. Si [ScanError.isFatal], le sigue [SessionEnded]. */
    data class Failed(val error: ScanError) : ScanEvent

    /**
     * La cadena de fallback cambió de motor. La UI lo comunica al usuario en lugar de mostrar un
     * error: la degradación debe ser visible pero no alarmante.
     */
    data class EngineSwitched(
        val from: ScannerEngineId,
        val to: ScannerEngineId,
        val reason: ScanError,
    ) : ScanEvent

    /** La sesión terminó: cámara liberada. Siempre es el último evento. */
    data class SessionEnded(val engineId: ScannerEngineId) : ScanEvent
}
