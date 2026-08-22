package com.whyscan.engines.vision

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor

/**
 * Punto de encuentro entre el motor y su preview.
 *
 * El motor crea la `AVCaptureSession` al arrancar y el composable necesita esa misma sesión para
 * construir su `AVCaptureVideoPreviewLayer`. Pero el composable puede entrar en composición antes
 * o después de que la sesión exista, así que hace falta un lugar estable donde ambos se encuentren
 * — y que además avise al preview cuando la sesión cambia.
 *
 * Es el equivalente iOS del `LifecycleCameraController` que en Android expone el motor de CameraX:
 * mismo reparto de responsabilidades, distinta API de plataforma.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CameraSessionHolder {

    var session: AVCaptureSession? = null
        private set

    private var device: AVCaptureDevice? = null

    private val listeners = mutableListOf<(AVCaptureSession?) -> Unit>()

    fun attach(session: AVCaptureSession, device: AVCaptureDevice) {
        this.session = session
        this.device = device
        listeners.toList().forEach { it(session) }
    }

    fun detach() {
        session = null
        device = null
        listeners.toList().forEach { it(null) }
    }

    fun addListener(listener: (AVCaptureSession?) -> Unit) {
        listeners += listener
        listener(session)
    }

    fun removeListener(listener: (AVCaptureSession?) -> Unit) {
        listeners -= listener
    }

    /**
     * `lockForConfiguration` es obligatorio antes de tocar la cámara y hay que soltarlo siempre:
     * dejar el dispositivo bloqueado impide que cualquier otra app lo use hasta que el proceso
     * muera.
     */
    fun setTorch(enabled: Boolean) {
        val camera = device ?: return
        if (!camera.hasTorch || !camera.isTorchModeSupported(AVCaptureTorchModeOn)) return

        withLockedDevice(camera) {
            it.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        }
    }

    fun setZoom(ratio: Float) {
        val camera = device ?: return
        withLockedDevice(camera) {
            // El rango real depende del hardware; pedir más de lo que admite lanza.
            it.videoZoomFactor = ratio.toDouble().coerceIn(MIN_ZOOM, it.activeFormatMaxZoom())
        }
    }

    /**
     * `lockForConfiguration` y `unlockForConfiguration` **no se importan**: cinterop las genera como
     * miembros de `AVCaptureDevice`, porque Apple las declara en la interfaz principal de la clase.
     * Lo que sí lleva import es todo lo que viene de una categoría —`hasTorch`, `torchMode`,
     * `videoZoomFactor`—, que se traduce a extensiones. Importarlas fue el primer error que dio el
     * runner macOS.
     */
    private inline fun withLockedDevice(camera: AVCaptureDevice, block: (AVCaptureDevice) -> Unit) {
        if (!camera.lockForConfiguration(null)) return
        try {
            block(camera)
        } finally {
            camera.unlockForConfiguration()
        }
    }

    private fun AVCaptureDevice.activeFormatMaxZoom(): Double =
        activeFormat.videoMaxZoomFactor.coerceAtMost(MAX_ZOOM_CAP)

    private companion object {
        const val MIN_ZOOM = 1.0

        // Tope de seguridad: algunos dispositivos reportan factores enormes que solo son zoom
        // digital y degradan la imagen hasta hacerla inservible para decodificar.
        const val MAX_ZOOM_CAP = 10.0
    }
}
