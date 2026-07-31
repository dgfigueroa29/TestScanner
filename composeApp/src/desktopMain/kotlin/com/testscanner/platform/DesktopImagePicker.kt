package com.testscanner.platform

import com.testscanner.core.model.ScanImage
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PickImageResult
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selector de imágenes de escritorio (RF-07).
 *
 * En escritorio no hay galería: hay un sistema de archivos. `JFileChooser` se abre sin ventana
 * padre porque el diálogo es modal por sí mismo y buscar el `Window` de Compose para pasárselo
 * ataría este archivo a la jerarquía de Swing que Compose Desktop monta por debajo.
 *
 * Hoy este selector no tiene a quién servir —en escritorio el único motor es la entrada manual, que
 * no lee imágenes, así que la UI ni ofrece el botón (`canScanFromImage`)—. Existe igualmente porque
 * el día que Escritorio tenga decodificador (deuda D13) lo único que faltará es el motor.
 */
class DesktopImagePicker : ImagePicker {

    override suspend fun pickImage(): PickImageResult = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Elegir una imagen"
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Imágenes", *SUPPORTED_EXTENSIONS)
        }

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext PickImageResult.Cancelled
        }

        read(chooser.selectedFile)
    }

    private fun read(file: File): PickImageResult = runCatching {
        val bytes = file.readBytes()
        // `ImageIO.read` decodifica la imagen entera; en escritorio la memoria no es el problema
        // que sí es en un móvil, y a cambio las dimensiones salen sin depender del formato.
        val image = ImageIO.read(file)

        PickImageResult.Picked(
            ScanImage(
                encoded = bytes,
                mimeType = file.mimeType(),
                widthPx = image?.width,
                heightPx = image?.height,
            ),
        )
    }.getOrElse { failure ->
        PickImageResult.Failed(failure.message ?: "No se pudo leer ${file.name}")
    }

    private fun File.mimeType(): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = arrayOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    }
}
