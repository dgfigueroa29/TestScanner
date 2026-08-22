package com.whyscan.platform

import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.SaveFileResult
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * Guardado de archivos en iOS (RF-11).
 *
 * iOS no tiene un "guardar como" equivalente al de escritorio: lo que hay es la **hoja de
 * compartir**, desde la que el usuario elige Archivos, Correo, AirDrop o lo que tenga. Así que el
 * archivo se escribe primero en el directorio temporal y se ofrece esa URL.
 *
 * Por eso [SaveFileResult.Saved] llega sin `location`: una vez presentada la hoja, la app no se
 * entera de a dónde fue a parar el archivo — ni tiene por qué.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileSaver : FileSaver {

    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: String,
    ): SaveFileResult {
        val presenter = rootViewController()
            ?: return SaveFileResult.Failed("No hay ninguna pantalla activa para compartir")

        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + suggestedName)

        val written = (content as NSString).writeToURL(
            url = url,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (!written) return SaveFileResult.Failed("No se pudo preparar el archivo")

        val controller = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )
        // En iPad la hoja es un popover y exige ancla: sin `sourceView` la app crashea al mostrarla.
        controller.popoverPresentationController?.sourceView = presenter.view

        presenter.presentViewController(controller, animated = true, completion = null)

        return SaveFileResult.Saved(location = null)
    }

    private fun rootViewController(): UIViewController? = UIApplication.sharedApplication
        .connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull()
        ?.rootViewController
}
