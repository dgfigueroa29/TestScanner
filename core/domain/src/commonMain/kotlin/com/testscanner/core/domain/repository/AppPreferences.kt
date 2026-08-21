package com.testscanner.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Preferencias de **la app**, no del escaneo.
 *
 * Están separadas de [ScanPreferences] a propósito: aquello son ajustes de una sesión de lectura
 * —qué motor, qué formatos, continuo o no— y esto es cómo se ve y en qué idioma habla el producto.
 * Mezclarlas obligaría a la pantalla de Ajustes a depender del catálogo de motores y a la de escaneo
 * a saber de temas.
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    /**
     * Modo avanzado: devuelve a la navegación el catálogo de motores, el comparador y las métricas.
     *
     * Está apagado por defecto porque el producto que se publica es un lector de códigos, y un
     * usuario que abre la app por primera vez no tiene por qué encontrarse ocho motores con sus
     * latencias. Lo que había antes no era otra app: era esta con el diagnóstico en la portada.
     */
    val advancedMode: Boolean = false,
)

/**
 * Cómo elige el usuario el aspecto claro/oscuro.
 *
 * [System] no es "claro por defecto": es delegar en el sistema y **seguir sus cambios**, incluido el
 * modo oscuro automático por horario. Por eso resolver el modo necesita saber qué dice el sistema
 * *ahora* y no puede ser una propiedad constante del enum.
 */
enum class ThemeMode(val id: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** Resuelve a un booleano, que es lo único que necesita el tema de Compose. */
    fun isDark(systemInDarkMode: Boolean): Boolean = when (this) {
        System -> systemInDarkMode
        Light -> false
        Dark -> true
    }

    companion object {
        /** Un id que no se reconoce vuelve al valor por defecto en lugar de romper el arranque. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Idioma de la interfaz.
 *
 * [tag] es una etiqueta BCP-47 y `null` significa "el del sistema". Se guarda el idioma elegido y no
 * el resuelto: si el usuario deja [System] y se muda de país, la app le sigue.
 *
 * Los dos idiomas soportados no son arbitrarios: los textos existen en `values/` (inglés, que es
 * además el respaldo de cualquier otro idioma) y en `values-es/`.
 */
enum class AppLanguage(val id: String, val tag: String?) {
    System("system", null),
    English("en", "en"),
    Spanish("es", "es"),
    ;

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: System
    }
}

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun current(): AppPreferences
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setAdvancedMode(enabled: Boolean)
}
