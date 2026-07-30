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
