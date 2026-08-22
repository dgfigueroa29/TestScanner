package com.whyscan.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tema propio de WhyScan.
 *
 * Sustituye al `dynamicColorScheme` que traía la plantilla de Android: un tema que cambia con el
 * fondo de pantalla del usuario es incompatible con una app cuya UI se superpone a un preview de
 * cámara, donde el contraste tiene que estar garantizado (RNF-05). Esa decisión se mantiene ahora
 * que hay marca: el azul de WhyScan **es** parte del producto, no un acento negociable.
 */
// Los colores viven en `ScannerPalette`, que no depende de Compose. Así el contraste se mide con
// aritmética en `commonTest` (`ContrastTest`) en lugar de quedar como una intención del documento.
//
// Los roles se fijan **todos**, y no es cosmético. Primero pasó con los `on*`: al declarar solo
// `primary`, `secondary` y `tertiary`, los `on*` se quedaban en los valores por defecto de Material
// —un morado y un granate de una paleta que no es esta—, así que el texto de un botón primario en
// modo oscuro salía morado. Y volvía a pasar con los `*Container`, que es lo que pintan el
// `FilterChip` seleccionado, la `Card`, el `NavigationBar` y el indicador del ítem activo.
private val LightColors = lightColorScheme(
    primary = Color(ScannerPalette.Light.PRIMARY),
    onPrimary = Color(ScannerPalette.Light.ON_PRIMARY),
    primaryContainer = Color(ScannerPalette.Light.PRIMARY_CONTAINER),
    onPrimaryContainer = Color(ScannerPalette.Light.ON_PRIMARY_CONTAINER),
    secondary = Color(ScannerPalette.Light.SECONDARY),
    onSecondary = Color(ScannerPalette.Light.ON_SECONDARY),
    secondaryContainer = Color(ScannerPalette.Light.SECONDARY_CONTAINER),
    onSecondaryContainer = Color(ScannerPalette.Light.ON_SECONDARY_CONTAINER),
    tertiary = Color(ScannerPalette.Light.TERTIARY),
    onTertiary = Color(ScannerPalette.Light.ON_TERTIARY),
    tertiaryContainer = Color(ScannerPalette.Light.TERTIARY_CONTAINER),
    onTertiaryContainer = Color(ScannerPalette.Light.ON_TERTIARY_CONTAINER),
    error = Color(ScannerPalette.Light.ERROR),
    onError = Color(ScannerPalette.Light.ON_ERROR),
    errorContainer = Color(ScannerPalette.Light.ERROR_CONTAINER),
    onErrorContainer = Color(ScannerPalette.Light.ON_ERROR_CONTAINER),
    background = Color(ScannerPalette.Light.BACKGROUND),
    onBackground = Color(ScannerPalette.Light.ON_BACKGROUND),
    surface = Color(ScannerPalette.Light.SURFACE),
    onSurface = Color(ScannerPalette.Light.ON_SURFACE),
    surfaceVariant = Color(ScannerPalette.Light.SURFACE_VARIANT),
    onSurfaceVariant = Color(ScannerPalette.Light.ON_SURFACE_VARIANT),
    surfaceContainerLowest = Color(ScannerPalette.Light.SURFACE_CONTAINER_LOWEST),
    surfaceContainerLow = Color(ScannerPalette.Light.SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(ScannerPalette.Light.SURFACE_CONTAINER),
    surfaceContainerHigh = Color(ScannerPalette.Light.SURFACE_CONTAINER_HIGH),
    surfaceContainerHighest = Color(ScannerPalette.Light.SURFACE_CONTAINER_HIGHEST),
    outline = Color(ScannerPalette.Light.OUTLINE),
    outlineVariant = Color(ScannerPalette.Light.OUTLINE_VARIANT),
    scrim = Color(ScannerPalette.Light.SCRIM),
    inverseSurface = Color(ScannerPalette.Light.INVERSE_SURFACE),
    inverseOnSurface = Color(ScannerPalette.Light.INVERSE_ON_SURFACE),
    inversePrimary = Color(ScannerPalette.Light.INVERSE_PRIMARY),
    // El tinte tonal es el color con el que Material tiñe una superficie elevada. Dejarlo en el
    // morado de fábrica teñía de morado cualquier superficie con elevación.
    surfaceTint = Color(ScannerPalette.Light.PRIMARY),
)

private val DarkColors = darkColorScheme(
    primary = Color(ScannerPalette.Dark.PRIMARY),
    onPrimary = Color(ScannerPalette.Dark.ON_PRIMARY),
    primaryContainer = Color(ScannerPalette.Dark.PRIMARY_CONTAINER),
    onPrimaryContainer = Color(ScannerPalette.Dark.ON_PRIMARY_CONTAINER),
    secondary = Color(ScannerPalette.Dark.SECONDARY),
    onSecondary = Color(ScannerPalette.Dark.ON_SECONDARY),
    secondaryContainer = Color(ScannerPalette.Dark.SECONDARY_CONTAINER),
    onSecondaryContainer = Color(ScannerPalette.Dark.ON_SECONDARY_CONTAINER),
    tertiary = Color(ScannerPalette.Dark.TERTIARY),
    onTertiary = Color(ScannerPalette.Dark.ON_TERTIARY),
    tertiaryContainer = Color(ScannerPalette.Dark.TERTIARY_CONTAINER),
    onTertiaryContainer = Color(ScannerPalette.Dark.ON_TERTIARY_CONTAINER),
    error = Color(ScannerPalette.Dark.ERROR),
    onError = Color(ScannerPalette.Dark.ON_ERROR),
    errorContainer = Color(ScannerPalette.Dark.ERROR_CONTAINER),
    onErrorContainer = Color(ScannerPalette.Dark.ON_ERROR_CONTAINER),
    background = Color(ScannerPalette.Dark.BACKGROUND),
    onBackground = Color(ScannerPalette.Dark.ON_BACKGROUND),
    surface = Color(ScannerPalette.Dark.SURFACE),
    onSurface = Color(ScannerPalette.Dark.ON_SURFACE),
    surfaceVariant = Color(ScannerPalette.Dark.SURFACE_VARIANT),
    onSurfaceVariant = Color(ScannerPalette.Dark.ON_SURFACE_VARIANT),
    surfaceContainerLowest = Color(ScannerPalette.Dark.SURFACE_CONTAINER_LOWEST),
    surfaceContainerLow = Color(ScannerPalette.Dark.SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(ScannerPalette.Dark.SURFACE_CONTAINER),
    surfaceContainerHigh = Color(ScannerPalette.Dark.SURFACE_CONTAINER_HIGH),
    surfaceContainerHighest = Color(ScannerPalette.Dark.SURFACE_CONTAINER_HIGHEST),
    outline = Color(ScannerPalette.Dark.OUTLINE),
    outlineVariant = Color(ScannerPalette.Dark.OUTLINE_VARIANT),
    scrim = Color(ScannerPalette.Dark.SCRIM),
    inverseSurface = Color(ScannerPalette.Dark.INVERSE_SURFACE),
    inverseOnSurface = Color(ScannerPalette.Dark.INVERSE_ON_SURFACE),
    inversePrimary = Color(ScannerPalette.Dark.INVERSE_PRIMARY),
    surfaceTint = Color(ScannerPalette.Dark.PRIMARY),
)

/** Espaciados del sistema de diseño. Evita `dp` sueltos repartidos por las pantallas. */
object Spacing {
    /** Separación entre un icono y su etiqueta, o entre dos chips. */
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp

    /** El margen de contenido por defecto. */
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Huecos de estado vacío y separaciones entre bloques de una pantalla de ajustes. */
    val xxl = 48.dp
}

/**
 * Envuelve el contenido en el tema de WhyScan.
 *
 * Recibe un booleano y no un `ThemeMode`: resolver "sistema" contra lo que el sistema dice **ahora**
 * es cosa de quien tiene el estado de la app delante, y así este módulo no depende del dominio.
 * El valor por defecto sigue siendo el del sistema para que cualquier `@Preview` o punto de entrada
 * que no quiera saber de preferencias siga funcionando.
 */
@Composable
fun WhyScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WhyScanTypography,
        shapes = WhyScanShapes,
        content = content,
    )
}
