package com.testscanner.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.testscanner.core.model.Detection
import com.testscanner.core.model.Point

/**
 * Marco de encuadre y contorno de las detecciones, dibujado **en Compose común** sobre el preview
 * nativo de cada plataforma.
 *
 * Es la contrapartida de `CameraPreviewEngine`: solo la superficie de vídeo es nativa; todo lo que
 * el usuario percibe como "el diseño" se pinta aquí una sola vez para Android, iOS, Desktop y Web.
 *
 * Los puntos llegan normalizados a [0, 1] sobre el frame analizado (ver
 * [com.testscanner.core.model.Point]), así que el mapeo a píxeles de pantalla es una simple regla
 * de tres con el tamaño del Canvas — sin depender de la resolución de análisis del motor.
 */
@Composable
fun ScanOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier,
    reticleColor: Color = Color.White.copy(alpha = RETICLE_ALPHA),
    detectionColor: Color = Color(0xFF34D399),
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawReticle(reticleColor)
        detections
            .mapNotNull { it.barcode.cornerPoints }
            .filter { it.size >= MIN_CORNERS }
            .forEach { corners -> drawDetection(corners, detectionColor) }
    }
}

/** Esquinas del área de encuadre. Orienta al usuario sin tapar el centro de la imagen. */
private fun DrawScope.drawReticle(color: Color) {
    val frame = Size(size.width * RETICLE_FRACTION, size.width * RETICLE_FRACTION)
        .let { Size(it.width, minOf(it.height, size.height * RETICLE_FRACTION)) }
    val topLeft = Offset((size.width - frame.width) / 2f, (size.height - frame.height) / 2f)
    val corner = minOf(frame.width, frame.height) * CORNER_FRACTION
    val stroke = Stroke(width = STROKE_DP.dp.toPx())

    listOf(
        // Cada esquina se dibuja como dos segmentos en L, no como un rectángulo completo: el marco
        // cerrado compite visualmente con el propio código.
        Offset(topLeft.x, topLeft.y) to Pair(Offset(corner, 0f), Offset(0f, corner)),
        Offset(topLeft.x + frame.width, topLeft.y) to Pair(Offset(-corner, 0f), Offset(0f, corner)),
        Offset(topLeft.x, topLeft.y + frame.height) to Pair(Offset(corner, 0f), Offset(0f, -corner)),
        Offset(topLeft.x + frame.width, topLeft.y + frame.height) to
            Pair(Offset(-corner, 0f), Offset(0f, -corner)),
    ).forEach { (origin, arms) ->
        val (horizontal, vertical) = arms
        drawLine(color, origin, origin + horizontal, stroke.width)
        drawLine(color, origin, origin + vertical, stroke.width)
    }
}

private fun DrawScope.drawDetection(corners: List<Point>, color: Color) {
    val path = Path().apply {
        corners.forEachIndexed { index, point ->
            val x = point.x * size.width
            val y = point.y * size.height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = STROKE_DP.dp.toPx()))
}

private const val RETICLE_FRACTION = 0.72f
private const val CORNER_FRACTION = 0.18f
private const val RETICLE_ALPHA = 0.75f
private const val STROKE_DP = 3
private const val MIN_CORNERS = 3
