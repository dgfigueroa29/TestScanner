// Los parámetros se usan dentro del `js("...")`, que detekt no analiza.
@file:Suppress("UnusedParameter")

package com.whyscan.platform

import com.whyscan.core.model.ScanImage
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PickImageResult
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise

/**
 * Abre un `<input type="file">` invisible y resuelve con la imagen elegida.
 *
 * El elemento se crea y se descarta en cada uso en lugar de vivir en la página: dejarlo puesto
 * obligaría a que la app supiera dónde insertarlo, y Compose para Web pinta sobre un `<canvas>` que
 * no tiene un sitio natural donde colgarlo.
 *
 * Devuelve `{ mime, data }` con la carga en base64 porque los `ByteArray` no cruzan la frontera de
 * Wasm sin `kotlinx-browser`; se decodifica del lado de Kotlin.
 */
private fun jsPickImage(): Promise<JsAny?> = js(
    """
    (function () {
        return new Promise(function (resolve) {
            var input = document.createElement('input');
            input.type = 'file';
            input.accept = 'image/*';
            input.style.display = 'none';
            document.body.appendChild(input);

            var chose = false;
            var done = function (value) {
                input.remove();
                resolve(value);
            };

            input.onchange = function () {
                var file = input.files && input.files[0];
                if (!file) { done(null); return; }
                chose = true;
                var reader = new FileReader();
                reader.onerror = function () { done({ mime: '', data: '', error: 'lectura' }); };
                reader.onload = function () {
                    var url = String(reader.result);
                    done({ mime: file.type || 'image/png', data: url.slice(url.indexOf(',') + 1) });
                };
                reader.readAsDataURL(file);
            };

            // No hay evento fiable de "cancelado" en todos los navegadores: si la ventana recupera
            // el foco y no se eligió ningún archivo, se asume cancelación. La comprobación mira
            // `chose` y no si el input sigue en el DOM, porque leer un archivo grande puede tardar
            // más que la espera y eso se reportaría como cancelado sin serlo.
            window.addEventListener('focus', function check() {
                window.removeEventListener('focus', check);
                setTimeout(function () { if (!chose) done(null); }, 500);
            });
        });
    })()
    """,
)

private fun pickedMime(picked: JsAny): String = js("picked.mime")

private fun pickedData(picked: JsAny): String = js("picked.data")

private fun pickedError(picked: JsAny): String = js("picked.error || ''")

/** Selector de imágenes del navegador (RF-07). */
class WebImagePicker : ImagePicker {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun pickImage(): PickImageResult {
        val picked = jsPickImage().await<JsAny?>() ?: return PickImageResult.Cancelled

        pickedError(picked).takeIf { it.isNotEmpty() }?.let {
            return PickImageResult.Failed("No se pudo leer el archivo elegido")
        }

        return runCatching {
            PickImageResult.Picked(
                ScanImage(
                    encoded = Base64.decode(pickedData(picked)),
                    mimeType = pickedMime(picked),
                ),
            )
        }.getOrElse { PickImageResult.Failed("El archivo elegido no es una imagen válida") }
    }
}
