package com.testscanner.engines.manual

import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TextInputEngine
import com.testscanner.core.scanner.TimeProvider
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile

/**
 * Motor de entrada manual: el usuario teclea el código en lugar de apuntar con la cámara.
 *
 * Cumple tres funciones que justifican su existencia como motor de pleno derecho y no como un caso
 * especial de la UI:
 * 1. **Cierra la cadena de fallback.** Está disponible siempre, en las cuatro plataformas, sin
 *    permisos ni hardware. Nunca existe el estado "no se puede escanear nada" (G4).
 * 2. **Es el motor de referencia del SPI.** Al ser 100 % `commonMain` y determinista, sirve de
 *    banco de pruebas del contrato antes de que exista ningún motor de cámara.
 * 3. **Es útil de verdad.** Un código roto o mal impreso se resuelve tecleando el número.
 *
 * La entrada llega por [submit] ([TextInputEngine]) y no por el constructor porque la sesión es un
 * `Flow` frío de larga duración: el usuario puede introducir varios valores en modo continuo.
 */
class ManualInputScannerEngine(
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine, TextInputEngine {

    /**
     * Los valores tecleados van por un [Channel] y no por un `MutableSharedFlow`.
     *
     * Con un SharedFlow había una carrera real: la sesión se suscribe *después* de emitir
     * [ScanEvent.SessionStarted], así que un `submit()` hecho en cuanto la UI ve ese evento se
     * emitía sin suscriptores y se perdía en silencio — la sesión quedaba esperando para siempre.
     * Un Channel almacena el valor con independencia de si alguien está escuchando, de modo que
     * "escribo el código nada más abrirse la pantalla" funciona siempre.
     */
    private val submissions = Channel<String>(capacity = SUBMISSION_BUFFER)

    override val id: ScannerEngineId = ScannerEngineId.ManualInput

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.manualInput

    /** Siempre disponible: es justamente su razón de ser. */
    override suspend fun availability(): EngineAvailability = EngineAvailability.Available

    override suspend fun submit(value: String) {
        submissions.send(value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        val startedAtMillis = time.nowMillis()
        emit(ScanEvent.SessionStarted(id))

        emitAll(
            submissions.receiveAsFlow().transformWhile { value ->
                val events = eventsFor(value, request, startedAtMillis)
                events.forEach { emit(it) }
                // En modo puntual la sesión termina con la primera detección válida; en modo
                // continuo sigue aceptando valores hasta que el consumidor cancele.
                request.continuous || events.none { it is ScanEvent.Detected }
            },
        )

        emit(ScanEvent.SessionEnded(id))
    }

    private fun eventsFor(
        value: String,
        request: ScanRequest,
        startedAtMillis: Long,
    ): List<ScanEvent> {
        val barcode = ManualCodeInterpreter.interpret(value)
            ?: return listOf(ScanEvent.Failed(ScanError.DecodeFailed("El valor está vacío")))

        if (barcode.format !in request.formats) {
            return listOf(ScanEvent.Failed(ScanError.FormatRejected(barcode.format)))
        }

        val now = time.nowMillis()
        return listOf(
            ScanEvent.Detected(
                detections = listOf(
                    Detection.of(
                        barcode = barcode,
                        engineId = id,
                        detectedAtMillis = now,
                        latencyMillis = now - startedAtMillis,
                        source = ScanSource.ManualInput,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val SUBMISSION_BUFFER = 16
    }
}
