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
import kotlinx.coroutines.flow.flow

/**
 * Recorre una cadena de motores: si el primero no está disponible o falla de forma fatal, pasa al
 * siguiente sin que la sesión se interrumpa para el usuario (G4).
 *
 * Es un **decorador** y no lógica dentro del ViewModel. La consecuencia práctica es que la
 * degradación se testea entera en `commonTest` con motores falsos, sin cámara ni dispositivo.
 *
 * Contrato de emisión:
 * - Los [ScanEvent.SessionEnded] de los motores internos se **suprimen**; solo se emite uno al
 *   final. Desde fuera, la cadena entera es una única sesión.
 * - Los errores no fatales se propagan tal cual: un frame corrupto no justifica degradar de motor.
 * - Cada cambio emite [ScanEvent.EngineSwitched], para que la UI lo comunique en lugar de mostrar
 *   un error.
 */
class FallbackScannerEngine(
    private val engines: List<BarcodeScannerEngine>,
) : DecoratingScannerEngine {

    init {
        require(engines.isNotEmpty()) { "La cadena de fallback necesita al menos un motor" }
    }

    private val primary: BarcodeScannerEngine = engines.first()

    /**
     * La cadena expone las capacidades del **primer motor**, igual que ya expone su descriptor.
     *
     * Lo destapó la suite de contrato al aplicarse a los decoradores: la cadena declaraba linterna
     * —heredada del descriptor del primero— y un `as? CameraControlEngine` sobre ella devolvía
     * `null`, porque quien la implementa es el motor de dentro. Declarar algo que nadie sirve es
     * justo lo que el contrato existe para impedir.
     *
     * Si la cadena degrada a otro motor, esto sigue apuntando al primero — la misma simplificación
     * que ya hace `descriptor`. No es un problema en la práctica porque la UI lee las capacidades
     * del motor **activo**, que conoce por `EngineSwitched`, no de la cadena.
     */
    override val delegate: BarcodeScannerEngine = primary

    override val id: ScannerEngineId = primary.id

    override val descriptor: ScannerEngineDescriptor = primary.descriptor

    /** La cadena está disponible si lo está cualquiera de sus motores. */
    override suspend fun availability(): EngineAvailability {
        var firstNonUsable: EngineAvailability? = null
        engines.forEach { engine ->
            val availability = engine.availability()
            if (availability.isUsable) return availability
            if (firstNonUsable == null) firstNonUsable = availability
        }
        return firstNonUsable ?: EngineAvailability.Unsupported("Cadena de fallback vacía")
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        var lastError: ScanError = ScanError.EngineUnavailable(
            engineId = primary.id,
            reason = "Ningún motor de la cadena pudo arrancar",
        )

        engines.forEachIndexed { index, engine ->
            val availability = engine.availability()

            if (availability.isUsable) {
                var fatalError: ScanError? = null

                engine.scan(request).collect { event ->
                    when {
                        event is ScanEvent.SessionEnded -> Unit
                        event is ScanEvent.Failed && event.error.isFatal -> fatalError = event.error
                        else -> emit(event)
                    }
                }

                val failure = fatalError
                if (failure == null) {
                    // El motor terminó su sesión limpiamente: la cadena termina con él.
                    emit(ScanEvent.SessionEnded(engine.id))
                    return@flow
                }
                lastError = failure
            } else {
                lastError = ScanError.EngineUnavailable(engine.id, availability.describe())
            }

            engines.getOrNull(index + 1)?.let { next ->
                emit(ScanEvent.EngineSwitched(from = engine.id, to = next.id, reason = lastError))
            }
        }

        emit(ScanEvent.Failed(lastError, engineId = engines.last().id))
        emit(ScanEvent.SessionEnded(engines.last().id))
    }
}

private fun EngineAvailability.describe(): String = when (this) {
    is EngineAvailability.Available -> "disponible"
    is EngineAvailability.RequiresPermission -> "falta el permiso de ${permission.displayName}"
    is EngineAvailability.RequiresDownload -> "requiere descargar su modelo"
    is EngineAvailability.Unsupported -> reason
    is EngineAvailability.NotImplemented -> "aún no implementado (fase $plannedPhase)"
    is EngineAvailability.Failed -> "falló al comprobar disponibilidad: $error"
}
