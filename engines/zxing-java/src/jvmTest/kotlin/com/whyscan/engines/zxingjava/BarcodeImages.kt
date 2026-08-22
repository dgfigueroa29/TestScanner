package com.whyscan.engines.zxingjava

import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.whyscan.core.model.ScanImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.google.zxing.BarcodeFormat as ZXingFormat

/**
 * Genera imágenes PNG con códigos reales, usando el codificador del propio ZXing.
 *
 * Es lo que convierte a este en el primer motor del proyecto verificado de extremo a extremo sin
 * dispositivo: el test no le entrega un doble que devuelve lo esperado, sino píxeles que hay que
 * binarizar y decodificar de verdad.
 *
 * Los márgenes se dejan en los que ZXing usa por defecto para cada simbología, y no en uno fijo:
 * la zona muda de un EAN-13 es de diez módulos y la de un QR de cuatro. Forzar el mismo número en
 * las dos generaba códigos que el propio ZXing no podía volver a leer — un fallo del generador de
 * fixtures que parecía un fallo del motor.
 */
internal object BarcodeImages {

    fun png(
        value: String,
        format: ZXingFormat = ZXingFormat.QR_CODE,
        width: Int = 300,
        height: Int = 300,
    ): ScanImage = MultiFormatWriter().encode(value, format, width, height).toPng()

    /** Une varias imágenes en una sola fila, para probar la lectura de varios códigos a la vez. */
    fun sideBySide(vararg images: BufferedImage): ScanImage {
        val totalWidth = images.sumOf { it.width }
        val maxHeight = images.maxOf { it.height }
        val canvas = BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_RGB)

        val graphics = canvas.createGraphics()
        try {
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, totalWidth, maxHeight)
            var x = 0
            images.forEach { image ->
                graphics.drawImage(image, x, 0, null)
                x += image.width
            }
        } finally {
            graphics.dispose()
        }

        return canvas.toPng()
    }

    fun bitmap(value: String, format: ZXingFormat, width: Int = 300, height: Int = 300): BufferedImage =
        MultiFormatWriter().encode(value, format, width, height).toBufferedImage()

    private fun BitMatrix.toPng(): ScanImage = toBufferedImage().toPng()

    private fun BitMatrix.toBufferedImage(): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, if (this[x, y]) BLACK else WHITE)
            }
        }
        return image
    }

    private fun BufferedImage.toPng(): ScanImage {
        val bytes = ByteArrayOutputStream()
        ImageIO.write(this, "png", bytes)
        return ScanImage(
            encoded = bytes.toByteArray(),
            mimeType = "image/png",
            widthPx = width,
            heightPx = height,
        )
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
}
