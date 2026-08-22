package com.whyscan.core.scanner

import com.whyscan.core.model.Permission
import com.whyscan.core.model.ScanError

/**
 * Estado de disponibilidad de un motor.
 *
 * La indisponibilidad es un dato de primera clase, no una excepción: la UI necesita explicar
 * **por qué** un motor no se puede usar, y el selector necesita distinguir "no está soportado
 * aquí" de "falta un permiso que el usuario todavía puede conceder".
 */
sealed interface EngineAvailability {

    /** Listo para escanear ahora mismo. */
    data object Available : EngineAvailability

    /** Falta un permiso que el usuario puede conceder. Recuperable sin cambiar de motor. */
    data class RequiresPermission(val permission: Permission) : EngineAvailability

    /** El motor necesita descargar su modelo antes del primer uso. */
    data class RequiresDownload(val sizeBytes: Long? = null) : EngineAvailability

    /** La plataforma o el hardware no lo permiten. No es recuperable en este dispositivo. */
    data class Unsupported(val reason: String) : EngineAvailability

    /**
     * Motor del catálogo aún no implementado.
     *
     * Existe para que el catálogo esté completo desde la Fase 1: la UI muestra las ocho
     * alternativas y en qué fase llega cada una, y el registro no cambia de forma cuando un motor
     * se implementa — solo cambia lo que responde `availability()`.
     */
    data class NotImplemented(val plannedPhase: Int) : EngineAvailability

    /** El motor falló al comprobar su propia disponibilidad. */
    data class Failed(val error: ScanError) : EngineAvailability

    val isUsable: Boolean get() = this is Available

    /** Si el usuario puede hacer algo para desbloquearlo (conceder permiso, descargar). */
    val isActionable: Boolean
        get() = this is RequiresPermission || this is RequiresDownload
}
