package com.testscanner.engines.browser

import kotlin.js.Promise

/**
 * Todo el interop con el navegador, en un único archivo.
 *
 * ### Por qué handles opacos y funciones sueltas, y no `external class`
 * Declarar `external class BarcodeDetector` sería más bonito, pero la API no está en ningún
 * navegador de escritorio mayoritario: una declaración externa de una clase inexistente falla al
 * enlazar, no al comprobarse. Con funciones `js(...)` que devuelven un handle opaco, cada llamada
 * se resuelve en el momento de usarse y [detectorIsAvailable] puede decidir antes de tocar nada.
 *
 * Los accesores devuelven tipos primitivos y `String` porque son los únicos que cruzan la frontera
 * de Wasm sin envoltorio. Es verboso a cambio de que ninguna línea pueda fallar de forma sutil.
 */

/** `true` si el navegador expone la API. Es lo primero que se comprueba, antes que nada más. */
internal fun detectorIsAvailable(): Boolean =
    js("typeof BarcodeDetector !== 'undefined'")

/**
 * La cámara solo se puede pedir en un contexto seguro. Sin esta comprobación, `getUserMedia`
 * lanzaría una excepción genérica y el usuario vería "fallo desconocido" en lugar de "esto necesita
 * HTTPS", que es un problema con solución.
 */
internal fun isSecureContext(): Boolean =
    js("typeof window !== 'undefined' && window.isSecureContext === true")

/**
 * Abre la cámara trasera y deja un `<video>` reproduciendo fuera del documento.
 *
 * El elemento no se inserta en el DOM aquí: hacerlo ataría el motor a una posición concreta de la
 * página. Lo coloca [attachSessionVideo] cuando el composable del visor sabe qué rectángulo ocupa.
 */
internal fun startCameraSession(formatsCsv: String): Promise<JsAny> = js(
    """
    (function (f) {
        var formats = f ? f.split(',') : undefined;
        var detector = new BarcodeDetector(formats ? { formats: formats } : undefined);
        return navigator.mediaDevices
            .getUserMedia({ video: { facingMode: 'environment' } })
            .then(function (stream) {
                var video = document.createElement('video');
                video.setAttribute('playsinline', '');
                video.muted = true;
                video.srcObject = stream;
                return video.play().then(function () {
                    return { detector: detector, video: video, stream: stream };
                });
            });
    })(formatsCsv)
    """,
)

/** Un pase del detector sobre el frame actual del vídeo. */
internal fun detectFrame(session: JsAny): Promise<JsAny> = js(
    "session.detector.detect(session.video)",
)

/**
 * Apaga la cámara. Es lo que el `awaitClose` del motor invoca al cancelarse la sesión: sin parar
 * cada track, el indicador de cámara del navegador se queda encendido tras salir de la pantalla.
 */
internal fun stopCameraSession(session: JsAny) {
    js(
        """
        (function (s) {
            s.video.pause();
            s.video.srcObject = null;
            s.stream.getTracks().forEach(function (t) { t.stop(); });
            if (s.video.isConnected) s.video.remove();
        })(session)
        """,
    )
}

/**
 * Coloca el `<video>` sobre el canvas de Compose, en el rectángulo que ocupa el visor.
 *
 * Va **encima** y no debajo porque el canvas no es transparente: el tema pinta su color de fondo en
 * toda la superficie, así que un vídeo detrás no se vería. La consecuencia es que el overlay de
 * detección queda tapado, y por eso el motor declara `occludesOverlay`.
 *
 * Las coordenadas son de viewport (`position: fixed`), que es lo que corresponde a la posición del
 * composable en la ventana. Asume que el canvas ocupa la página desde el origen — cierto en esta
 * app, donde `App()` es la raíz.
 */
internal fun attachSessionVideo(
    session: JsAny,
    left: Double,
    top: Double,
    width: Double,
    height: Double,
) {
    js(
        """
        (function (s, l, t, w, h) {
            var v = s.video;
            v.style.position = 'fixed';
            v.style.left = l + 'px';
            v.style.top = t + 'px';
            v.style.width = w + 'px';
            v.style.height = h + 'px';
            v.style.objectFit = 'cover';
            v.style.zIndex = '1';
            v.style.pointerEvents = 'none';
            if (!v.isConnected) document.body.appendChild(v);
        })(session, left, top, width, height)
        """,
    )
}

/** Saca el vídeo del documento sin apagar la cámara: la sesión puede seguir decodificando. */
internal fun detachSessionVideo(session: JsAny) {
    js("(function (s) { if (s.video.isConnected) s.video.remove(); })(session)")
}

/**
 * Decodifica una imagen ya capturada (RF-07).
 *
 * Recibe un data URL en lugar de bytes para no depender de `kotlinx-browser`: convertir un
 * `ByteArray` en `Uint8Array` exigiría esa librería, mientras que `fetch` sobre un data URL ya
 * produce el `Blob` que necesita `createImageBitmap`.
 */
internal fun detectDataUrl(formatsCsv: String, dataUrl: String): Promise<JsAny> = js(
    """
    (function (f, u) {
        // RNF-03: solo data URLs. El bitmap se arma con los bytes que ya están en memoria; si
        // alguna vez llegara aquí una URL remota, `fetch` sacaría la imagen del dispositivo.
        if (u.indexOf('data:') !== 0) throw new Error('solo se decodifican data URLs');
        var formats = f ? f.split(',') : undefined;
        var detector = new BarcodeDetector(formats ? { formats: formats } : undefined);
        return fetch(u)
            .then(function (r) { return r.blob(); })
            .then(function (b) { return createImageBitmap(b); })
            .then(function (img) { return detector.detect(img); });
    })(formatsCsv, dataUrl)
    """,
)

internal fun resultCount(results: JsAny): Int = js("results.length")

internal fun resultRawValue(results: JsAny, index: Int): String = js("results[index].rawValue")

internal fun resultFormat(results: JsAny, index: Int): String = js("results[index].format")

internal fun resultCornerCount(results: JsAny, index: Int): Int =
    js("results[index].cornerPoints ? results[index].cornerPoints.length : 0")

internal fun resultCornerX(results: JsAny, index: Int, corner: Int): Double =
    js("results[index].cornerPoints[corner].x")

internal fun resultCornerY(results: JsAny, index: Int, corner: Int): Double =
    js("results[index].cornerPoints[corner].y")

/** Tamaño real del frame, para normalizar las esquinas a `[0, 1]`. */
internal fun sessionFrameWidth(session: JsAny): Int = js("session.video.videoWidth")

internal fun sessionFrameHeight(session: JsAny): Int = js("session.video.videoHeight")

/**
 * Dimensiones de la última imagen estática decodificada. `createImageBitmap` no las expone en el
 * resultado del detector, así que se leen aparte del mismo data URL.
 */
internal fun imageSize(dataUrl: String): Promise<JsAny> = js(
    """
    (function (u) {
        if (u.indexOf('data:') !== 0) throw new Error('solo se decodifican data URLs');
        return fetch(u)
            .then(function (r) { return r.blob(); })
            .then(function (b) { return createImageBitmap(b); })
            .then(function (img) { return { w: img.width, h: img.height }; });
    })(dataUrl)
    """,
)

internal fun sizeWidth(size: JsAny): Int = js("size.w")

internal fun sizeHeight(size: JsAny): Int = js("size.h")
