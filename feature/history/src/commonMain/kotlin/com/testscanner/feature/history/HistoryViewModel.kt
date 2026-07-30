package com.testscanner.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.usecase.ClearScanHistoryUseCase
import com.testscanner.core.domain.usecase.ObserveScanHistoryUseCase
import com.testscanner.core.model.ScannerEngineId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

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
            HistoryAction.Clear -> clear()
        }
    }

    private fun filterBy(id: ScannerEngineId?) {
        _state.update { it.copy(engineFilter = id) }
    }

    private fun clear() {
        viewModelScope.launch { clearHistory() }
    }
}
