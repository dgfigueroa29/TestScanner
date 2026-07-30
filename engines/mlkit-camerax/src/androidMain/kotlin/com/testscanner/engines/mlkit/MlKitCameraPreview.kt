package com.testscanner.engines.mlkit

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Superficie de vídeo del motor de CameraX.
 *
 * Es aquí — y no en el motor — donde entra el `LifecycleOwner`: el composable lo toma del árbol,
 * enlaza el controlador mientras está en composición y lo suelta al salir. El motor nunca guarda
 * una referencia a la Activity, así que rotar la pantalla no filtra nada.
 */
@Composable
internal fun MlKitCameraXEngine.RenderCameraPreview(modifier: Modifier) {
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
