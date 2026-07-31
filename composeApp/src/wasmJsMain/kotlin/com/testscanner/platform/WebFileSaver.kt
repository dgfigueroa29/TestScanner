package com.testscanner.platform

import com.testscanner.core.platform.FileSaver
import com.testscanner.core.platform.SaveFileResult

/**
 * Descarga el contenido como archivo.
 *
 * En el navegador no hay sistema de archivos al que escribir: lo que hay es una descarga. Se crea
 * un `Blob`, se cuelga de un `<a download>` invisible y se pulsa por código. Es el mecanismo
 * estándar y funciona en todos los navegadores, a diferencia de la File System Access API, que solo
 * está en los basados en Chromium.
 *
 * `revokeObjectURL` no es opcional: cada `createObjectURL` retiene el blob en memoria hasta que se
 * libera o se recarga la página, así que exportar varias veces iría acumulando copias.
 */
private fun jsDownload(name: String, mimeType: String, content: String): Boolean = js(
    """
    (function (n, m, c) {
        try {
            var blob = new Blob([c], { type: m });
            var url = URL.createObjectURL(blob);
            var link = document.createElement('a');
            link.href = url;
            link.download = n;
            link.style.display = 'none';
            document.body.appendChild(link);
            link.click();
            link.remove();
            setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
            return true;
        } catch (e) {
            return false;
        }
    })(name, mimeType, content)
    """,
)

/** Guardado de archivos en el navegador (RF-11). */
class WebFileSaver : FileSaver {

    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: String,
    ): SaveFileResult = if (jsDownload(suggestedName, mimeType, content)) {
        // El navegador no dice dónde acabó la descarga, y con razón: es cosa suya.
        SaveFileResult.Saved(location = null)
    } else {
        SaveFileResult.Failed("El navegador no pudo iniciar la descarga")
    }
}
