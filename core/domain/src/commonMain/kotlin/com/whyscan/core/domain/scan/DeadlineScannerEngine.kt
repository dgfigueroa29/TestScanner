package com.whyscan.core.domain.scan

import com.whyscan.core.model.ScanError
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.DecoratingScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cierra la sesión cuando vence [ScanRequest.timeoutMillis].
 *
 * `timeoutMillis` y [ScanError.Timeout] existían en el modelo desde la Fase 1 pero **ningún código
 * los cumplía**: eran una promesa del API sin implementación. Este decorador la salda.
 *
 * ### Qué significa exactamente el plazo
 * Es una fecha límite para **la sesión entera**, no un tiempo máximo entre detecciones. En modo
 * puntual la sesión termina sola con la primera lectura, así que el plazo solo actúa si no se lee
 * nada. En modo continuo acota la duración total, que es lo que se quiere cuando alguien pide
 * "escaneá durante 30 segundos".
 *
 * Se aplica **sobre la cadena completa**, no sobre cada motor: si el plazo fuera por motor, una
 * cadena de tres tardaría el triple de lo que el usuario pidió.
 */
class DeadlineScannerEngine(
    override val delegate: BarcodeScannerEngine,
) : DecoratingScannerEngine {

    override val id: ScannerEngineId = delegate.id

    override val descriptor: ScannerEngineDescriptor = delegate.descriptor

    override suspend fun availability(): EngineAvailability = delegate.availability()

    override fun scan(request: ScanRequest): Flow<ScanEvent> {
        val timeout = request.timeoutMillis ?: return delegate.scan(request)

        return channelFlow {
            val session = launch {
                delegate.scan(request).collect { send(it) }
            }

            val finishedOnTime = withTimeoutOrNull(timeout) { session.join() }

            if (finishedOnTime == null) {
                // Cancelar la sesión antes de emitir: así la cámara ya está liberada cuando el
                // consumidor recibe el SessionEnded, y no al revés.
                session.cancel()
                // Sin engineId: el plazo es de la sesión entera, no de un motor concreto.
                send(ScanEvent.Failed(ScanError.Timeout))
                send(ScanEvent.SessionEnded(id))
            }

            // Sin `awaitClose`: aquí el productor termina por sí mismo. Ponerlo dejaría el
            // `channelFlow` esperando un cierre que nunca llega y la sesión no completaría nunca.
            // Si es el consumidor quien cancela, la concurrencia estructurada cancela `session`.
        }
    }
}

/** Envuelve el motor para que respete el plazo del [ScanRequest], si lo trae. */
fun BarcodeScannerEngine.withDeadline(): BarcodeScannerEngine = DeadlineScannerEngine(this)
