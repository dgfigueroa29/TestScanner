package com.testscanner.core.domain.scan

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.DecoratingScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Descarta las detecciones cuyo formato no está entre los solicitados.
 *
 * Existe porque el filtrado por formato es **desigual entre motores**: unos aceptan una máscara de
 * formatos en su configuración, otros devuelven todo lo que ven. Aplicar el filtro aquí garantiza
 * el mismo comportamiento observable en los ocho motores, sin que cada uno lo reimplemente — y sin
 * que el ViewModel tenga que desconfiar del motor.
 */
class FormatFilteringScannerEngine(
    override val delegate: BarcodeScannerEngine,
) : DecoratingScannerEngine {

    override val id: ScannerEngineId = delegate.id

    override val descriptor: ScannerEngineDescriptor = delegate.descriptor

    override suspend fun availability(): EngineAvailability = delegate.availability()

    override fun scan(request: ScanRequest): Flow<ScanEvent> =
        delegate.scan(request).map { event ->
            when (event) {
                is ScanEvent.Detected -> event.copy(
                    detections = event.detections.filter { it.barcode.format in request.formats },
                )

                else -> event
            }
        }
}

/** Envuelve el motor para que respete estrictamente los formatos del [ScanRequest]. */
fun BarcodeScannerEngine.filteringFormats(): BarcodeScannerEngine =
    FormatFilteringScannerEngine(this)
