package com.testscanner.engines.manual

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.TextInputEngine
import com.testscanner.core.scanner.testing.BarcodeScannerEngineContractTest

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
