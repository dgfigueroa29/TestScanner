package com.testscanner.core.designsystem

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

/**
 * En iOS el idioma preferido de la app se expresa escribiendo `AppleLanguages` en los defaults, que
 * es la clave que respalda `NSLocale.preferredLanguages`.
 *
 * Se guarda el valor original por lo mismo que en las otras plataformas: sin él, volver a "seguir al
 * sistema" no tendría a dónde volver.
 *
 * **Sin verificar.** Si Compose en iOS lee `preferredLanguages`, el cambio es inmediato como en
 * Android; si lee `NSLocale.currentLocale`, no lo será hasta reabrir la app. No se ha podido
 * comprobar: este proyecto compila iOS pero no lo ejecuta —no hay dispositivo— y el workflow de iOS
 * es manual y solo enlaza. Se deja el mecanismo estándar de la plataforma y esta nota; el día que
 * haya un iPhone delante, esto es lo primero que hay que mirar.
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
