package com.whyscan.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.whyscan.core.domain.repository.AppLanguage
import com.whyscan.core.domain.repository.AppPreferences
import com.whyscan.core.domain.repository.AppPreferencesRepository
import com.whyscan.core.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Preferencias de app persistentes, con la misma forma que
 * [SettingsScanPreferencesRepository]: almacén síncrono detrás, proyección observable delante.
 *
 * A diferencia de las de escaneo, estas sí se declaran en el módulo común de datos: `Settings` está
 * en el grafo de las cuatro plataformas, así que no hay nada específico que aportar por plataforma.
 *
 * Los enums se guardan por su `id` estable y no por `name` ni por `ordinal`. No es manía: renombrar
 * una constante de Kotlin o reordenar el enum cambiaría en silencio el tema y el idioma de todo el
 * mundo que ya tuviera la app instalada.
 */
class SettingsAppPreferencesRepository(
    private val settings: Settings,
) : AppPreferencesRepository {

    private val state = MutableStateFlow(readFromDisk())

    override fun observePreferences(): Flow<AppPreferences> = state.asStateFlow()

    override suspend fun current(): AppPreferences = state.first()

    override suspend fun setThemeMode(mode: ThemeMode) {
        settings[KEY_THEME] = mode.id
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        settings[KEY_LANGUAGE] = language.id
        state.update { it.copy(language = language) }
    }

    override suspend fun setAdvancedMode(enabled: Boolean) {
        settings[KEY_ADVANCED] = enabled
        state.update { it.copy(advancedMode = enabled) }
    }

    private fun readFromDisk(): AppPreferences = AppPreferences(
        themeMode = ThemeMode.fromId(settings.getStringOrNull(KEY_THEME)),
        language = AppLanguage.fromId(settings.getStringOrNull(KEY_LANGUAGE)),
        advancedMode = settings.getBoolean(KEY_ADVANCED, false),
    )

    private companion object {
        const val KEY_THEME = "app.theme_mode"
        const val KEY_LANGUAGE = "app.language"
        const val KEY_ADVANCED = "app.advanced_mode"
    }
}
