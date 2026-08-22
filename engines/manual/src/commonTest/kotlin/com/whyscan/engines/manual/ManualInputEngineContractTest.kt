package com.whyscan.engines.manual

import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.TextInputEngine
import com.whyscan.core.scanner.testing.BarcodeScannerEngineContractTest

/**
 * El motor de entrada manual pasando la suite de contrato.
 *
 * Es el primer cliente de la suite y su razón de existir en la Fase 1: valida el contrato del SPI
 * antes de que exista ningún motor de cámara, de modo que los de la Fase 2 hereden una batería ya
 * probada en lugar de estrenarla.
 */
class ManualInputEngineContractTest : BarcodeScannerEngineContractTest() {

    override fun createEngine(): BarcodeScannerEngine = ManualInputScannerEngine()

    override fun request(): ScanRequest = ScanRequest(source = ScanSource.ManualInput)

    override val producesDetection: Boolean = true

    override suspend fun triggerDetection(engine: BarcodeScannerEngine) {
        (engine as TextInputEngine).submit("4006381333931")
    }
}
