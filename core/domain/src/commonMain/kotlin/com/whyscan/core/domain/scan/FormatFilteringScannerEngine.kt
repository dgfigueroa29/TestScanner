package com.whyscan.core.domain.scan

import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.DecoratingScannerEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerEngineDescriptor
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
