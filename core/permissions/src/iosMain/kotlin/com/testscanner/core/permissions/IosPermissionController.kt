package com.testscanner.core.permissions

import com.testscanner.core.model.Permission
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

/**
 * Permisos de iOS.
 *
 * A diferencia de Android no hace falta prestar nada desde la vista: `AVCaptureDevice` resuelve el
 * permiso a nivel de proceso, así que el controlador puede vivir como singleton sin retener nada.
 *
 * Requiere `NSCameraUsageDescription` en el `Info.plist`; sin esa clave iOS **mata la app** al
 * pedir acceso, en lugar de denegarlo.
 */
class IosPermissionController : PermissionController {

    override suspend fun status(permission: Permission): PermissionStatus = when (permission) {
        Permission.Camera -> cameraStatus()
        // La fototeca se resolverá con PHPhotoLibrary cuando llegue el escaneo desde imagen.
        Permission.PhotoLibrary -> PermissionStatus.NotDetermined
    }

    override suspend fun request(permission: Permission): PermissionStatus {
        if (permission != Permission.Camera) return status(permission)

        val current = cameraStatus()
        // En iOS el diálogo del sistema se muestra **una sola vez** en la vida de la app. Volver a
        // pedirlo cuando ya fue denegado no muestra nada, así que se reporta como denegación
        // permanente y la UI ofrece los ajustes en lugar de un botón que no haría nada.
        if (current != PermissionStatus.NotDetermined) return current

        val granted = suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { allowed ->
                continuation.resume(allowed)
            }
        }

        return if (granted) PermissionStatus.Granted else PermissionStatus.Denied(permanently = true)
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }

    private fun cameraStatus(): PermissionStatus =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            AVAuthorizationStatusDenied -> PermissionStatus.Denied(permanently = true)
            // Control parental o MDM: el usuario no puede concederlo aunque quiera.
            AVAuthorizationStatusRestricted -> PermissionStatus.Denied(permanently = true)
            else -> PermissionStatus.NotDetermined
        }
}
