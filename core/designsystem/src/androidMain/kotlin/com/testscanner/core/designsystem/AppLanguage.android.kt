package com.testscanner.core.designsystem

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

@Composable
internal actual fun ApplyPlatformLanguage(tag: String?) {
    val original = systemDefault ?: Locale.getDefault().also { systemDefault = it }
    val target = tag?.let(Locale::forLanguageTag) ?: original

    // Comparar antes de escribir evita tocar un estado global en cada recomposición.
    if (Locale.getDefault() != target) Locale.setDefault(target)
}

/** Android sí: `Locale.setDefault` es lo que lee el entorno de recursos de Compose. */
actual val PlatformSupportsLanguageOverride: Boolean = true
