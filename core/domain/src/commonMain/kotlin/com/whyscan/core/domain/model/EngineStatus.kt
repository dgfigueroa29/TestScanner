package com.whyscan.core.domain.model

import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Permission
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScannerEngineDescriptor

/**
 * Un motor del catálogo junto con su estado real en este dispositivo.
 *
 * El catálogo siempre lista **todos** los motores del producto; [installed] distingue los que están
 * enlazados en este binario de los que solo existen como ficha (otra plataforma, o fase futura).
 */
data class EngineStatus(
    val descriptor: ScannerEngineDescriptor,
    val availability: EngineAvailability,
    val installed: Boolean,
) {
    val id: ScannerEngineId get() = descriptor.id
    val isUsable: Boolean get() = installed && availability.isUsable

    /**
     * Si sirve para decodificar una imagen ya capturada (RF-07).
     *
     * Un motor bloqueado por el **permiso de cámara** sigue valiendo para esto: el archivo ya está
     * en el dispositivo y no hay cámara que abrir. La excepción no es un detalle — escanear desde
     * foto es precisamente la salida cuando el usuario niega la cámara, y sin ella la app la
     * escondería justo en el momento en que hace falta.
     *
     * Cualquier otra indisponibilidad —modelo sin descargar, plataforma no soportada, motor de una
     * fase futura— sí bloquea, porque ahí no hay nada que ejecutar.
     */
    val canDecodeImages: Boolean
        get() = installed &&
            ScanSource.StaticImage in descriptor.capabilities.sources &&
            (availability.isUsable || availability.isBlockedOnlyByCameraPermission)
}

private val EngineAvailability.isBlockedOnlyByCameraPermission: Boolean
    get() = this is EngineAvailability.RequiresPermission && permission == Permission.Camera

/** Motor descartado por el selector, con el motivo. La UI lo usa para explicar la decisión. */
data class RejectedEngine(
    val id: ScannerEngineId,
    val reason: RejectionReason,
)

sealed interface RejectionReason {
    /** No está disponible ahora (permiso, descarga, plataforma, no implementado). */
    data class NotAvailable(val availability: EngineAvailability) : RejectionReason

    /** Está disponible pero no cubre lo que pide la petición. */
    data class DoesNotSatisfyRequest(
        val uncoveredFormats: Set<BarcodeFormat>,
        val missingCapabilities: List<String>,
    ) : RejectionReason
}

/**
 * Resultado de la política de selección: una **cadena ordenada**, no un único motor.
 *
 * El primer elemento es el preferido; el resto son los fallbacks que se probarán en orden si aquel
 * falla de forma fatal. Devolver la cadena completa — en lugar de un motor y volver a preguntar
 * cuando falle — hace que la degradación sea determinista y testeable.
 */
data class EngineSelection(
    val chain: List<ScannerEngineId>,
    val rejected: List<RejectedEngine>,
) {
    val preferred: ScannerEngineId? get() = chain.firstOrNull()
    val hasFallback: Boolean get() = chain.size > 1

    companion object {
        val Empty = EngineSelection(emptyList(), emptyList())
    }
}
