package com.testscanner.core.domain.scan

import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.DecoratingScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Suprime la relectura del **mismo código** dentro de una ventana de tiempo.
 *
 * ## El defecto que arregla
 *
 * Una cámara analiza treinta frames por segundo. Con el escaneo continuo encendido, apuntar a un QR
 * durante tres segundos hacía que el motor emitiera ese mismo código **noventa veces**, y cada una:
 *
 *  - se apilaba en la lista de resultados de la pantalla, que crecía sin límite; y
 *  - **se guardaba en el historial persistente**, que acababa con noventa filas idénticas.
 *
 * Lo segundo es lo grave: no es ruido visual, es corrupción de los datos del usuario. Exportar el
 * historial a CSV daba un archivo lleno de repeticiones que no correspondían a nada que hubiera
 * pasado. Ningún motor lo evita por su cuenta —para ML Kit o Vision, un código que sigue delante de
 * la lente es un código que sigue ahí— y por eso es responsabilidad del dominio, exactamente como
 * `continuous` y `allowMultiple` lo son en [RequestLimitsScannerEngine].
 *
 * ## Por qué una ventana y no "una sola vez por sesión"
 *
 * Porque leer dos veces el mismo código **es** un caso de uso: contar unidades del mismo producto en
 * un inventario, comprobar que una etiqueta se lee bien. La ventana distingue las dos situaciones
 * sin preguntar: el código que no se ha movido de delante de la cámara se ignora; el que se aparta y
 * se vuelve a presentar se lee de nuevo.
 *
 * ## Dónde va en la cadena
 *
 * **Fuera** de la cadena de fallback, no por motor: si un motor cae y otro toma el relevo sobre el
 * mismo código físico, eso es una lectura repetida y no dos lecturas. Y **solo en la sesión en
 * vivo**: `DecodeImageUseCase` monta su propia cadena sin este decorador, porque en una foto los
 * códigos aparecen una vez y no hay repetición que suprimir. El comparador tampoco lo lleva, y ahí
 * es esencial: su razón de ser es que **todos** los motores reporten el mismo código.
 */
class DistinctDetectionsScannerEngine(
    override val delegate: BarcodeScannerEngine,
    private val time: TimeProvider,
    private val repeatWindowMillis: Long = DEFAULT_REPEAT_WINDOW_MILLIS,
) : DecoratingScannerEngine {

    override val id: ScannerEngineId = delegate.id

    override val descriptor: ScannerEngineDescriptor = delegate.descriptor

    override suspend fun availability(): EngineAvailability = delegate.availability()

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        // Dentro del `flow` y no como campo de la clase: cada colección es una sesión nueva y tiene
        // que empezar sin memoria. Un mapa compartido haría que volver a abrir la pantalla ignorara
        // el primer código, que es justo el que el usuario está esperando.
        val lastSeenMillis = mutableMapOf<String, Long>()

        delegate.scan(request).collect { event ->
            if (event !is ScanEvent.Detected) {
                emit(event)
                return@collect
            }

            val now = time.nowMillis()
            val fresh = event.detections.filter { detection ->
                val key = detection.repeatKey()
                val previous = lastSeenMillis[key]
                val isFresh = previous == null || now - previous >= repeatWindowMillis
                // Solo la lectura que pasa el filtro renueva la marca. Renovarla también con las
                // suprimidas convertiría un código sostenido delante de la lente en un código que
                // no vuelve a leerse nunca: cada frame empujaría la ventana hacia adelante.
                if (isFresh) lastSeenMillis[key] = now
                isFresh
            }

            // Un evento sin nada nuevo no se emite. Emitir `Detected` con la lista vacía le diría al
            // consumidor que hubo una lectura sin resultados, que es otra cosa distinta y falsa.
            if (fresh.isNotEmpty()) emit(event.copy(detections = fresh))
        }
    }

    /**
     * Dos lecturas son la misma si coinciden valor **y** formato.
     *
     * El formato entra en la clave porque el mismo dato puede llegar por dos simbologías —un EAN-13
     * y su QR impreso al lado— y son dos códigos distintos del mundo real. Concatenar con un espacio
     * basta: ningún `id` de formato lleva espacios, así que el primero de la clave siempre separa
     * formato de valor por muchos que el valor traiga dentro.
     */
    private fun Detection.repeatKey(): String = "${barcode.format.id} ${barcode.rawValue}"
}

/** Envuelve el motor para que no repita un código ya leído hace poco. */
fun BarcodeScannerEngine.suppressingRepeats(
    time: TimeProvider,
    repeatWindowMillis: Long = DEFAULT_REPEAT_WINDOW_MILLIS,
): BarcodeScannerEngine = DistinctDetectionsScannerEngine(this, time, repeatWindowMillis)

/**
 * Dos segundos: suficiente para cubrir el código sostenido delante de la lente, corto para que
 * apartarlo y volver a presentarlo se sienta inmediato.
 */
const val DEFAULT_REPEAT_WINDOW_MILLIS = 2_000L
