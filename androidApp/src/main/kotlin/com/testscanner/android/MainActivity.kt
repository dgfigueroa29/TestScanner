package com.testscanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.testscanner.App
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionRequester
import com.testscanner.navigation.Navigator
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject

/**
 * Shell de Android: no contiene lógica ni UI propia.
 *
 * Sus dos responsabilidades más allá de pintar `App()` son de plataforma pura: prestar su
 * `ActivityResultLauncher` al controlador de permisos mientras está viva — el controlador es un
 * singleton del grafo y no puede retener la Activity, así que el préstamo se retira en `onDestroy` —
 * y conectar el botón atrás del sistema al `Navigator` compartido.
 */
class MainActivity : ComponentActivity() {

    private val permissionController: AndroidPermissionController by inject()

    private val navigator = Navigator()

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

        // El botón atrás del sistema desapila en el Navigator; cuando ya no hay nada que desapilar
        // se devuelve el control a la plataforma para que cierre la Activity (ADR-0005).
        onBackPressedDispatcher.addCallback(this) {
            if (!navigator.goBack()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setContent { App(navigator) }
    }

    override fun onDestroy() {
        permissionController.requester = null
        super.onDestroy()
    }
}
