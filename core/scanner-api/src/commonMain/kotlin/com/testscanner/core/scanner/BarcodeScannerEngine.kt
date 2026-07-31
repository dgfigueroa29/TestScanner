package com.testscanner.core.scanner

import com.testscanner.core.model.Barcode
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow

/**
 * Contrato que implementa **toda** alternativa de escaneo. Es el núcleo del diseño: ver
 * `docs/adr/ADR-0002-scanner-engine-spi.md`.
 *
 * Deliberadamente mínimo. Las capacidades que no todos los motores tienen — controlar la linterna,
 * decodificar una imagen estática, aceptar texto tecleado — viven en interfaces segregadas
 * ([CameraControlEngine], [ImageDecodingEngine], [TextInputEngine]). Meterlas aquí obligaría a que
 * el Google Code Scanner, que abre su propia UI y no expone la cámara, lanzara
 * `UnsupportedOperationException`: un contrato que promete lo que no cumple.
 *
 * ### Contrato de la sesión
 * Toda implementación debe garantizar:
 * 1. El primer evento emitido es [ScanEvent.SessionStarted].
 * 2. Si la sesión termina por sí misma, el último evento es [ScanEvent.SessionEnded]. Si es el
 *    consumidor quien cancela, el `Flow` se cancela sin más emisiones — semántica estándar de
 *    corrutinas — y la liberación de recursos ocurre igualmente (punto 3).
 * 3. Cancelar la corrutina que colecta el `Flow` libera la cámara (`awaitClose` / `finally`).
 * 4. Todo formato reportado en [ScanEvent.Detected] está en `descriptor.capabilities.supportedFormats`.
 *
 * Estas cuatro reglas las verifica `BarcodeScannerEngineContractTest` para cualquier motor nuevo.
 */
interface BarcodeScannerEngine {

    val id: ScannerEngineId

    val descriptor: ScannerEngineDescriptor

    /**
     * Comprueba si el motor puede usarse ahora. Es `suspend` porque determinarlo puede requerir
     * I/O: consultar si el modelo de ML Kit ya se descargó, si Play Services está actualizado, o si
     * el navegador expone `BarcodeDetector`.
     *
     * Debe ser idempotente y no tener efectos secundarios observables.
     */
    suspend fun availability(): EngineAvailability

    /**
     * Sesión de escaneo. Devuelve un `Flow` frío: la cámara se abre al colectar y se cierra al
     * cancelar. Ver `docs/adr/ADR-0004-flow-como-api-de-sesion.md`.
     */
    fun scan(request: ScanRequest): Flow<ScanEvent>
}

/** Capacidad opcional: decodificar una imagen ya capturada (RF-07). */
interface ImageDecodingEngine {
    suspend fun decode(image: ScanImage, request: ScanRequest): Result<List<Barcode>>
}

/** Capacidad opcional: controles de cámara (RF-14). La UI los muestra solo si el motor la expone. */
interface CameraControlEngine {
    suspend fun setTorch(enabled: Boolean)
    suspend fun setZoomRatio(ratio: Float)
}

/**
 * Capacidad opcional: el motor se alimenta de texto introducido por el usuario en lugar de frames.
 * Lo implementa el motor de entrada manual, que cierra la cadena de fallback.
 */
interface TextInputEngine {
    /** Entrega un valor tecleado a la sesión activa. */
    suspend fun submit(value: String)
}

/**
 * Un motor que **envuelve a otro** para modificar su comportamiento sin cambiar lo que declara.
 *
 * Los decoradores del dominio —filtrar formatos, aplicar límites, interpretar valores, poner un
 * plazo— copian el descriptor del motor que envuelven. Eso es correcto: no le quitan capacidades.
 * Pero deja una trampa: el descriptor de un motor decorado dice "sé encender la linterna" y un
 * `as? CameraControlEngine` sobre él falla, porque quien la implementa es el motor de dentro.
 *
 * Exponer el delegado permite que [capability] atraviese la cadena y encuentre a quien de verdad
 * sabe hacerlo. La alternativa —que cada decorador implementara las tres capacidades opcionales y
 * delegara— obligaría a prometerlas siempre, que es justo lo que ADR-0002 evita.
 *
 * `ComparingScannerEngine` deliberadamente **no** lo implementa: envuelve a varios motores a la vez
 * y no hay un delegado del que heredar capacidades.
 */
interface DecoratingScannerEngine : BarcodeScannerEngine {
    val delegate: BarcodeScannerEngine
}

/**
 * Busca una capacidad opcional atravesando los decoradores que haya por medio.
 *
 * Sustituye al `as? CameraControlEngine` directo: sobre un motor sin decorar hace exactamente lo
 * mismo, y sobre una cadena decorada encuentra al motor que sí la implementa en lugar de devolver
 * `null` por accidente.
 */
inline fun <reified T : Any> BarcodeScannerEngine.capability(): T? {
    var current: BarcodeScannerEngine? = this
    while (current != null) {
        if (current is T) return current
        current = (current as? DecoratingScannerEngine)?.delegate
    }
    return null
}
