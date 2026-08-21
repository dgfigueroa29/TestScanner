package com.testscanner.core.designsystem

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

/**
 * En iOS el idioma preferido de la app se expresa escribiendo `AppleLanguages` en los defaults.
 *
 * Se guarda el valor original por lo mismo que en las otras plataformas: sin él, volver a "seguir al
 * sistema" no tendría a dónde volver.
 */
private var systemDefault: List<*>? = null

@Composable
internal actual fun ApplyPlatformLanguage(tag: String?) {
    val defaults = NSUserDefaults.standardUserDefaults
    val original = systemDefault
        ?: (defaults.objectForKey(APPLE_LANGUAGES) as? List<*> ?: emptyList<String>())
            .also { systemDefault = it }

    defaults.setObject(if (tag == null) original else listOf(tag), forKey = APPLE_LANGUAGES)
}

private const val APPLE_LANGUAGES = "AppleLanguages"

actual val PlatformSupportsLanguageOverride: Boolean = true
