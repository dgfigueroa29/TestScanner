package com.testscanner.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.testscanner.core.model.ScanImage
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PickImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puente hacia el selector de la Activity visible.
 *
 * Es el mismo reparto que `PermissionRequester`: **abrir** el selector necesita una Activity, pero
 * **leer** el archivo elegido solo necesita un Context. El picker vive en el grafo como singleton,
 * así que retener la Activity filtraría memoria en cada rotación.
 */
fun interface ImageRequester {
    /** `content://` de la imagen elegida, o `null` si el usuario canceló. */
    suspend fun pickImageUri(): Uri?
}

/**
 * Selector de imágenes de Android (RF-07).
 *
 * No pide ningún permiso, y no es un descuido: el *photo picker* del sistema corre fuera de la app
 * y devuelve únicamente lo que el usuario selecciona, así que no hay nada que conceder. Pedir
 * `READ_MEDIA_IMAGES` daría acceso a toda la galería para leer una sola foto.
 */
class AndroidImagePicker(private val context: Context) : ImagePicker {

    @Volatile
    var requester: ImageRequester? = null

    override suspend fun pickImage(): PickImageResult {
        val launcher = requester
            ?: return PickImageResult.Failed("No hay ninguna pantalla activa para abrir el selector")

        val uri = launcher.pickImageUri() ?: return PickImageResult.Cancelled

        // Leer el archivo es I/O de disco y puede ser de varios megas: fuera del hilo principal.
        return withContext(Dispatchers.IO) { read(uri) }
    }

    private fun read(uri: Uri): PickImageResult = runCatching {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return PickImageResult.Failed("No se pudo abrir la imagen elegida")

        val bounds = bytes.bounds()

        PickImageResult.Picked(
            ScanImage(
                encoded = bytes,
                mimeType = resolver.getType(uri) ?: DEFAULT_MIME_TYPE,
                widthPx = bounds.outWidth.takeIf { it > 0 },
                heightPx = bounds.outHeight.takeIf { it > 0 },
            ),
        )
    }.getOrElse { failure ->
        PickImageResult.Failed(failure.message ?: "No se pudo leer la imagen")
    }

    /**
     * Dimensiones sin cargar el bitmap completo en memoria. `inJustDecodeBounds` decodifica solo la
     * cabecera; hacerlo entero solo para saber el tamaño podría reventar con una foto de 50 MP.
     */
    private fun ByteArray.bounds(): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(this@bounds, 0, size, this)
        }

    private companion object {
        const val DEFAULT_MIME_TYPE = "image/*"
    }
}
