package com.whyscan.core.data.repository

import com.whyscan.core.model.HistoryEntry

/**
 * Recorta el historial a [max] entradas **sin tocar las que tienen nota**.
 *
 * ### Por qué las notas no se podan
 * El techo existe para acotar el volumen, y el volumen lo produce el escaneo continuo: una sesión
 * larga deja cientos de lecturas y ninguna nota. Una nota, en cambio, es la señal más explícita que
 * da el usuario de que esa lectura le importa. Borrarla por antigüedad es perder, sin avisar, lo
 * único del historial que alguien se molestó en escribir a mano.
 *
 * ### Por qué es una función suelta y no un método
 * La comparten los dos almacenes de `:core:data`, y su gemela vive en el `trimTo` de Room como una
 * cláusula `WHERE note IS NULL`. Tres implementaciones de la misma regla es lo que hace que el
 * historial de Web y el de las otras tres plataformas sigan siendo comparables — que es la razón por
 * la que este proyecto guarda exactamente los mismos campos en todas partes. Estando suelta se
 * prueba directamente, sin levantar ningún repositorio.
 */
internal fun List<HistoryEntry>.trimmedKeepingNotes(max: Int): List<HistoryEntry> {
    if (size <= max) return this

    var room = max
    return filter { entry ->
        when {
            entry.hasNote -> true
            room > 0 -> {
                room--
                true
            }

            else -> false
        }
    }
}
