package com.testscanner.core.platform

import com.testscanner.core.model.ScanImage

/**
 * Elegir una imagen del dispositivo para escanearla (RF-07).
 *
 * Está aparte de [PlatformActions] a propósito: las acciones sobre el resultado son *fire and
 * forget* y siempre están disponibles, mientras que elegir una imagen es una interacción con su
 * propio ciclo —abre una pantalla del sistema, puede cancelarse, puede fallar al leer el archivo— y
 * en Android necesita una Activity viva. Mezclarlas obligaría a que la implementación de las
 * acciones cargara con ese ciclo de vida sin necesitarlo.
 */
fun interface ImagePicker {
    suspend fun pickImage(): PickImageResult
}

/**
 * Resultado de elegir una imagen.
 *
 * Cancelar no es un fallo: es la salida más frecuente de un selector de archivos, y confundir las
 * dos cosas haría que la app mostrara un error cada vez que el usuario cambia de idea.
 */
sealed interface PickImageResult {

    data class Picked(val image: ScanImage) : PickImageResult

    data object Cancelled : PickImageResult

    data class Failed(val reason: String) : PickImageResult
}

/** Implementación inerte para tests y para plataformas donde todavía no hay selector. */
class NoOpImagePicker : ImagePicker {
    override suspend fun pickImage(): PickImageResult =
        PickImageResult.Failed("Esta plataforma no tiene selector de imágenes")
}
