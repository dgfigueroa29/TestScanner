package com.whyscan.engines.zxing

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
 * Superficie de vídeo del motor de zxing-cpp en iOS.
 *
 * El preview se alimenta de la misma `AVCaptureSession` que produce los frames que se decodifican,
 * pero por un camino distinto: la capa de Core Animation los pinta y `AVCaptureVideoDataOutput` los
 * entrega al motor. Son dos consumidores de la misma sesión, no uno alimentando al otro.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun RenderZXingPreview(holder: ZXingCameraSessionHolder, modifier: Modifier) {
    val view = remember { ZXingPreviewUIView() }

    DisposableEffect(holder) {
        val listener: (AVCaptureSession?) -> Unit = { session -> view.bind(session) }
        holder.addListener(listener)
        onDispose {
            holder.removeListener(listener)
            view.bind(null)
        }
    }

    UIKitView(factory = { view }, modifier = modifier)
}

/**
 * `UIView` cuya capa de preview sigue el tamaño de la vista: un `CALayer` no se redimensiona solo.
 */
@OptIn(ExperimentalForeignApi::class)
private class ZXingPreviewUIView : UIView(frame = CGRectZero.readValue()) {

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
        // Sin desactivar las acciones implícitas, cada cambio de tamaño se anima y el preview
        // "salta" al rotar.
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer.setFrame(currentBounds())
        CATransaction.commit()
    }

    private fun currentBounds(): CValue<CGRect> = bounds
}
