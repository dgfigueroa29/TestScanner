package com.testscanner.engines.zxingjava

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.testing.BarcodeScannerEngineContractTest

/**
 * El segundo motor **real** que pasa la suite de contrato, después del de entrada manual.
 *
 * Puede hacerlo porque no necesita cámara: es Java puro y decodifica archivos. Los de cámara siguen
 * cubiertos solo por lo declarativo, por la decisión de no exigir emulador en CI.
 */
class ZXingJavaEngineContractTest : BarcodeScannerEngineContractTest() {

    override fun createEngine(): BarcodeScannerEngine = ZXingJavaEngine()

    override fun request(): ScanRequest = ScanRequest(source = ScanSource.StaticImage)
}
