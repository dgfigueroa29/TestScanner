package com.testscanner.platform

import com.testscanner.core.platform.PlatformActions
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * Acciones del sistema en escritorio.
 *
 * `canShare` es `false` porque en escritorio **no existe** una hoja de compartir equivalente a la de
 * los móviles. Podría abrirse un cliente de correo, pero eso no es compartir: es una acción distinta
 * disfrazada. Es preferible no ofrecer el botón — que es justamente para lo que existe la bandera.
 */
class DesktopPlatformActions : PlatformActions {

    override val canShare: Boolean = false

    override suspend fun copyToClipboard(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }.isSuccess

    override suspend fun share(text: String): Boolean = false

    override suspend fun openUrl(url: String): Boolean = runCatching {
        // `isDesktopSupported` es false en entornos headless y en algunos escritorios de Linux;
        // sin comprobarlo, `getDesktop()` lanza.
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(URI(url))
    }.isSuccess
}
