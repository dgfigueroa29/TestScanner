package com.testscanner.platform

import com.testscanner.core.platform.FileSaver
import com.testscanner.core.platform.SaveFileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** Guardado de archivos en escritorio (RF-11): un diálogo de sistema y un `File`. */
class DesktopFileSaver : FileSaver {

    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: String,
    ): SaveFileResult = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Guardar historial"
            selectedFile = File(suggestedName)
        }

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return@withContext SaveFileResult.Cancelled
        }

        runCatching {
            val target = chooser.selectedFile
            target.writeText(content)
            SaveFileResult.Saved(target.absolutePath)
        }.getOrElse { failure ->
            SaveFileResult.Failed(failure.message ?: "No se pudo escribir el archivo")
        }
    }
}
