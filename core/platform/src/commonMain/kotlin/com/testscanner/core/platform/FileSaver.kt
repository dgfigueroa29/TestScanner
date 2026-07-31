package com.testscanner.core.platform

/**
 * Guardar un archivo de texto donde el usuario decida.
 *
 * Tercer servicio del sistema del módulo, y separado de los otros dos por el mismo criterio:
 * [PlatformActions] son acciones instantáneas siempre disponibles, [ImagePicker] trae algo de
 * fuera y esto lleva algo hacia fuera. Los tres tienen ciclos distintos y ninguno necesita a los
 * otros.
 *
 * Recibe el contenido ya formado —no un `ByteArray` ni un flujo— porque una exportación de
 * historial cabe holgadamente en memoria: son unos cientos de filas de texto. Streaming complicaría
 * las cuatro implementaciones para un caso que no existe.
 */
fun interface FileSaver {
    suspend fun save(suggestedName: String, mimeType: String, content: String): SaveFileResult
}

/**
 * Resultado de guardar.
 *
 * Cancelar se distingue de fallar por el mismo motivo que en [PickImageResult]: es la salida
 * frecuente de un diálogo de archivos y tratarla como error avisaría de un problema inexistente
 * cada vez que el usuario se arrepiente.
 */
sealed interface SaveFileResult {

    /** [location] es lo que se pueda mostrar del destino; algunos sistemas no lo revelan. */
    data class Saved(val location: String?) : SaveFileResult

    data object Cancelled : SaveFileResult

    data class Failed(val reason: String) : SaveFileResult
}

/** Implementación inerte para tests y para plataformas sin destino donde escribir. */
class NoOpFileSaver : FileSaver {
    override suspend fun save(
        suggestedName: String,
        mimeType: String,
        content: String,
    ): SaveFileResult = SaveFileResult.Failed("Esta plataforma no puede guardar archivos")
}
