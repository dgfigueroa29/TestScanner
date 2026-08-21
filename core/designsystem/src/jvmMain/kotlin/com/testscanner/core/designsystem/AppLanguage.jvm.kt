package com.testscanner.core.designsystem

import androidx.compose.runtime.Composable
import java.util.Locale

/** Ver la nota en la implementación de Android: `Locale.setDefault` es global y destructivo. */
private var systemDefault: Locale? = null

@Composable
internal actual fun ApplyPlatformLanguage(tag: String?) {
    val original = systemDefault ?: Locale.getDefault().also { systemDefault = it }
    val target = tag?.let(Locale::forLanguageTag) ?: original

    if (Locale.getDefault() != target) Locale.setDefault(target)
}

actual val PlatformSupportsLanguageOverride: Boolean = true
