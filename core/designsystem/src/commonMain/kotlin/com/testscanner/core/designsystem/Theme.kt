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
private val ScannerBlue = Color(0xFF2563EB)
private val ScannerBlueDark = Color(0xFF93B4FF)
private val ScannerTeal = Color(0xFF0F766E)
private val ScannerTealDark = Color(0xFF5EEAD4)
private val ScannerAmber = Color(0xFFB45309)
private val ScannerAmberDark = Color(0xFFFCD34D)

private val LightColors = lightColorScheme(
    primary = ScannerBlue,
    secondary = ScannerTeal,
    tertiary = ScannerAmber,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EBF0),
)

private val DarkColors = darkColorScheme(
    primary = ScannerBlueDark,
    secondary = ScannerTealDark,
    tertiary = ScannerAmberDark,
    background = Color(0xFF0B1020),
    surface = Color(0xFF141A2A),
    surfaceVariant = Color(0xFF232B3E),
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
