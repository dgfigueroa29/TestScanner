package com.whyscan.core.domain.scan

import com.whyscan.core.model.BarcodeValueType
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
 * Rellena [com.whyscan.core.model.Barcode.valueType] interpretando el contenido con
 * [BarcodeValueParser].
 *
 * Es la pieza que hace cumplir la decisión de ADR-0002: **el motor reporta el contenido y la
 * simbología; el dominio decide qué significa**. Si dejáramos la interpretación a cada SDK, el
 * mismo código leído con ML Kit tendría más información que leído con ZXing y la comparación entre
 * motores dejaría de ser justa.
 */
class SemanticParsingScannerEngine(
    override val delegate: BarcodeScannerEngine,
) : DecoratingScannerEngine {

    override val id: ScannerEngineId = delegate.id

    override val descriptor: ScannerEngineDescriptor = delegate.descriptor

    override suspend fun availability(): EngineAvailability = delegate.availability()

    override fun scan(request: ScanRequest): Flow<ScanEvent> =
        delegate.scan(request).map { event ->
            when (event) {
                is ScanEvent.Detected -> event.copy(
                    detections = event.detections.map { detection ->
                        val barcode = detection.barcode
                        if (barcode.valueType !is BarcodeValueType.Text) {
                            detection
                        } else {
                            detection.copy(
                                barcode = barcode.copy(
                                    valueType = BarcodeValueParser.parse(
                                        rawValue = barcode.rawValue,
                                        format = barcode.format,
                                    ),
                                ),
                            )
                        }
                    },
                )

                else -> event
            }
        }
}

/** Envuelve el motor para que el dominio interprete el contenido de cada detección. */
fun BarcodeScannerEngine.interpretingValues(): BarcodeScannerEngine =
    SemanticParsingScannerEngine(this)
