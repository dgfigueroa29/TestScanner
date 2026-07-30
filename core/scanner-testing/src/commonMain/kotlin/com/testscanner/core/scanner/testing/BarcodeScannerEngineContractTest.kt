package com.testscanner.core.scanner.testing

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batería que **todo** motor debe pasar. Cada implementación hereda esta clase y aporta su factory.
 *
 * Es la pieza que convierte "añadir un motor" en una operación con red de seguridad: verifica que
 * lo que el motor *declara* en su descriptor coincide con lo que *hace*, que respeta el ciclo de
 * vida de la sesión y que la cancelación libera recursos. Sin esto, las capacidades declarativas
 * — de las que dependen el selector y la UI entera — serían una promesa sin comprobar (ADR-0002).
 *
 * Los motores con cámara real la heredan desde `androidTest`/`iosTest`; los que son `commonMain`
 * puro, desde `commonTest`.
 */
abstract class BarcodeScannerEngineContractTest {

    /** Instancia nueva del motor bajo prueba. Se llama una vez por test. */
    abstract fun createEngine(): BarcodeScannerEngine

    /** Petición con la que se ejercita la sesión. Se puede ajustar por motor. */
    open fun request(): ScanRequest = ScanRequest()

    /**
     * Si el motor puede forzarse a detectar desde un test unitario.
     *
     * Es `false` por defecto porque un motor de cámara necesita hardware. Los que sí pueden
     * — entrada manual, decodificación de imagen — lo ponen a `true` y sobrescriben
     * [triggerDetection]; entonces los asertos sobre [ScanEvent.Detected] se ejecutan de verdad en
     * lugar de saltarse.
     */
    open val canTriggerDetection: Boolean = false

    /** Provoca al menos una detección en el motor, ya arrancado. Ver [canTriggerDetection]. */
    open suspend fun triggerDetection(engine: BarcodeScannerEngine): Unit =
        error("El motor declara canTriggerDetection = true pero no implementa triggerDetection()")

    @Test
    fun `el id del motor coincide con el de su descriptor`() {
        val engine = createEngine()
        assertEquals(
            engine.id,
            engine.descriptor.id,
            "El descriptor de ${engine.id} anuncia otro identificador",
        )
    }

    @Test
    fun `el descriptor declara al menos un formato, una fuente y una plataforma`() {
        val engine = createEngine()
        val capabilities = engine.descriptor.capabilities
        assertTrue(capabilities.supportedFormats.isNotEmpty(), "sin formatos declarados")
        assertTrue(capabilities.sources.isNotEmpty(), "sin fuentes declaradas")
        assertTrue(engine.descriptor.platforms.isNotEmpty(), "sin plataformas declaradas")
    }

    @Test
    fun `un motor con UI propia no promete control de camara`() {
        // Si prometiera linterna, el selector lo elegiría ante peticiones que la exigen y fallaría
        // en tiempo de ejecución, justo lo que las capacidades declarativas deben evitar.
        val capabilities = createEngine().descriptor.capabilities
        if (capabilities.providesOwnUi) {
            assertTrue(!capabilities.supportsTorch, "UI propia y linterna a la vez")
            assertTrue(!capabilities.supportsZoom, "UI propia y zoom a la vez")
        }
    }

    @Test
    fun `availability es idempotente y sin efectos secundarios`() = runTest {
        val engine = createEngine()
        val first = engine.availability()
        val second = engine.availability()
        assertEquals(first, second, "availability() de ${engine.id} no es estable entre llamadas")
    }

    @Test
    fun `el primer evento de la sesion es SessionStarted con su propio id`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        val first = engine.scan(request()).take(1).toList().single()

        val started = first as? ScanEvent.SessionStarted
        assertTrue(started != null, "el primer evento fue $first en lugar de SessionStarted")
        assertEquals(engine.id, started.engineId)
    }

    @Test
    fun `cancelar la sesion no propaga excepciones`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        // `take(1)` cancela el Flow tras el primer evento: si el motor no libera bien sus
        // recursos en awaitClose/finally, esto revienta.
        engine.scan(request()).take(1).toList()
    }

    @Test
    fun `toda deteccion reporta un formato declarado en capabilities`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        val supported = engine.descriptor.capabilities.supportedFormats
        val events = collectSessionWithDetection(engine) ?: return@runTest

        events.filterIsInstance<ScanEvent.Detected>()
            .flatMap { it.detections }
            .forEach { detection ->
                assertTrue(
                    detection.barcode.format in supported,
                    "${engine.id} reportó ${detection.barcode.format}, que no declara soportar",
                )
                assertEquals(
                    engine.id,
                    detection.engineId,
                    "una detección de ${engine.id} viene firmada por otro motor",
                )
            }
    }

    @Test
    fun `la sesion termina con SessionEnded cuando acaba por si misma`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        val events = collectSessionWithDetection(engine) ?: return@runTest

        assertTrue(
            events.lastOrNull() is ScanEvent.SessionEnded,
            "el último evento fue ${events.lastOrNull()} en lugar de SessionEnded",
        )
    }

    /**
     * Recorre una sesión completa provocando una detección. Devuelve `null` si el motor no puede
     * forzarse desde un test unitario, y entonces el test que la usa se salta a sí mismo.
     */
    private suspend fun collectSessionWithDetection(
        engine: BarcodeScannerEngine,
    ): List<ScanEvent>? {
        if (!canTriggerDetection) return null

        val events = mutableListOf<ScanEvent>()
        var triggered = false

        engine.scan(request()).collect { event ->
            events += event
            if (event is ScanEvent.SessionStarted && !triggered) {
                triggered = true
                triggerDetection(engine)
            }
        }
        return events
    }
}
