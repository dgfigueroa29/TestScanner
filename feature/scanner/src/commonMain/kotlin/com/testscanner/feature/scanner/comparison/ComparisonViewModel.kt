package com.testscanner.feature.scanner.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.domain.scan.EngineScoreboard
import com.testscanner.core.domain.usecase.ComparisonPlan
import com.testscanner.core.domain.usecase.StartComparisonUseCase
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ejecuta varios motores a la vez sobre la misma petición y va acumulando el marcador.
 *
 * Cierra el objetivo G5: responder con datos del dispositivo real a "¿qué motor funciona mejor para
 * este código?". La comparación siempre corre en modo continuo — con una sola detección no hay nada
 * que comparar.
 */
class ComparisonViewModel(
    private val startComparison: StartComparisonUseCase,
    private val preferencesRepository: ScanPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ComparisonState())
    val state: StateFlow<ComparisonState> = _state.asStateFlow()

    private var sessionJob: Job? = null

    init {
        viewModelScope.launch { refreshPlan() }
    }

    fun onAction(action: ComparisonAction) {
        when (action) {
            ComparisonAction.Start -> start()
            ComparisonAction.Stop -> stop()
            ComparisonAction.Reset -> reset()
        }
    }

    /**
     * La petición de comparación **no** exige escaneo continuo ni múltiples códigos, aunque sería
     * lo intuitivo.
     *
     * Exigirlos filtraría por capacidades y dejaría fuera precisamente al Google Code Scanner, que
     * es one-shot y a la vez el motor más interesante de contrastar. Basta con pedir la misma
     * fuente y los mismos formatos: cada motor aporta lo que sabe, el que termina antes deja de
     * emitir, y el marcador refleja esa diferencia — que es justamente el dato que se busca.
     */
    private suspend fun buildRequest(): ScanRequest =
        ScanRequest(formats = preferencesRepository.current().formats)

    private suspend fun refreshPlan() {
        when (val plan = startComparison.plan(buildRequest())) {
            is ComparisonPlan.Ready -> _state.update {
                it.copy(participants = plan.participants, notEnoughEngines = false)
            }

            is ComparisonPlan.NotEnoughEngines -> _state.update {
                it.copy(participants = plan.available, notEnoughEngines = true)
            }
        }
    }

    private fun start() {
        sessionJob?.cancel()
        _state.update { it.copy(isRunning = true, scoreboard = EngineScoreboard.Empty, error = null) }

        sessionJob = viewModelScope.launch {
            refreshPlan()
            if (_state.value.notEnoughEngines) {
                _state.update { it.copy(isRunning = false) }
                return@launch
            }
            startComparison(buildRequest()).collect(::reduce)
        }
    }

    private fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        _state.update { it.copy(isRunning = false) }
    }

    private fun reset() {
        stop()
        _state.update { it.copy(scoreboard = EngineScoreboard.Empty, error = null) }
    }

    private fun reduce(event: ScanEvent) {
        when (event) {
            is ScanEvent.Failed -> _state.update {
                it.copy(error = event.error, isRunning = if (event.error.isFatal) false else it.isRunning)
            }

            is ScanEvent.SessionEnded -> _state.update { it.copy(isRunning = false) }

            // Los eventos sin motor identificable no se atribuyen: en una comparación en paralelo
            // adivinar de quién vienen falsearía el marcador (ver EngineScoreboard).
            else -> _state.update { it.copy(scoreboard = it.scoreboard.reduce(event)) }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }
}
