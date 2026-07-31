package com.testscanner.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.testscanner.App
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionRequester
import com.testscanner.navigation.Navigator
import com.testscanner.platform.AndroidImagePicker
import com.testscanner.platform.ImageRequester
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject

/**
 * Shell de Android: no contiene lógica ni UI propia.
 *
 * Sus responsabilidades más allá de pintar `App()` son de plataforma pura: prestar sus
 * `ActivityResultLauncher` al controlador de permisos y al selector de imágenes mientras está viva
 * — ambos son singletons del grafo y no pueden retener la Activity, así que el préstamo se retira
 * en `onDestroy` — y conectar el botón atrás del sistema al `Navigator` compartido.
 */
class MainActivity : ComponentActivity() {

    private val permissionController: AndroidPermissionController by inject()

    private val imagePicker: AndroidImagePicker by inject()

    private val navigator = Navigator()

    private var pendingRequest: ((Boolean) -> Unit)? = null

    private var pendingImage: ((Uri?) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingRequest?.invoke(granted)
        pendingRequest = null
    }

    // `PickVisualMedia` es el *photo picker* del sistema: corre fuera de la app y devuelve solo lo
    // que el usuario elige, así que no requiere `READ_MEDIA_IMAGES` ni ningún otro permiso.
    private val imageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pendingImage?.invoke(uri)
        pendingImage = null
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

        imagePicker.requester = ImageRequester {
            suspendCancellableCoroutine { continuation ->
                pendingImage = { uri -> continuation.resume(uri) }
                imageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
                continuation.invokeOnCancellation { pendingImage = null }
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
        imagePicker.requester = null
        super.onDestroy()
    }
}
