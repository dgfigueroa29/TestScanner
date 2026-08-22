package com.whyscan.engines.ocr

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Superficie de vídeo del motor de OCR.
 *
 * Igual que en el motor de códigos: el `LifecycleOwner` lo toma el composable del árbol y lo suelta
 * al salir de composición, de modo que el motor nunca retiene la Activity.
 */
@Composable
internal fun MlKitOcrEngine.RenderOcrPreview(modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
    )
}
