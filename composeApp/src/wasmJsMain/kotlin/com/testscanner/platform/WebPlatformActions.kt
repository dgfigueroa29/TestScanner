package com.testscanner.platform

import com.testscanner.core.platform.PlatformActions

private fun jsCanShare(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.share")

private fun jsCopy(text: String): Boolean =
    js("(function(t){ if (!navigator.clipboard) return false; navigator.clipboard.writeText(t); return true; })(text)")

private fun jsShare(text: String): Boolean =
    js("(function(t){ if (!navigator.share) return false; navigator.share({ text: t }); return true; })(text)")

private fun jsOpen(url: String): Boolean =
    js("(function(u){ return !!window.open(u, '_blank', 'noopener,noreferrer'); })(url)")

/**
 * Acciones del sistema en el navegador.
 *
 * Dos particularidades honestas de la plataforma:
 * - **`canShare` se consulta en tiempo de ejecución.** `navigator.share` existe en móviles y en
 *   Safari, pero no en la mayoría de navegadores de escritorio. Es el mismo patrón que el catálogo
 *   de motores usa con `BarcodeDetector`: la capacidad se comprueba, no se asume.
 * - **Copiar y compartir devuelven `true` en cuanto la llamada arranca**, sin esperar la promesa.
 *   El navegador puede seguir denegando el permiso del portapapeles después; esperar el resultado
 *   exigiría puentear promesas de JS a corrutinas para un `Boolean` que solo sirve para decidir si
 *   mostrar un aviso. Se asume el compromiso y queda escrito aquí.
 */
class WebPlatformActions : PlatformActions {

    override val canShare: Boolean get() = jsCanShare()

    override suspend fun copyToClipboard(text: String): Boolean = jsCopy(text)

    override suspend fun share(text: String): Boolean = jsShare(text)

    // `noopener` no es cosmético: sin él la pestaña abierta puede manipular la nuestra vía
    // `window.opener`, y el destino de un QR es contenido en el que no se puede confiar.
    override suspend fun openUrl(url: String): Boolean = jsOpen(url)
}
