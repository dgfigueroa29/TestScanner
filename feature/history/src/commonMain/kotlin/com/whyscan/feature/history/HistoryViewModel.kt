package com.whyscan.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.export.HistoryExporter
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.usecase.ClearScanHistoryUseCase
import com.whyscan.core.domain.usecase.ObserveScanHistoryUseCase
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
 * No sabe si detrás hay Room o un almacén en memoria — solo conoce los casos de uso. Esa es la razón
 * de que cambiar el almacén en la Fase 2 no haya tocado ni el dominio ni la UI.
 */
class HistoryViewModel(
    private val observeHistory: ObserveScanHistoryUseCase,
    private val clearHistory: ClearScanHistoryUseCase,
    private val platformActions: PlatformActions,
    private val fileSaver: FileSaver,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState(canShare = platformActions.canShare))
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<HistoryEffect>()
    val effects: SharedFlow<HistoryEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            observeHistory().collect { detections ->
                _state.update { it.copy(detections = detections, isLoading = false) }
            }
        }
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.FilterByEngine -> filterBy(action.id)
            is HistoryAction.RunResultAction -> runResultAction(action.action, action.text)
            is HistoryAction.Export -> export(action.format)
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
     * Exporta lo que se está viendo, no todo el historial (RF-11).
     *
     * Es deliberado: si el usuario filtró por un motor, exportar el conjunto entero le daría un
     * archivo que no se parece a la pantalla que tiene delante. `visible` es justo lo que ve.
     */
    private fun export(format: ExportFormat) {
        if (_state.value.isExporting) return

        val detections = _state.value.visible
        if (detections.isEmpty()) {
            viewModelScope.launch { _effects.emit(HistoryEffect.ShowMessage(HistoryMessage.NothingToExport)) }
            return
        }

        _state.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            try {
                val result = fileSaver.save(
                    suggestedName = HistoryExporter.fileName(format),
                    mimeType = format.mimeType,
                    content = HistoryExporter.export(detections, format),
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

    private fun clear() {
        viewModelScope.launch { clearHistory() }
    }
}
