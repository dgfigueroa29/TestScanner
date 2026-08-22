package com.testscanner.core.domain.scan

import com.testscanner.core.domain.FakeScannerEngine
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.FakeTimeProvider
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El decorador que arregla el defecto que llenaba el historial de repeticiones.
 *
 * Los tests están escritos en términos de *lo que ve el usuario* —cuántas veces se le reporta un
 * código— y no de llamadas internas: es lo que hace que sigan valiendo si la implementación cambia
 * de mapa a lista o de ventana a otra política.
 */
class DistinctDetectionsScannerEngineTest {

    private val engineId = ScannerEngineId.MlKitCameraX

    @Test
    fun `el_mismo_codigo_dentro_de_la_ventana_se_reporta_una_sola_vez`() = runTest {
        // Es el caso real: un QR sostenido delante de la lente durante medio segundo a 30 fps.
        val time = FakeTimeProvider()
        val engine = scripted(time, List(FRAMES_HELD) { 33L to detected("https://scanly.app") })

        val values = engine.suppressingRepeats(time).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("https://scanly.app"), values)
    }

    @Test
    fun `pasada_la_ventana_el_mismo_codigo_vuelve_a_leerse`() = runTest {
        // Apartar el código y volver a presentarlo **es** un caso de uso: contar unidades iguales
        // en un inventario. Si esto fallara, el decorador habría cambiado un defecto por otro peor.
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(
                0L to detected("7790001001234"),
                WINDOW_MILLIS to detected("7790001001234"),
            ),
        )

        val values = engine.suppressingRepeats(time, WINDOW_MILLIS).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("7790001001234", "7790001001234"), values)
    }

    @Test
    fun `dos_codigos_distintos_en_el_mismo_frame_pasan_los_dos`() = runTest {
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(0L to ScanEvent.Detected(listOf(detection("uno"), detection("dos")))),
        )

        val values = engine.suppressingRepeats(time).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("uno", "dos"), values)
    }

    @Test
    fun `un_frame_con_uno_nuevo_y_uno_repetido_reporta_solo_el_nuevo`() = runTest {
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(
                0L to detected("viejo"),
                33L to ScanEvent.Detected(listOf(detection("viejo"), detection("nuevo"))),
            ),
        )

        val values = engine.suppressingRepeats(time).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("viejo", "nuevo"), values)
    }

    @Test
    fun `un_frame_entero_repetido_no_emite_ningun_evento`() = runTest {
        // Emitir `Detected` con la lista vacía sería mentir: le diría al consumidor que hubo una
        // lectura sin resultados, que es un estado distinto y que no ha ocurrido.
        val time = FakeTimeProvider()
        val engine = scripted(time, listOf(0L to detected("x"), 33L to detected("x")))

        val events = engine.suppressingRepeats(time).scan(ScanRequest()).toList()

        assertEquals(1, events.count { it is ScanEvent.Detected })
        assertTrue(events.filterIsInstance<ScanEvent.Detected>().none { it.detections.isEmpty() })
    }

    @Test
    fun `el_mismo_valor_en_otro_formato_es_otro_codigo`() = runTest {
        // Un producto con su EAN-13 impreso y un QR al lado que codifica el mismo número son dos
        // códigos del mundo real. Deduplicar solo por valor perdería uno de los dos.
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(
                0L to detected("7790001001234", BarcodeFormat.Ean13),
                33L to detected("7790001001234", BarcodeFormat.QrCode),
            ),
        )

        val values = engine.suppressingRepeats(time).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("7790001001234", "7790001001234"), values)
    }

    @Test
    fun `sostener_el_codigo_no_empuja_la_ventana_hacia_adelante`() = runTest {
        // Solo la lectura que pasa el filtro renueva la marca de tiempo. Si la renovaran también las
        // suprimidas, un código que nunca se aparta de la lente no volvería a leerse jamás: cada
        // frame correría la ventana. Aquí hay frames cada 500 ms durante toda la ventana y justo
        // después toca volver a leer.
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(
                0L to detected("x"),
                500L to detected("x"),
                500L to detected("x"),
                500L to detected("x"),
                500L to detected("x"),
            ),
        )

        val values = engine.suppressingRepeats(time, WINDOW_MILLIS).scan(ScanRequest()).detectedValues()

        assertEquals(listOf("x", "x"), values)
    }

    @Test
    fun `los_eventos_que_no_son_lecturas_pasan_intactos`() = runTest {
        val time = FakeTimeProvider()
        val engine = scripted(
            time,
            listOf(
                0L to ScanEvent.FrameAnalyzed(engineId, analyzedAtMillis = 1L),
                0L to detected("x"),
                33L to detected("x"),
            ),
        )

        val events = engine.suppressingRepeats(time).scan(ScanRequest()).toList()

        // SessionStarted, FrameAnalyzed, la única lectura y SessionEnded.
        assertEquals(1, events.count { it is ScanEvent.FrameAnalyzed })
        assertTrue(events.first() is ScanEvent.SessionStarted)
        assertTrue(events.last() is ScanEvent.SessionEnded)
    }

    @Test
    fun `cada_sesion_empieza_sin_memoria`() = runTest {
        // Volver a abrir la pantalla y encontrarse con que el primer código no se lee —porque se
        // leyó en la sesión anterior— sería el peor fallo posible de este decorador: justo el código
        // que el usuario está esperando. El estado vive dentro del `flow`, no en la clase.
        val time = FakeTimeProvider()
        val engine = scripted(time, listOf(0L to detected("x"))).suppressingRepeats(time)

        val primera = engine.scan(ScanRequest()).detectedValues()
        val segunda = engine.scan(ScanRequest()).detectedValues()

        assertEquals(listOf("x"), primera)
        assertEquals(listOf("x"), segunda)
    }

    private fun detection(value: String, format: BarcodeFormat = BarcodeFormat.QrCode): Detection =
        FakeScannerEngine.detection(engineId = engineId, value = value, format = format)

    private fun detected(value: String, format: BarcodeFormat = BarcodeFormat.QrCode): ScanEvent =
        ScanEvent.Detected(listOf(detection(value, format)))

    private suspend fun Flow<ScanEvent>.detectedValues(): List<String> =
        toList().filterIsInstance<ScanEvent.Detected>().flatMap { event ->
            event.detections.map { it.barcode.rawValue }
        }

    /**
     * Motor de guion: emite eventos avanzando el reloj antes de cada uno.
     *
     * [FakeScannerEngine] no sirve aquí porque emite su lista de golpe y el tiempo no corre entre
     * emisiones — y el tiempo entre frames **es** la variable que este decorador mide.
     */
    private fun scripted(time: FakeTimeProvider, script: List<Pair<Long, ScanEvent>>) =
        object : BarcodeScannerEngine {
            override val id: ScannerEngineId = engineId

            override val descriptor: ScannerEngineDescriptor =
                FakeScannerEngine(engineId).descriptor

            override suspend fun availability(): EngineAvailability = EngineAvailability.Available

            override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
                emit(ScanEvent.SessionStarted(engineId))
                script.forEach { (advanceMillis, event) ->
                    time.advanceBy(advanceMillis)
                    emit(event)
                }
                emit(ScanEvent.SessionEnded(engineId))
            }
        }

    private companion object {
        /** Medio segundo de vídeo a 30 fps, que es lo que dura mirar un código sin querer. */
        const val FRAMES_HELD = 15

        const val WINDOW_MILLIS = 2_000L
    }
}
