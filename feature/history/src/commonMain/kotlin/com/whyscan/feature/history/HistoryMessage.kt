package com.whyscan.feature.history

/**
 * Lo que el ViewModel del historial quiere contarle al usuario, **sin texto**.
 *
 * Mismo motivo que en la pantalla de escaneo: el ViewModel dice qué pasó y la pantalla decide cómo
 * se llama, con `composeResources`. Es un tipo aparte y no uno compartido porque los recursos de
 * Compose son por módulo: un mensaje común obligaría a las dos features a compartir un módulo de
 * strings solo para no repetir cuatro casos.
 */
sealed interface HistoryMessage {

    data object Copied : HistoryMessage

    data object CopyFailed : HistoryMessage

    data object ShareFailed : HistoryMessage

    data object OpenFailed : HistoryMessage

    /** La exportación terminó. `location` es `null` donde el sistema no revela el destino. */
    data class Exported(val location: String?) : HistoryMessage

    data object NothingToExport : HistoryMessage

    /** La nota se guardó. Se confirma porque el campo se cierra y si no, no queda ni rastro. */
    data object NoteSaved : HistoryMessage

    data object NoteRemoved : HistoryMessage

    data object EntryDeleted : HistoryMessage

    /** Motivo que da la plataforma al no poder escribir. Ver la nota de `ScannerMessage.Raw`. */
    data class ExportFailed(val reason: String) : HistoryMessage
}
