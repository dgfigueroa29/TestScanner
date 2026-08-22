package com.whyscan.core.scanner.testing

import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.CameraControlEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ImageDecodingEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.TextInputEngine
import com.whyscan.core.scanner.capability
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
 * La heredan desde `commonTest` los motores que se pueden instanciar sin dispositivo —el de entrada
 * manual— y **todos los decoradores del dominio**, incluida la cadena completa que llega al
 * ViewModel.
 *
 * Los motores de cámara no la heredan, y es una decisión y no un olvido: exigirían un emulador en
 * CI, y un test que nunca se ejecuta da una red de seguridad falsa. Lo que los cubre sin dispositivo
 * está en `docs/ROADMAP.md`.
 *
 * Lleva `@Suppress("TooManyFunctions")` porque una batería de contrato **es** una lista de
 * comprobaciones pequeñas e independientes: partirla en varias clases para bajar la cuenta haría
 * que un motor tuviera que heredar de tres sitios para quedar cubierto.
 */
@Suppress("TooManyFunctions")
abstract class BarcodeScannerEngineContractTest {

    /** Instancia nueva del motor bajo prueba. Se llama una vez por test. */
    abstract fun createEngine(): BarcodeScannerEngine

    /** Petición con la que se ejercita la sesión. Se puede ajustar por motor. */
    open fun request(): ScanRequest = ScanRequest()

    /**
     * Si una sesión de este motor produce al menos una detección dentro de un test unitario.
     *
     * Es `false` por defecto porque un motor de cámara necesita hardware. Los que sí pueden
     * — entrada manual, decodificación de imagen, decoradores sobre un motor falso — lo ponen a
     * `true`; entonces los asertos sobre [ScanEvent.Detected] se ejecutan de verdad en lugar de
     * saltarse.
     */
    open val producesDetection: Boolean = false

    /**
     * Empuja al motor para que detecte, si lo necesita.
     *
     * Por defecto no hace nada: hay motores que producen la detección solos en cuanto arranca la
     * sesión. El de entrada manual, en cambio, espera a que alguien le entregue un valor.
     */
    open suspend fun triggerDetection(engine: BarcodeScannerEngine) = Unit

    @Test
    fun `el_id_del_motor_coincide_con_el_de_su_descriptor`() {
        val engine = createEngine()
        assertEquals(
            engine.id,
            engine.descriptor.id,
            "El descriptor de ${engine.id} anuncia otro identificador",
        )
    }

    @Test
    fun `el_descriptor_declara_al_menos_un_formato_una_fuente_y_una_plataforma`() {
        val engine = createEngine()
        val capabilities = engine.descriptor.capabilities
        assertTrue(capabilities.supportedFormats.isNotEmpty(), "sin formatos declarados")
        assertTrue(capabilities.sources.isNotEmpty(), "sin fuentes declaradas")
        assertTrue(engine.descriptor.platforms.isNotEmpty(), "sin plataformas declaradas")
    }

    @Test
    fun `un_motor_con_UI_propia_no_promete_control_de_camara`() {
        // Si prometiera linterna, el selector lo elegiría ante peticiones que la exigen y fallaría
        // en tiempo de ejecución, justo lo que las capacidades declarativas deben evitar.
        val capabilities = createEngine().descriptor.capabilities
        if (capabilities.providesOwnUi) {
            assertTrue(!capabilities.supportsTorch, "UI propia y linterna a la vez")
            assertTrue(!capabilities.supportsZoom, "UI propia y zoom a la vez")
        }
    }

    @Test
    fun `si_declara_linterna_o_zoom_alguien_en_la_cadena_los_implementa`() {
        // Es la clase de fallo que más veces ha aparecido en este proyecto: algo declarado que
        // ningún código cumple. La UI muestra el control leyendo el descriptor, así que un motor
        // que promete linterna sin implementarla pinta un botón que no hace nada.
        val engine = createEngine()
        val capabilities = engine.descriptor.capabilities

        if (capabilities.supportsTorch || capabilities.supportsZoom) {
            assertTrue(
                engine.capability<CameraControlEngine>() != null,
                "${engine.id} declara controles de cámara pero no implementa CameraControlEngine",
            )
        }
    }

    @Test
    fun `si_declara_imagen_estatica_alguien_en_la_cadena_sabe_decodificarla`() {
        // Sin esto, el selector elegiría el motor para una petición de imagen y el caso de uso lo
        // descartaría después por no ser `ImageDecodingEngine`: una elección que no lleva a nada.
        val engine = createEngine()

        if (ScanSource.StaticImage in engine.descriptor.capabilities.sources) {
            assertTrue(
                engine.capability<ImageDecodingEngine>() != null,
                "${engine.id} declara la fuente imagen pero no implementa ImageDecodingEngine",
            )
        }
    }

    @Test
    fun `si_declara_entrada_manual_alguien_en_la_cadena_la_acepta`() {
        val engine = createEngine()

        if (ScanSource.ManualInput in engine.descriptor.capabilities.sources) {
            assertTrue(
                engine.capability<TextInputEngine>() != null,
                "${engine.id} declara entrada manual pero no implementa TextInputEngine",
            )
        }
    }

    @Test
    fun `availability_es_idempotente_y_sin_efectos_secundarios`() = runTest {
        val engine = createEngine()
        val first = engine.availability()
        val second = engine.availability()
        assertEquals(first, second, "availability() de ${engine.id} no es estable entre llamadas")
    }

    @Test
    fun `el_primer_evento_de_la_sesion_es_SessionStarted_con_su_propio_id`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        val first = engine.scan(request()).take(1).toList().single()

        val started = first as? ScanEvent.SessionStarted
        assertTrue(started != null, "el primer evento fue $first en lugar de SessionStarted")
        assertEquals(engine.id, started.engineId)
    }

    @Test
    fun `cancelar_la_sesion_no_propaga_excepciones`() = runTest {
        val engine = createEngine()
        if (engine.availability() !is EngineAvailability.Available) return@runTest

        // `take(1)` cancela el Flow tras el primer evento: si el motor no libera bien sus
        // recursos en awaitClose/finally, esto revienta.
        engine.scan(request()).take(1).toList()
    }

    @Test
    fun `toda_deteccion_reporta_un_formato_declarado_en_capabilities`() = runTest {
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
    fun `la_sesion_termina_with_SessionEnded_cuando_acaba_por_si_misma`() = runTest {
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
        if (!producesDetection) return null

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
