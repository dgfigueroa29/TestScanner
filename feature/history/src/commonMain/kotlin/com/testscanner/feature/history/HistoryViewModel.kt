package com.testscanner.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.domain.usecase.ClearScanHistoryUseCase
import com.testscanner.core.domain.usecase.ObserveScanHistoryUseCase
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.platform.PlatformActions
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
            is HistoryAction.RunResultAction -> runResultAction(action.detection, action.action)
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
    private fun runResultAction(detection: Detection, action: ResultAction) {
        viewModelScope.launch {
            val text = ResultActionsFactory.shareableText(detection.barcode)

            val (succeeded, failureMessage) = when (action) {
                ResultAction.Copy ->
                    platformActions.copyToClipboard(text) to "No se pudo copiar al portapapeles"

                ResultAction.Share ->
                    platformActions.share(text) to "No se pudo abrir la hoja de compartir"

                is ResultAction.Open ->
                    platformActions.openUrl(action.uri) to "Ninguna app puede abrir esto"
            }

            val message = when {
                !succeeded -> failureMessage
                action == ResultAction.Copy -> "Copiado"
                else -> null
            }

            message?.let { _effects.emit(HistoryEffect.ShowMessage(it)) }
        }
    }

    private fun clear() {
        viewModelScope.launch { clearHistory() }
    }
}
