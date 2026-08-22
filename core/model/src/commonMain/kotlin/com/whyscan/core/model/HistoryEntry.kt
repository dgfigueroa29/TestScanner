package com.whyscan.core.model

/**
 * Una fila del historial: la [Detection] que ocurrió, más lo que el usuario haya añadido después.
 *
 * ## Por qué la nota no vive dentro de [Detection]
 *
 * [Detection] es **el hecho de haber leído un código**: lo produce un motor, lo atraviesan los
 * decoradores del SPI, lo compara el comparador y lo puntúa el marcador. Es un dato de máquina y
 * describe algo que ya pasó, así que nada de lo que haya dentro puede cambiar después.
 *
 * Una nota es lo contrario en las dos dimensiones: la escribe una persona y la escribe **más tarde**.
 * Meterla en [Detection] obligaría a los ocho motores, a los seis decoradores y a la suite de
 * contrato a acarrear un campo que en toda esa mitad del sistema es siempre `null`, y dejaría un
 * modelo de dominio en el que "dos lecturas del mismo código son iguales" pasa a depender de si
 * alguien escribió algo. Aquí la separación cuesta un tipo y evita las dos cosas.
 *
 * El paralelo exacto ya estaba en el proyecto: `Barcode` es qué dice el código y [Detection] es
 * quién lo leyó y cuándo. Esto añade el tercer nivel — qué significa para el usuario.
 */
data class HistoryEntry(
    val detection: Detection,
    /**
     * Texto de referencia que el usuario asocia a la lectura. `null` es "no hay nota" y es distinto
     * de `""`: los almacenes normalizan la cadena vacía a `null` para que borrar una nota no deje
     * una fila que dice tener una y no tiene nada dentro.
     */
    val note: String? = null,
) {
    /** El id de la detección **es** el id de la fila: la relación es uno a uno. */
    val id: String get() = detection.id

    val hasNote: Boolean get() = !note.isNullOrBlank()

    companion object {
        /**
         * Normaliza lo que llega de un campo de texto.
         *
         * Vive aquí y no en cada almacén porque los tres —Room, el JSON del navegador y el de
         * memoria— tienen que coincidir: si uno guardara `"   "` y otro `null`, la misma app daría
         * historiales distintos según la plataforma.
         */
        fun normalizeNote(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }
    }
}
