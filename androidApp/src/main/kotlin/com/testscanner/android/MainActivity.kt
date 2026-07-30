package com.testscanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.testscanner.App
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionRequester
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject

/**
 * Shell de Android: no contiene lógica ni UI propia.
 *
 * Su única responsabilidad más allá de pintar `App()` es prestar su `ActivityResultLauncher` al
 * controlador de permisos mientras está viva. El controlador es un singleton del grafo y no puede
 * quedarse con una referencia a la Activity, así que el préstamo se retira en `onDestroy`.
 */
class MainActivity : ComponentActivity() {

    private val permissionController: AndroidPermissionController by inject()

    private var pendingRequest: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingRequest?.invoke(granted)
        pendingRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        permissionController.requester = PermissionRequester { permission ->
            suspendCancellableCoroutine { continuation ->
                pendingRequest = { granted -> continuation.resume(granted) }
                permissionLauncher.launch(permission)
                continuation.invokeOnCancellation { pendingRequest = null }
            }
        }

        setContent { App() }
    }

    override fun onDestroy() {
        permissionController.requester = null
        super.onDestroy()
    }
}
