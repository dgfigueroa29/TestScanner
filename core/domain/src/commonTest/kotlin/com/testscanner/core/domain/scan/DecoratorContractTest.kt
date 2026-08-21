package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.FakeTimeProvider
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.testing.BarcodeScannerEngineContractTest

/**
 * Un decorador **también es un motor**, así que debe pasar el mismo contrato que envuelve.
 *
 * No es una formalidad. Los tres fallos de contrato que ha tenido este proyecto estaban en
 * decoradores y no en motores: un `awaitClose` que impedía que el `Flow` terminara, la supresión de
 * `SessionEnded` en la cadena de fallback y unos límites de petición que dejaban la sesión abierta
 * para siempre. Los motores de verdad necesitan un dispositivo para ejercitarse; los decoradores
 * corren aquí, sobre un motor falso, y son justo donde el contrato se rompe.
 */
private fun triggeringEngine() = FakeScannerEngine(
    id = ScannerEngineId.ManualInput,
    events = listOf(ScanEvent.Detected(listOf(FakeScannerEngine.detection(ScannerEngineId.ManualInput)))),
)

class FormatFilteringContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = triggeringEngine().filteringFormats()
    override val producesDetection: Boolean = true
}

class SemanticParsingContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = triggeringEngine().interpretingValues()
    override val producesDetection: Boolean = true
}

class RequestLimitsContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = triggeringEngine().enforcingRequestLimits()
    override val producesDetection: Boolean = true
}

/** Sin plazo declarado el decorador es transparente; con plazo se prueba aparte. */
class DeadlineContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = triggeringEngine().withDeadline()
    override val producesDetection: Boolean = true
}

class DeadlineWithTimeoutContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = triggeringEngine().withDeadline()
    override fun request(): ScanRequest = ScanRequest(timeoutMillis = 10_000)
    override val producesDetection: Boolean = true
}

/**
 * Cada `scan()` arranca con la memoria de repeticiones vacía, así que la primera lectura siempre
 * pasa: el reloj puede quedarse parado en cero sin que el contrato deje de cumplirse.
 */
class DistinctDetectionsContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine =
        triggeringEngine().suppressingRepeats(FakeTimeProvider())

    override val producesDetection: Boolean = true
}

class FallbackContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine =
        FallbackScannerEngine(listOf(triggeringEngine()))

    override val producesDetection: Boolean = true
}

/**
 * La cadena completa tal y como la monta `StartScanSessionUseCase`.
 *
 * Es la que llega de verdad al ViewModel, y la única forma de saber que apilar cuatro decoradores
 * no rompe lo que cada uno respeta por separado.
 */
class FullChainContractTest : BarcodeScannerEngineContractTest() {
    override fun createEngine(): BarcodeScannerEngine = FallbackScannerEngine(
        listOf(triggeringEngine().filteringFormats().enforcingRequestLimits().interpretingValues()),
    ).withDeadline().suppressingRepeats(FakeTimeProvider())

    override val producesDetection: Boolean = true
}
