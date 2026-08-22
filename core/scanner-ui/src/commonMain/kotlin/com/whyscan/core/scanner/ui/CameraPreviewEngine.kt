package com.whyscan.core.scanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Capacidad opcional: **el motor aporta su propia superficie de preview**.
 *
 * Es la extensión natural de las capacidades segregadas de ADR-0002 al terreno de la UI, y resuelve
 * un problema concreto: la superficie de vídeo es lo único de la pantalla que es irreduciblemente
 * nativo, y cada motor la produce de forma distinta — `PreviewView` de CameraX en Android, una
 * `AVCaptureVideoPreviewLayer` en iOS, un `<video>` en el navegador.
 *
 * Las alternativas eran peores:
 * - Un `expect @Composable CameraPreview` en la feature obligaría a que `:feature:scanner`
 *   dependiera de cada módulo de motor, y añadir un motor pasaría a tocar la feature (rompe RNF-07).
 * - Exponer un handle opaco (`Any`) desde el SPI obligaría a la UI a hacer casts por motor, que es
 *   exactamente el `when (engineId)` que la arquitectura evita.
 *
 * Con esta interfaz, la UI hace `(engine as? CameraPreviewEngine)?.CameraPreview(modifier)` y no
 * conoce ni un solo motor concreto. El precio es que los módulos de motor con cámara dependen de
 * Compose; es un precio real y asumido: son módulos de plataforma, no de dominio.
 *
 * El overlay de detección **no** se pinta aquí: se dibuja encima en Compose común, sobre los
 * `cornerPoints` normalizados que reporta el motor. Así el 100 % del diseño visual es compartido y
 * solo la superficie de vídeo es nativa.
 */
interface CameraPreviewEngine {

    /**
     * Superficie de vídeo del motor. La implementación es responsable de enlazar la cámara al
     * ciclo de vida del composable y de soltarla cuando este sale de la composición.
     */
    @Composable
    fun CameraPreview(modifier: Modifier)

    /**
     * Si la superficie tapa cualquier cosa que Compose pinte encima.
     *
     * Es `false` en Android y iOS: allí el vídeo vive **dentro** del árbol de Compose (`AndroidView`,
     * `UIKitView`) y el overlay se dibuja sobre él sin problema.
     *
     * En el navegador no hay equivalente: Compose pinta sobre un `<canvas>` y el `<video>` es un
     * elemento del DOM que se coloca **encima**. El overlay seguiría dibujándose —en el canvas, bajo
     * el vídeo— y simplemente no se vería. Declararlo permite que la pantalla no lo pinte, en lugar
     * de dejar código que se ejecuta y no produce nada visible.
     */
    val occludesOverlay: Boolean get() = false
}
