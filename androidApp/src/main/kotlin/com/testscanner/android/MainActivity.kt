package com.testscanner.android

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.testscanner.App
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionRequester
import com.testscanner.navigation.Navigator
import com.testscanner.platform.AndroidFileSaver
import com.testscanner.platform.AndroidImagePicker
import com.testscanner.platform.DocumentRequester
import com.testscanner.platform.ImageRequester
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

/**
 * Shell de Android: no contiene lógica ni UI propia.
 *
 * Sus responsabilidades más allá de pintar `App()` son de plataforma pura: prestar sus
 * `ActivityResultLauncher` al controlador de permisos y al selector de imágenes mientras está viva
 * — ambos son singletons del grafo y no pueden retener la Activity, así que el préstamo se retira
 * en `onDestroy` — y conectar el botón atrás del sistema al `Navigator` compartido, además de
 * guardar y restaurar su backstack al recrearse la Activity.
 */
class MainActivity : ComponentActivity() {

    private val permissionController: AndroidPermissionController by inject()

    private val imagePicker: AndroidImagePicker by inject()

    private val fileSaver: AndroidFileSaver by inject()

    private val navigator = Navigator()

    private var pendingRequest: ((Boolean) -> Unit)? = null

    private var pendingImage: ((Uri?) -> Unit)? = null

    private var pendingDocument: ((Uri?) -> Unit)? = null

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

    // `CreateDocument` recibe el tipo MIME al **construirse**, no al lanzarse, y los launchers hay
    // que registrarlos antes de que la Activity arranque. Como los formatos de exportación son dos
    // y conocidos, se registra uno por cada uno en lugar de inventar registro dinámico.
    private val csvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_CSV),
    ) { uri ->
        pendingDocument?.invoke(uri)
        pendingDocument = null
    }

    private val jsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri ->
        pendingDocument?.invoke(uri)
        pendingDocument = null
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

        fileSaver.requester = DocumentRequester { mimeType, suggestedName ->
            suspendCancellableCoroutine { continuation ->
                pendingDocument = { uri -> continuation.resume(uri) }
                launchCreateDocument(mimeType, suggestedName)
                continuation.invokeOnCancellation { pendingDocument = null }
            }
        }

        // Recrear la Activity recrea el Navigator, y con él se perdía el backstack. Girar el
        // teléfono **no** entra ahí: el manifiesto declara `configChanges` para orientación y
        // tamaño, precisamente para no reiniciar la cámara al rotar. Lo que sí la recrea es que el
        // sistema mate el proceso en segundo plano, y los cambios de configuración que la Activity
        // no declara — el tamaño de letra o el idioma del sistema.
        //
        // El backstack viaja como ids y no como objetos porque `Destination` no es `Parcelable` y no
        // hay razón para que lo sea (ADR-0005).
        savedInstanceState?.getStringArrayList(KEY_BACKSTACK)?.let(navigator::restoreState)

        setContent {
            // El botón atrás del sistema desapila en el Navigator; cuando ya no hay nada que
            // desapilar **no se intercepta**, y así la plataforma cierra la Activity ella misma
            // (ADR-0005).
            //
            // `BackHandler` y no `onBackPressedDispatcher.addCallback` con el truco de
            // `isEnabled = false` + `onBackPressed()`: ese patrón deja de funcionar bien con el
            // *predictive back*, que a partir de `targetSdk` 36 viene activado por defecto. El
            // sistema decide qué animación pintar **cuando empieza el gesto**, preguntando si hay
            // algún callback habilitado. Con un callback siempre habilitado, siempre creía que la
            // vuelta era dentro de la app, y al soltar el dedo se encontraba con que la Activity se
            // cerraba — animación equivocada garantizada. Además `isEnabled = false` no se volvía a
            // poner en `true` nunca, así que el callback quedaba muerto si la Activity sobrevivía.
            //
            // Atando `enabled` al tamaño del backstack, el sistema sabe de antemano cuál de las dos
            // vueltas toca y anima la correcta.
            val backstack by navigator.backstack.collectAsState()
            BackHandler(enabled = backstack.size > 1) { navigator.goBack() }

            App(navigator = navigator, onDarkThemeResolved = ::applySystemBarStyle)
        }
    }

    /**
     * Ajusta el contraste de los iconos de las barras del sistema al tema **de la app**.
     *
     * `enableEdgeToEdge()` sin argumentos deja que los iconos sigan al modo oscuro del *sistema*, y
     * eso solo funciona mientras los dos coinciden. En cuanto el usuario elige un tema en Ajustes
     * —que es justo lo que ahora se puede hacer— dejan de coincidir: con el teléfono en claro y la
     * app forzada a oscuro, los iconos de la barra de estado salían oscuros sobre nuestro fondo
     * oscuro, es decir, invisibles. Y al revés.
     *
     * Se vuelve a llamar a `enableEdgeToEdge` en cada cambio porque es idempotente y es la API que
     * decide este contraste; no hay una versión "solo actualizar el estilo".
     *
     * Los dos estilos van transparentes: el color de fondo lo pinta Compose bajo las barras, que es
     * lo que significa edge-to-edge. `SystemBarStyle.light` pide los dos colores —el segundo es el
     * velo que el sistema usa en API 26-28, donde los iconos de la barra de navegación no podían
     * ser oscuros— y transparente en ambos es lo correcto aquí.
     */
    private fun applySystemBarStyle(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }

        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun launchCreateDocument(mimeType: String, suggestedName: String) {
        val launcher = when (mimeType) {
            MIME_CSV -> csvLauncher
            MIME_JSON -> jsonLauncher
            // Un tipo que no se registró se trata como cancelación: es preferible a lanzar sobre un
            // launcher inexistente, que reventaría la Activity.
            else -> null
        }

        if (launcher == null) {
            pendingDocument?.invoke(null)
            pendingDocument = null
            return
        }

        launcher.launch(suggestedName)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_BACKSTACK, ArrayList(navigator.saveState()))
    }

    override fun onDestroy() {
        permissionController.requester = null
        imagePicker.requester = null
        fileSaver.requester = null
        super.onDestroy()
    }

    private companion object {
        const val MIME_CSV = "text/csv"
        const val MIME_JSON = "application/json"
        const val KEY_BACKSTACK = "backstack"
    }
}
