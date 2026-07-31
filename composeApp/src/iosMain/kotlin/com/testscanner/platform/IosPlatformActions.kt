package com.testscanner.platform

import com.testscanner.core.platform.PlatformActions
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * Acciones del sistema en iOS.
 *
 * La hoja de compartir es un `UIViewController` y hay que presentarla desde otro, así que se busca
 * el controlador raíz de la ventana activa. Es el único punto de la app donde el código Kotlin toca
 * la jerarquía de vistas de UIKit, y solo porque `UIActivityViewController` no ofrece otra forma.
 */
class IosPlatformActions : PlatformActions {

    override val canShare: Boolean = true

    override suspend fun copyToClipboard(text: String): Boolean {
        UIPasteboard.generalPasteboard.string = text
        return true
    }

    override suspend fun share(text: String): Boolean {
        val presenter = rootViewController() ?: return false

        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )

        // En iPad la hoja es un popover y **exige** un ancla: sin `sourceView` la app crasheza al
        // presentarla. Anclarla al propio controlador la deja centrada, que es lo razonable cuando
        // la acción no sale de un botón concreto.
        controller.popoverPresentationController?.sourceView = presenter.view

        presenter.presentViewController(controller, animated = true, completion = null)
        return true
    }

    override suspend fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        if (!UIApplication.sharedApplication.canOpenURL(nsUrl)) return false
        UIApplication.sharedApplication.openURL(nsUrl)
        return true
    }

    /**
     * Primera ventana de la primera escena. La app tiene una sola ventana; si algún día soporta
     * varias en iPad, habrá que elegir la activa en vez de la primera.
     */
    private fun rootViewController(): UIViewController? = UIApplication.sharedApplication
        .connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull()
        ?.rootViewController
}
