package com.testscanner.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios de Scanly, algo más redondeados que los de fábrica de Material 3.
 *
 * No es capricho: la app pinta su UI **encima o al lado de un visor de cámara**, que es un
 * rectángulo con esquinas muy marcadas. Un radio generoso en tarjetas y hojas separa la interfaz de
 * la imagen sin necesitar bordes ni sombras, que sobre vídeo se ven sucios.
 *
 * Se exponen también sueltos —[Radius]— porque el visor y el overlay no son componentes de Material
 * y aun así tienen que usar los mismos valores. Esa era la razón por la que en `ScannerScreen`
 * aparecía un `RoundedCornerShape(Spacing.md)`: un radio tomado prestado de una escala de
 * espaciados, que es un `dp` suelto con disfraz.
 */
object Radius {
    /** Chips y campos pequeños. */
    val xs = 8.dp

    /** Botones y campos de texto. */
    val sm = 12.dp

    /** Tarjetas. El valor más usado de toda la app. */
    val md = 16.dp

    /** Visor de cámara, diálogos y contenedores grandes. */
    val lg = 22.dp

    /** Hojas inferiores y superficies que nacen del borde de la pantalla. */
    val xl = 28.dp

    /** Píldoras: `FilterChip` de formato, indicador de navegación. */
    val pill = 999.dp
}

/**
 * El fichero se llama `Radius.kt` y no `Shapes.kt` por la regla `MatchingDeclarationName` de detekt:
 * la única declaración de tipo aquí arriba es [Radius], y el nombre del fichero tiene que ser ese.
 *
 * Llamar `Shapes` al objeto para poder llamar `Shapes.kt` al fichero no era opción: chocaría con
 * `androidx.compose.material3.Shapes`, que se usa tres líneas más abajo.
 */
internal val ScanlyShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
