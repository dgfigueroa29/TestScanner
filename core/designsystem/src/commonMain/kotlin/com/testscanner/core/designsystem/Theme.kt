package com.testscanner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tema propio de TestScanner.
 *
 * Sustituye al `dynamicColorScheme` que traía la plantilla de Android: un tema que cambia con el
 * fondo de pantalla del usuario es incompatible con una app cuya UI se superpone a un preview de
 * cámara, donde el contraste tiene que estar garantizado (RNF-05).
 */
// Los colores viven en `ScannerPalette`, que no depende de Compose. Así el contraste se mide con
// aritmética en `commonTest` (`ContrastTest`) en lugar de quedar como una intención del documento.
//
// Los roles `on*` se fijan **todos**, y no es cosmético: al declarar solo `primary`, `secondary` y
// `tertiary`, los `on*` se quedaban en los valores por defecto de Material —un morado y un granate
// de una paleta que no es esta—, así que el texto de un botón primario en modo oscuro salía morado.
private val LightColors = lightColorScheme(
    primary = Color(ScannerPalette.Light.PRIMARY),
    onPrimary = Color(ScannerPalette.Light.ON_PRIMARY),
    secondary = Color(ScannerPalette.Light.SECONDARY),
    onSecondary = Color(ScannerPalette.Light.ON_SECONDARY),
    tertiary = Color(ScannerPalette.Light.TERTIARY),
    onTertiary = Color(ScannerPalette.Light.ON_TERTIARY),
    background = Color(ScannerPalette.Light.BACKGROUND),
    onBackground = Color(ScannerPalette.Light.ON_BACKGROUND),
    surface = Color(ScannerPalette.Light.SURFACE),
    onSurface = Color(ScannerPalette.Light.ON_SURFACE),
    surfaceVariant = Color(ScannerPalette.Light.SURFACE_VARIANT),
    onSurfaceVariant = Color(ScannerPalette.Light.ON_SURFACE_VARIANT),
    error = Color(ScannerPalette.Light.ERROR),
    onError = Color(ScannerPalette.Light.ON_ERROR),
)

private val DarkColors = darkColorScheme(
    primary = Color(ScannerPalette.Dark.PRIMARY),
    onPrimary = Color(ScannerPalette.Dark.ON_PRIMARY),
    secondary = Color(ScannerPalette.Dark.SECONDARY),
    onSecondary = Color(ScannerPalette.Dark.ON_SECONDARY),
    tertiary = Color(ScannerPalette.Dark.TERTIARY),
    onTertiary = Color(ScannerPalette.Dark.ON_TERTIARY),
    background = Color(ScannerPalette.Dark.BACKGROUND),
    onBackground = Color(ScannerPalette.Dark.ON_BACKGROUND),
    surface = Color(ScannerPalette.Dark.SURFACE),
    onSurface = Color(ScannerPalette.Dark.ON_SURFACE),
    surfaceVariant = Color(ScannerPalette.Dark.SURFACE_VARIANT),
    onSurfaceVariant = Color(ScannerPalette.Dark.ON_SURFACE_VARIANT),
    error = Color(ScannerPalette.Dark.ERROR),
    onError = Color(ScannerPalette.Dark.ON_ERROR),
)

/** Espaciados del sistema de diseño. Evita `dp` sueltos repartidos por las pantallas. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
fun TestScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
