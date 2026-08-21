package com.testscanner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.repository.AppLanguage
import com.testscanner.core.domain.repository.AppPreferencesRepository
import com.testscanner.core.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ajustes de la app: aspecto, idioma y modo avanzado.
 *
 * No tiene efectos de una sola vez —ni `SharedFlow` ni snackbars— y no es un olvido: aquí todo
 * cambio *es* su propio feedback, porque el tema y el idioma se ven en la propia pantalla en cuanto
 * se tocan. Un aviso de "guardado" sobre algo que ya cambió delante del usuario es ruido.
 *
 * [canChooseLanguage] llega por constructor y no se consulta aquí dentro para que el ViewModel se
 * pueda testear en `commonTest` con los dos valores, que es donde vive la única lógica que tiene.
 */
class SettingsViewModel(
    private val preferences: AppPreferencesRepository,
    canChooseLanguage: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(canChooseLanguage = canChooseLanguage))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.observePreferences().collect { current ->
                _state.update { it.copy(preferences = current, isLoading = false) }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetThemeMode -> setThemeMode(action.mode)
            is SettingsAction.SetLanguage -> setLanguage(action.language)
            is SettingsAction.SetAdvancedMode -> setAdvancedMode(action.enabled)
        }
    }

    private fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /**
     * Cambiar el idioma en una plataforma que no puede honrarlo dejaría una preferencia guardada que
     * la app ignora: al reinstalar en otro dispositivo aparecería un idioma que el usuario no eligió
     * ahí. Se ignora el intento en lugar de persistir una mentira.
     */
    private fun setLanguage(language: AppLanguage) {
        if (!_state.value.canChooseLanguage) return
        viewModelScope.launch { preferences.setLanguage(language) }
    }

    private fun setAdvancedMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setAdvancedMode(enabled) }
    }
}
