package com.whyscan.core.designsystem

import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * El idioma del sistema, capturado la primera vez que se pasa por aquí.
 *
 * Hay que guardarlo porque `Locale.setDefault` es global y destructivo: una vez cambiado, ya no hay
 * forma de preguntarle al proceso cuál era el original, y "seguir al sistema" dejaría de poder
 * volver a ninguna parte.
 */
private var systemDefault: Locale? = null

/**
 * En Android la cadena tiene un eslabón que no se ve: Compose lee
 * `androidx.compose.ui.text.intl.Locale.current`, que sale de `android.os.LocaleList.getDefault()`,
 * y **esa** se recalcula sola cuando `java.util.Locale.getDefault()` cambia —lo dice su contrato:
 * reordena la lista para dejar arriba el locale por defecto—. Por eso basta con `setDefault` y no
 * hace falta tocar la `Configuration` con `updateConfiguration`, que además está depreciada.
 */
@Composable
internal actual fun ApplyPlatformLanguage(tag: String?) {
    val original = systemDefault ?: Locale.getDefault().also { systemDefault = it }
    val target = tag?.let(Locale::forLanguageTag) ?: original

    // Comparar antes de escribir evita tocar un estado global en cada recomposición.
    if (Locale.getDefault() != target) Locale.setDefault(target)
}

actual val PlatformSupportsLanguageOverride: Boolean = true
