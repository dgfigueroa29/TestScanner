package com.whyscan.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.export.HistoryExporter
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.usecase.ScanHistory
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.platform.SaveFileResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Historial de escaneos.
 *
 * No sabe si detrás hay Room o un almacén en memoria — solo conoce [ScanHistory]. Esa es la razón de
 * que cambiar el almacén en la Fase 2 no haya tocado ni el dominio ni la UI, y de que añadir las
 * notas no haya tocado el escáner.
 */
class HistoryViewModel(
    private val history: ScanHistory,
    private val platformActions: PlatformActions,
    private val fileSaver: FileSaver,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState(canShare = platformActions.canShare))
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<HistoryEffect>()
    val effects: SharedFlow<HistoryEffect> = _effects.asSharedFlow()

    /**
     * La última lectura borrada, esperando por si el usuario se arrepiente.
     *
     * Vive en el ViewModel y no en el estado a propósito: no se pinta en ninguna parte y meterlo en
     * `HistoryState` haría que cada borrado repintara la lista entera por un dato que nadie mira.
     * Guarda el [HistoryEntry] completo —nota incluida— porque restituir media fila no es restituir.
     */
    private var lastDeleted: HistoryEntry? = null

    init {
        viewModelScope.launch {
            history.observe().collect { entries ->
                _state.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.FilterByEngine -> filterBy(action.id)
            is HistoryAction.Search -> _state.update { it.copy(query = action.query) }
            is HistoryAction.RunResultAction -> runResultAction(action.action, action.text)
            is HistoryAction.EditNote -> _state.update { it.copy(editingNoteFor = action.detectionId) }
            is HistoryAction.SetNote -> setNote(action.detectionId, action.note)
            is HistoryAction.Delete -> delete(action.detectionId)
            HistoryAction.UndoDelete -> undoDelete()
            is HistoryAction.Export -> export(action.format)
            HistoryAction.ConfirmClear -> _state.update { it.copy(isConfirmingClear = true) }
            HistoryAction.DismissClear -> _state.update { it.copy(isConfirmingClear = false) }
            HistoryAction.Clear -> clear()
        }
    }

    private fun filterBy(id: ScannerEngineId?) {
        _state.update { it.copy(engineFilter = id) }
    }

    /**
     * Copiar, compartir o abrir un resultado guardado (RF-13).
     *
     * Es el caso de uso más frecuente del historial: se escaneó algo antes y ahora hace falta
     * pegarlo en otro lado. Sin esto, el historial solo servía para mirar.
     */
    private fun runResultAction(action: ResultAction, text: String) {
        viewModelScope.launch {
            val (succeeded, failure) = when (action) {
                ResultAction.Copy ->
                    platformActions.copyToClipboard(text) to HistoryMessage.CopyFailed

                ResultAction.Share ->
                    platformActions.share(text) to HistoryMessage.ShareFailed

                is ResultAction.Open ->
                    platformActions.openUrl(action.uri) to HistoryMessage.OpenFailed
            }

            val message: HistoryMessage? = when {
                !succeeded -> failure
                action == ResultAction.Copy -> HistoryMessage.Copied
                else -> null
            }

            message?.let { _effects.emit(HistoryEffect.ShowMessage(it)) }
        }
    }

    /**
     * Asocia una nota a una lectura, o la borra si el texto queda vacío.
     *
     * El campo se cierra antes de que el guardado termine y no después, a propósito: la lista se
     * repinta desde el `Flow` del almacén, así que esperar dejaría el teclado abierto un instante
     * sobre una fila que ya cambió. Si el guardado fallara, el estado del que se repinta es el del
     * almacén y la nota simplemente no aparecería — que es la verdad.
     *
     * Quién decide si `"  "` es una nota no se decide aquí: lo normaliza [ScanHistory] para las tres
     * plataformas a la vez.
     */
    private fun setNote(detectionId: String, note: String) {
        _state.update { it.copy(editingNoteFor = null) }

        viewModelScope.launch {
            history.setNote(detectionId, note)

            val message = if (HistoryEntry.normalizeNote(note) == null) {
                HistoryMessage.NoteRemoved
            } else {
                HistoryMessage.NoteSaved
            }
            _effects.emit(HistoryEffect.ShowMessage(message))
        }
    }

    /**
     * Borra una lectura y se la guarda por si el usuario se arrepiente.
     *
     * La copia se toma **antes** de borrar y del estado, que es el único sitio donde todavía existe:
     * después del `delete` el almacén ya no la tiene y no habría de dónde sacarla. Si la fila no
     * está en el estado —una carrera con la poda— no se borra nada y no se ofrece deshacer.
     */
    private fun delete(detectionId: String) {
        val entry = _state.value.entries.firstOrNull { it.id == detectionId } ?: return
        lastDeleted = entry

        viewModelScope.launch {
            history.delete(detectionId)
            _effects.emit(HistoryEffect.ShowMessage(HistoryMessage.EntryDeleted, undoable = true))
        }
    }

    /**
     * Devuelve al historial lo último que se borró.
     *
     * `lastDeleted` se limpia al restituir para que un segundo toque en un aviso que siga en
     * pantalla no vuelva a insertar lo mismo. No hace falta más: restituir es idempotente en las
     * tres implementaciones, pero un no-op explícito se lee mejor que confiar en ello.
     */
    private fun undoDelete() {
        val entry = lastDeleted ?: return
        lastDeleted = null

        viewModelScope.launch { history.restore(entry) }
    }

    /**
     * Exporta lo que se está viendo, no todo el historial (RF-11).
     *
     * Es deliberado: si el usuario filtró por un motor o buscó algo, exportar el conjunto entero le
     * daría un archivo que no se parece a la pantalla que tiene delante. `visible` es justo lo que ve.
     */
    private fun export(format: ExportFormat) {
        if (_state.value.isExporting) return

        val entries = _state.value.visible
        if (entries.isEmpty()) {
            viewModelScope.launch { _effects.emit(HistoryEffect.ShowMessage(HistoryMessage.NothingToExport)) }
            return
        }

        _state.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            try {
                val result = fileSaver.save(
                    suggestedName = HistoryExporter.fileName(format),
                    mimeType = format.mimeType,
                    content = HistoryExporter.export(entries, format),
                )

                val message = when (result) {
                    is SaveFileResult.Saved -> HistoryMessage.Exported(result.location)
                    is SaveFileResult.Failed -> HistoryMessage.ExportFailed(result.reason)
                    // Cancelar no es un fallo: el usuario cambió de idea y no hay nada que contarle.
                    SaveFileResult.Cancelled -> null
                }

                message?.let { _effects.emit(HistoryEffect.ShowMessage(it)) }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    /** Solo se llega aquí tras confirmar: ver la nota de `HistoryAction.ConfirmClear`. */
    private fun clear() {
        _state.update { it.copy(isConfirmingClear = false) }
        viewModelScope.launch { history.clear() }
    }
}
