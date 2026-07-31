package com.testscanner.engines.zxing

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import zxingcpp.ImageFormat
import zxingcpp.ImageView

/**
 * Convierte una imagen ya capturada en algo que zxing-cpp pueda leer (RF-07).
 *
 * A diferencia del frame de cámara —que llega como luminancia y se pasa tal cual— aquí hay que
 * **rasterizar**: un JPEG o un PNG no tienen píxeles hasta que se dibujan. Se usa RGBA y no gris
 * porque dibujar en un contexto de escala de grises depende del espacio de color de la imagen
 * original, y una foto con perfil raro saldría en negro sin avisar.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toRgbaImageView(): ImageView? {
    val image = usePinned { pinned ->
        UIImage.imageWithData(NSData.create(bytes = pinned.addressOf(0), length = size.convert()))
    } ?: return null

    val cgImage = image.CGImage ?: return null
    val (width, height) = image.size.useContents { width.toInt() to height.toInt() }
    if (width <= 0 || height <= 0) return null

    val rowStride = width * BYTES_PER_PIXEL
    val pixels = ByteArray(rowStride * height)

    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
    try {
        pixels.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.convert(),
                height = height.convert(),
                bitsPerComponent = BITS_PER_COMPONENT.convert(),
                bytesPerRow = rowStride.convert(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return null

            try {
                CGContextDrawImage(
                    context,
                    CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                    cgImage,
                )
            } finally {
                CGContextRelease(context)
            }
        }
    } finally {
        CGColorSpaceRelease(colorSpace)
    }

    return ImageView(
        data = pixels,
        width = width,
        height = height,
        format = ImageFormat.RGBA,
        rowStride = rowStride,
    )
}

private const val BYTES_PER_PIXEL = 4
private const val BITS_PER_COMPONENT = 8
