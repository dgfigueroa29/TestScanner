package com.testscanner.engines.zxing

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
 * Punto de encuentro entre el motor y su preview, igual que el de `:engines:vision-ios`.
 *
 * Está duplicado a sabiendas. Factorizarlo crearía una dependencia entre dos motores que deben
 * poder eliminarse por separado (RNF-06, RNF-07), y "quitar un motor" pasaría de borrar una línea
 * de `settings.gradle.kts` a un refactor. Son ochenta líneas de puente con AVFoundation; el
 * acoplamiento costaría más.
 */
@OptIn(ExperimentalForeignApi::class)
internal class ZXingCameraSessionHolder {

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
            it.videoZoomFactor = ratio.toDouble().coerceIn(MIN_ZOOM, it.activeFormatMaxZoom())
        }
    }

    /**
     * Dejar el dispositivo bloqueado impide que otra app lo use hasta que muera el proceso.
     *
     * `lockForConfiguration` y `unlockForConfiguration` no se importan: son miembros de
     * `AVCaptureDevice`, no extensiones. Ver la nota en el holder de `:engines:vision-ios`.
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
        const val MAX_ZOOM_CAP = 10.0
    }
}
