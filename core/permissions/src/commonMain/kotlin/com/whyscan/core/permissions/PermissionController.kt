package com.whyscan.core.permissions

import com.whyscan.core.model.Permission

/** Resultado de consultar o solicitar un permiso. */
sealed interface PermissionStatus {
    data object Granted : PermissionStatus
    data object NotDetermined : PermissionStatus

    /**
     * Denegado. [permanently] distingue el "no ahora" del "no y no vuelvas a preguntar": en el
     * segundo caso la UI debe ofrecer abrir los ajustes en lugar de volver a pedirlo.
     */
    data class Denied(val permanently: Boolean) : PermissionStatus

    /** La plataforma no gestiona este permiso (Desktop abre la webcam sin pedir nada). */
    data object NotApplicable : PermissionStatus

    val isGranted: Boolean get() = this is Granted || this is NotApplicable
}

/**
 * Acceso a los permisos del sistema, abstraído para las cuatro plataformas.
 *
 * Vive en su propio módulo — y no dentro de un motor — porque varios motores comparten el mismo
 * permiso de cámara y ninguno debería ser dueño de él.
 */
interface PermissionController {
    suspend fun status(permission: Permission): PermissionStatus
    suspend fun request(permission: Permission): PermissionStatus

    /** Abre los ajustes de la app. Usado cuando el permiso está denegado permanentemente. */
    fun openAppSettings()
}

/**
 * Implementación de referencia para plataformas sin permisos explícitos (Desktop) y para tests.
 */
class AlwaysGrantedPermissionController : PermissionController {
    override suspend fun status(permission: Permission): PermissionStatus =
        PermissionStatus.NotApplicable

    override suspend fun request(permission: Permission): PermissionStatus =
        PermissionStatus.NotApplicable

    override fun openAppSettings() = Unit
}
