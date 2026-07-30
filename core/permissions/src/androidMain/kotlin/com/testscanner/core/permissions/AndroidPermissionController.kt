package com.testscanner.core.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.testscanner.core.model.Permission

/** Nombre del permiso de Android correspondiente a cada [Permission] del dominio. */
private fun Permission.manifestName(): String = when (this) {
    Permission.Camera -> android.Manifest.permission.CAMERA
    Permission.PhotoLibrary -> android.Manifest.permission.READ_MEDIA_IMAGES
}

/**
 * Puente hacia el `ActivityResultLauncher` de la Activity visible.
 *
 * Existe porque **pedir** un permiso necesita una Activity, pero **consultarlo** solo necesita un
 * Context. Guardar la Activity dentro del controlador — que vive en el grafo de Koin como singleton
 * — la retendría más allá de su ciclo de vida y filtraría memoria en cada rotación.
 */
fun interface PermissionRequester {
    suspend fun request(permission: String): Boolean
}

/**
 * Controlador de permisos de Android.
 *
 * La Activity actual se registra en [requester] cuando arranca y lo limpia al destruirse. Si no hay
 * ninguna registrada, [request] degrada a devolver el estado actual en lugar de fallar: es
 * preferible que la UI muestre "permiso pendiente" a que la app reviente.
 */
class AndroidPermissionController(
    private val context: Context,
) : PermissionController {

    @Volatile
    var requester: PermissionRequester? = null

    private val alreadyAsked = mutableSetOf<Permission>()

    override suspend fun status(permission: Permission): PermissionStatus = when {
        isGranted(permission) -> PermissionStatus.Granted
        permission in alreadyAsked -> PermissionStatus.Denied(permanently = false)
        else -> PermissionStatus.NotDetermined
    }

    override suspend fun request(permission: Permission): PermissionStatus {
        if (isGranted(permission)) return PermissionStatus.Granted

        val launcher = requester ?: return status(permission)
        val secondAttempt = permission in alreadyAsked
        alreadyAsked += permission

        val granted = launcher.request(permission.manifestName())

        return when {
            granted -> PermissionStatus.Granted
            // Denegar dos veces equivale a "no volver a preguntar": a partir de ahí el diálogo del
            // sistema ya no aparece y la UI debe ofrecer los ajustes en lugar de reintentar.
            secondAttempt -> PermissionStatus.Denied(permanently = true)
            else -> PermissionStatus.Denied(permanently = false)
        }
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isGranted(permission: Permission): Boolean =
        context.checkSelfPermission(permission.manifestName()) == PackageManager.PERMISSION_GRANTED
}
