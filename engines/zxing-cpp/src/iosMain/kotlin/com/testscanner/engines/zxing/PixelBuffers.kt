package com.testscanner.engines.zxing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.posix.memcpy
import zxingcpp.ImageFormat
import zxingcpp.ImageView

/**
 * Convierte un frame de la cámara en algo que zxing-cpp pueda leer.
 *
 * Se copia **solo el plano de luminancia** del buffer YUV. No es un atajo: un decodificador de
 * códigos de barras trabaja sobre intensidad, el color no aporta nada, y saltarse la conversión a
 * RGB ahorra el trabajo más caro del pipeline en cada frame.
 *
 * `rowStride` se pasa tal cual porque los buffers de CoreVideo vienen alineados y su ancho en bytes
 * es casi siempre mayor que el ancho en píxeles. Ignorarlo produciría una imagen inclinada — el
 * fallo clásico de este puente, y silencioso: no falla, simplemente no lee nada.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CVPixelBufferRef.toLumaImageView(): ImageView? {
    CVPixelBufferLockBaseAddress(this, kCVPixelBufferLock_ReadOnly)
    try {
        val base = CVPixelBufferGetBaseAddressOfPlane(this, LUMA_PLANE) ?: return null
        val rowStride = CVPixelBufferGetBytesPerRowOfPlane(this, LUMA_PLANE).toInt()
        val width = CVPixelBufferGetWidthOfPlane(this, LUMA_PLANE).toInt()
        val height = CVPixelBufferGetHeightOfPlane(this, LUMA_PLANE).toInt()
        if (width <= 0 || height <= 0 || rowStride <= 0) return null

        val bytes = ByteArray(rowStride * height)
        bytes.usePinned { destination ->
            memcpy(destination.addressOf(0), base, (rowStride * height).convert())
        }

        return ImageView(
            data = bytes,
            width = width,
            height = height,
            format = ImageFormat.Lum,
            rowStride = rowStride,
        )
    } finally {
        CVPixelBufferUnlockBaseAddress(this, kCVPixelBufferLock_ReadOnly)
    }
}

/** `size_t` en el binding: el índice del plano de luminancia dentro del buffer YUV. */
private const val LUMA_PLANE: ULong = 0uL
