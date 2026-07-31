package com.testscanner.core.domain.scan

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.DecoratingScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

/**
 * Hace cumplir los límites que el [ScanRequest] pide y que los motores **no aplican por su cuenta**:
 * cuántos códigos por frame y si la sesión sigue tras la primera lectura.
 *
 * Es el mismo argumento que justifica [FormatFilteringScannerEngine]: los motores son desiguales.
 * El de entrada manual respeta `continuous` porque lo implementa a mano; ML Kit y Vision dejan la
 * cámara corriendo y siguen emitiendo para siempre, porque para ellos "parar" es que el consumidor
 * cancele. Sin este decorador, el interruptor de "escaneo continuo" de la UI no hacía nada con los
 * motores de cámara y una sesión puntual no terminaba nunca.
 *
 * Los dos límites viven juntos y no en dos decoradores porque son la misma regla mirada dos veces:
 * *el motor reportó más de lo que se le pidió*. Separarlos obligaría a recorrer el stream dos veces
 * para expresar una sola idea.
 */
class RequestLimitsScannerEngine(
    override val delegate: BarcodeScannerEngine,
) : DecoratingScannerEngine {

    override val id: ScannerEngineId = delegate.id

    override val descriptor: ScannerEngineDescriptor = delegate.descriptor

    override suspend fun availability(): EngineAvailability = delegate.availability()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scan(request: ScanRequest): Flow<ScanEvent> {
        if (request.continuous && request.allowMultiple) return delegate.scan(request)

        var finished = false

        return delegate.scan(request).transformWhile { event ->
            val limited = if (event is ScanEvent.Detected && !request.allowMultiple) {
                // Recortar a una sola lectura por frame, no descartar el evento entero: el usuario
                // pidió un código, y devolverle cero porque el motor vio tres sería absurdo.
                event.copy(detections = event.detections.take(1))
            } else {
                event
            }

            emit(limited)

            val detected = limited is ScanEvent.Detected && limited.detections.isNotEmpty()
            if (detected && !request.continuous) {
                // Cerrar la sesión aquí y no dejar que el consumidor cancele: el contrato del SPI
                // dice que el último evento es SessionEnded, y cancelar desde fuera no lo emitiría.
                if (!finished) {
                    finished = true
                    emit(ScanEvent.SessionEnded(id))
                }
                false
            } else {
                // Un SessionEnded del motor también cierra: no hay nada más que esperar.
                limited !is ScanEvent.SessionEnded
            }
        }
    }
}

/** Envuelve el motor para que respete `allowMultiple` y `continuous` del [ScanRequest]. */
fun BarcodeScannerEngine.enforcingRequestLimits(): BarcodeScannerEngine =
    RequestLimitsScannerEngine(this)
