package com.whyscan.core.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Preferencias persistentes (salda la deuda D2).
 *
 * A diferencia del historial, esto sí funciona en las cuatro plataformas: `multiplatform-settings`
 * cubre SharedPreferences, NSUserDefaults, java.util.prefs y localStorage.
 *
 * El almacén es síncrono, así que la parte observable es un [MutableStateFlow] que se hidrata al
 * construirse y se escribe en cada cambio. No se usa la API de flujos de la librería — que sigue
 * siendo experimental — porque no hace falta: nadie más escribe en estas claves, así que la
 * proyección en memoria no puede quedar desincronizada.
 *
 * Los enums se guardan por su `id` estable y no por `name` ni ordinal, igual que en el historial:
 * renombrar una constante de Kotlin no debe cambiar en silencio los ajustes del usuario.
 */
class SettingsScanPreferencesRepository(
    private val settings: Settings,
) : ScanPreferencesRepository {

    private val state = MutableStateFlow(readFromDisk())

    override fun observePreferences(): Flow<ScanPreferences> = state.asStateFlow()

    override suspend fun current(): ScanPreferences = state.first()

    override suspend fun setPreferredEngine(id: ScannerEngineId?) {
        if (id == null) settings.remove(KEY_ENGINE) else settings[KEY_ENGINE] = id.id
        state.update { it.copy(preferredEngineId = id) }
    }

    override suspend fun setFormats(formats: Set<BarcodeFormat>) {
        settings[KEY_FORMATS] = formats.joinToString(SEPARATOR) { it.id }
        state.update { it.copy(formats = formats) }
    }

    override suspend fun setContinuous(enabled: Boolean) {
        settings[KEY_CONTINUOUS] = enabled
        state.update { it.copy(continuous = enabled) }
    }

    override suspend fun setAllowMultiple(enabled: Boolean) {
        settings[KEY_ALLOW_MULTIPLE] = enabled
        state.update { it.copy(allowMultiple = enabled) }
    }

    private fun readFromDisk(): ScanPreferences = ScanPreferences(
        // Un motor guardado que ya no existe en el catálogo se descarta en lugar de romper el
        // arranque: puede haberse eliminado entre dos versiones de la app.
        preferredEngineId = settings.getStringOrNull(KEY_ENGINE)?.let(ScannerEngineId::fromId),
        formats = readFormats(),
        continuous = settings.getBoolean(KEY_CONTINUOUS, false),
        allowMultiple = settings.getBoolean(KEY_ALLOW_MULTIPLE, false),
    )

    private fun readFormats(): Set<BarcodeFormat> {
        val stored = settings.getStringOrNull(KEY_FORMATS) ?: return BarcodeFormat.all
        val formats = stored.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapTo(mutableSetOf(), BarcodeFormat::fromId)
        // Un conjunto vacío haría inválido cualquier ScanRequest; se cae al conjunto completo.
        return formats.ifEmpty { BarcodeFormat.all }
    }

    private companion object {
        const val KEY_ENGINE = "scan.preferred_engine"
        const val KEY_FORMATS = "scan.formats"
        const val KEY_CONTINUOUS = "scan.continuous"
        const val KEY_ALLOW_MULTIPLE = "scan.allow_multiple"
        const val SEPARATOR = ","
    }
}
