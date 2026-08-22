package com.whyscan.engines.vision

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * Superficie de vídeo del motor de iOS.
 *
 * Se conecta a la sesión a través de [CameraSessionHolder] en lugar de recibirla por parámetro,
 * porque el composable puede entrar en composición antes de que el motor arranque la sesión — y
 * también sobrevivir a que se detenga y vuelva a arrancar al cambiar de motor.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun RenderCameraPreview(holder: CameraSessionHolder, modifier: Modifier) {
    val view = remember { PreviewUIView() }

    DisposableEffect(holder) {
        val listener: (AVCaptureSession?) -> Unit = { session -> view.bind(session) }
        holder.addListener(listener)
        onDispose {
            holder.removeListener(listener)
            view.bind(null)
        }
    }

    UIKitView(
        factory = { view },
        modifier = modifier,
    )
}

/**
 * `UIView` cuyo `AVCaptureVideoPreviewLayer` sigue el tamaño de la vista.
 *
 * Hace falta una subclase porque un `CALayer` no se redimensiona con su vista: si no se ajusta el
 * `frame` en `layoutSubviews`, el vídeo se queda del tamaño que tuviera al crearse y no acompaña
 * rotaciones ni cambios de layout.
 */
@OptIn(ExperimentalForeignApi::class)
private class PreviewUIView : UIView(frame = CGRectZero.readValue()) {

    private val previewLayer = AVCaptureVideoPreviewLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    fun bind(session: AVCaptureSession?) {
        previewLayer.session = session
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Sin desactivar las acciones implícitas, cada cambio de tamaño se anima con el fade por
        // defecto de Core Animation y el preview "salta" al rotar.
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer.setFrame(currentBounds())
        CATransaction.commit()
    }

    private fun currentBounds(): CValue<CGRect> = bounds
}
