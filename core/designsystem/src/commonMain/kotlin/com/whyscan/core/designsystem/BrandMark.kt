package com.whyscan.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * La marca de WhyScan: cuatro esquinas de encuadre y la línea de lectura.
 *
 * Se dibuja en código y no se empaqueta como imagen por dos motivos. Uno, es la **misma** forma que
 * el icono de lanzador de Android (`ic_launcher_foreground.xml`), y tenerla en un solo sitio evita
 * que la app y su icono se separen con el tiempo. Dos, un `ImageVector` se tiñe con el color del
 * tema, así que la marca funciona igual en claro y en oscuro sin necesitar dos archivos.
 *
 * El encuadre no es un adorno: es lo que el usuario ve al apuntar con la cámara. Que el icono de la
 * app sea el gesto de la app es lo que hace que una marca se reconozca antes de leer el nombre.
 */
// Las coordenadas de un trazado **son** la definición de la forma: darles nombre no aclararía nada
// —¿`ESQUINA_SUPERIOR_IZQUIERDA_X_INICIAL`?— y rompería la correspondencia línea a línea con
// `ic_launcher_foreground.xml`, que es lo que hace comprobable que las dos dibujan lo mismo.
@Suppress("MagicNumber")
val WhyScanMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "WhyScanMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply {
        // Las cuatro esquinas van en un solo trazo con cuatro subtrayectos: comparten grosor y
        // remates, y separarlas solo multiplicaría por cuatro los sitios donde ajustarlos.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Superior izquierda
            moveTo(4f, 9f)
            lineTo(4f, 4f)
            lineTo(9f, 4f)
            // Superior derecha
            moveTo(15f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 9f)
            // Inferior derecha
            moveTo(20f, 15f)
            lineTo(20f, 20f)
            lineTo(15f, 20f)
            // Inferior izquierda
            moveTo(9f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 15f)
        }

        // La línea de lectura, aparte porque es más gruesa: es el elemento que da la sensación de
        // que algo está ocurriendo, y a igual grosor que el encuadre se perdía dentro de él.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = SCAN_LINE_STROKE,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(7.5f, 12f)
            lineTo(16.5f, 12f)
        }
    }.build()
}

private const val VIEWPORT = 24f
private const val STROKE = 2f
private const val SCAN_LINE_STROKE = 2.4f
