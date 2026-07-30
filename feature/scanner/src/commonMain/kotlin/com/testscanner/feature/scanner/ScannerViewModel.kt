package com.testscanner.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.domain.usecase.ObserveEngineCatalogUseCase
import com.testscanner.core.domain.usecase.ObserveScanPreferencesUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.SetPreferredEngineUseCase
import com.testscanner.core.domain.usecase.SetScanFormatsUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.TextInputEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI de la pantalla de escaneo.
 *
 * El ViewModel **no conoce ningún motor concreto**: pide una sesión al caso de uso y reacciona a
 * los [ScanEvent] que llegan. Toda la lógica de selección y degradación vive en el dominio, así
 * que añadir un motor nuevo no toca este archivo (RNF-07).
 */
class ScannerViewModel(
    private val observeCatalog: ObserveEngineCatalogUseCase,
    private val observePreferences: ObserveScanPreferencesUseCase,
    private val setPreferredEngine: SetPreferredEngineUseCase,
    private val setScanFormats: SetScanFormatsUseCase,
    private val startScanSession: StartScanSessionUseCase,
    private val saveDetection: SaveDetectionUseCase,
    private val preferencesRepository: ScanPreferencesRepository,
    private val engineRepository: ScannerEngineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ScannerEffect>()
    val effects: SharedFlow<ScannerEffect> = _effects.asSharedFlow()

    private var sessionJob: Job? = null

    init {
        observeCatalogChanges()
        observePreferenceChanges()
    }

    fun onAction(action: ScannerAction) {
        when (action) {
            ScannerAction.Refresh -> refresh()
            ScannerAction.StartSession -> startSession()
            ScannerAction.StopSession -> stopSession()
            is ScannerAction.SelectEngine -> selectEngine(action.id)
            is ScannerAction.ToggleFormat -> toggleFormat(action.format)
            is ScannerAction.SetContinuous -> setContinuous(action.enabled)
            is ScannerAction.ManualInputChanged -> _state.update { it.copy(manualInput = action.value) }
            ScannerAction.SubmitManualInput -> submitManualInput()
            ScannerAction.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun observeCatalogChanges() {
        viewModelScope.launch {
            observeCatalog().collect { catalog ->
                _state.update { it.copy(catalog = catalog, isLoading = false) }
            }
        }
    }

    private fun observePreferenceChanges() {
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                _state.update {
                    it.copy(
                        selectedEngineId = preferences.preferredEngineId,
                        formats = preferences.formats,
                        continuous = preferences.continuous,
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch { engineRepository.refresh() }
    }

    private fun selectEngine(id: ScannerEngineId?) {
        viewModelScope.launch {
            setPreferredEngine(id)
            if (_state.value.sessionStatus == SessionStatus.Scanning) {
                stopSession()
                startSession()
            }
        }
    }

    private fun toggleFormat(format: BarcodeFormat) {
        viewModelScope.launch {
            val current = _state.value.formats
            setScanFormats(if (format in current) current - format else current + format)
        }
    }

    private fun setContinuous(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setContinuous(enabled) }
    }

    private fun startSession() {
        sessionJob?.cancel()
        _state.update {
            it.copy(
                sessionStatus = SessionStatus.Starting,
                detections = emptyList(),
                switchedFrom = null,
                error = null,
            )
        }

        sessionJob = viewModelScope.launch {
            val preferences = preferencesRepository.current()
            val request = ScanRequest(
                formats = preferences.formats,
                source = sourceFor(preferences.preferredEngineId),
                continuous = preferences.continuous,
                allowMultiple = preferences.allowMultiple,
            )

            startScanSession(request, preferences.preferredEngineId).collect(::reduce)
        }
    }

    /**
     * La entrada manual no consume frames de cámara. Sin esto, el selector descartaría el motor
     * manual por no soportar la fuente pedida justo cuando es el único disponible.
     */
    private fun sourceFor(engineId: ScannerEngineId?): ScanSource =
        if (engineId == ScannerEngineId.ManualInput) ScanSource.ManualInput else ScanSource.LiveCamera

    private fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        _state.update { it.copy(sessionStatus = SessionStatus.Idle, activeEngineId = null) }
    }

    private suspend fun reduce(event: ScanEvent) {
        when (event) {
            is ScanEvent.SessionStarted -> _state.update {
                it.copy(sessionStatus = SessionStatus.Scanning, activeEngineId = event.engineId)
            }

            is ScanEvent.Detected -> {
                event.detections.forEach { saveDetection(it) }
                _state.update { it.copy(detections = event.detections + it.detections) }
            }

            is ScanEvent.EngineSwitched -> {
                _state.update { it.copy(switchedFrom = event.from, activeEngineId = event.to) }
                _effects.emit(
                    ScannerEffect.ShowMessage(
                        "Se cambió a otro motor porque el anterior no pudo continuar",
                    ),
                )
            }

            is ScanEvent.Failed -> if (event.error.isFatal) {
                _state.update { it.copy(error = event.error, sessionStatus = SessionStatus.Finished) }
            } else {
                _state.update { it.copy(error = event.error) }
            }

            is ScanEvent.SessionEnded -> _state.update {
                it.copy(sessionStatus = SessionStatus.Finished, activeEngineId = null)
            }

            is ScanEvent.FrameAnalyzed -> Unit
        }
    }

    private fun submitManualInput() {
        val value = _state.value.manualInput
        if (value.isBlank()) return

        viewModelScope.launch {
            val engine = engineRepository.engine(ScannerEngineId.ManualInput)
            if (engine is TextInputEngine) {
                engine.submit(value)
                _state.update { it.copy(manualInput = "") }
            } else {
                _effects.emit(ScannerEffect.ShowMessage("La entrada manual no está disponible"))
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }
}
