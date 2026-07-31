package com.testscanner.platform

import android.content.Context
import android.net.Uri
import com.testscanner.core.platform.FileSaver
import com.testscanner.core.platform.SaveFileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puente hacia el diálogo de "guardar como" de la Activity visible.
 *
 * Tercer préstamo de launcher del proyecto, con el mismo reparto que los otros dos: **abrir** el
 * diálogo necesita una Activity, **escribir** en el `Uri` resultante solo necesita un Context.
 */
fun interface DocumentRequester {
    /** `content://` donde el usuario quiere escribir, o `null` si canceló. */
    suspend fun createDocument(mimeType: String, suggestedName: String): Uri?
}

/**
 * Guardado de archivos en Android (RF-11).
 *
 * Usa el selector del sistema (`ACTION_CREATE_DOCUMENT`) y no una ruta propia: no requiere permisos
 * de almacenamiento en ninguna versión, el archivo queda donde el usuario lo puso —Descargas,
 * Drive, lo que tenga— y sobrevive a desinstalar la app. Escribir en el directorio privado sería
 * más fácil y el archivo se perdería con la app.
 */
class AndroidFileSaver(private val context: Context) : FileSaver {

    @Volatile
    var requester: DocumentRequester? = null

    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: String,
    ): SaveFileResult {
        val launcher = requester
            ?: return SaveFileResult.Failed("No hay ninguna pantalla activa para elegir destino")

        val uri = launcher.createDocument(mimeType, suggestedName)
            ?: return SaveFileResult.Cancelled

        return withContext(Dispatchers.IO) { write(uri, content) }
    }

    private fun write(uri: Uri, content: String): SaveFileResult = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.encodeToByteArray())
        } ?: return SaveFileResult.Failed("No se pudo abrir el destino elegido")

        SaveFileResult.Saved(uri.lastPathSegment)
    }.getOrElse { failure ->
        SaveFileResult.Failed(failure.message ?: "No se pudo escribir el archivo")
    }
}
