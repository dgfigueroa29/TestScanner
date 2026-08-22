package com.testscanner.feature.settings

import com.testscanner.core.domain.repository.AppLanguage
import com.testscanner.core.domain.repository.AppPreferences
import com.testscanner.core.domain.repository.ThemeMode

/**
 * Estado de la pantalla de Ajustes.
 *
 * Es un envoltorio fino sobre [AppPreferences] y no una copia de sus campos: duplicarlos obligaría a
 * añadir cada preferencia nueva en dos sitios, y el día que uno se olvidara la pantalla mostraría un
 * valor viejo sin que nada fallara.
 */
data class SettingsState(
    val preferences: AppPreferences = AppPreferences(),
    /**
     * Si esta plataforma puede honrar un idioma distinto al del sistema.
     *
     * En el navegador no puede, así que el selector no se dibuja. Preferimos no ofrecer el control
     * a ofrecerlo roto.
     */
    val canChooseLanguage: Boolean = true,
    val isLoading: Boolean = true,
)

sealed interface SettingsAction {
    data class SetThemeMode(val mode: ThemeMode) : SettingsAction
    data class SetLanguage(val language: AppLanguage) : SettingsAction
    data class SetAdvancedMode(val enabled: Boolean) : SettingsAction
}
