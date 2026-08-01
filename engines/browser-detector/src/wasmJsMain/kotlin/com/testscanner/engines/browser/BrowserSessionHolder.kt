package com.testscanner.engines.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity

/**
 * Punto de encuentro entre el motor y su preview.
 *
 * El motor crea la sesión al arrancar y el composable necesita ese mismo `<video>`; pero el
 * composable puede entrar en composición antes o después. Es el mismo problema —y la misma
 * solución— que el `CameraSessionHolder` de iOS, con un handle de JS en lugar de una
 * `AVCaptureSession`.
 */
internal class BrowserSessionHolder {

    var session: JsAny? = null
        private set

    private val listeners = mutableListOf<(JsAny?) -> Unit>()

    fun attach(session: JsAny) {
        this.session = session
        listeners.toList().forEach { it(session) }
    }

    fun detach() {
        session = null
        listeners.toList().forEach { it(null) }
    }

    fun addListener(listener: (JsAny?) -> Unit) {
        listeners += listener
        listener(session)
    }

    fun removeListener(listener: (JsAny?) -> Unit) {
        listeners -= listener
    }
}

/**
 * Superficie de vídeo en el navegador (deuda D14).
 *
 * No hay `AndroidView` ni `UIKitView` en Compose para Web: la app se pinta sobre un `<canvas>` y no
 * existe forma de incrustar un elemento del DOM dentro del árbol. Así que el composable **no pinta
 * nada**: mide su rectángulo y le dice al motor dónde colocar su `<video>`, que vive en el
 * documento, por encima del canvas.
 *
 * El puente entre los dos mundos es [onGloballyPositioned]: da la posición y el tamaño en píxeles
 * de Compose, que se dividen por la densidad para obtener píxeles CSS. Sin esa división el vídeo
 * saldría del tamaño equivocado en cualquier pantalla con `devicePixelRatio` distinto de 1 — es
 * decir, en casi todos los móviles.
 */
@Composable
internal fun RenderBrowserPreview(holder: BrowserSessionHolder, modifier: Modifier) {
    val density = LocalDensity.current.density

    DisposableEffect(holder) {
        onDispose { holder.session?.let(::detachSessionVideo) }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val session = holder.session ?: return@onGloballyPositioned
            val position = coordinates.positionInWindow()
            attachSessionVideo(
                session = session,
                left = (position.x / density).toDouble(),
                top = (position.y / density).toDouble(),
                width = (coordinates.size.width / density).toDouble(),
                height = (coordinates.size.height / density).toDouble(),
            )
        },
    )
}
