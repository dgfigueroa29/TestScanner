package com.testscanner.platform

import com.testscanner.core.model.ScanImage
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PickImageResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * Selector de imágenes de iOS (RF-07).
 *
 * Usa `UIImagePickerController` y no `PHPickerViewController` por una razón concreta: `PHPicker`
 * entrega `NSItemProvider`s que hay que cargar de forma asíncrona y cuyo tipo hay que negociar,
 * mientras que aquí el `UIImage` llega ya resuelto en el callback. Para elegir **una** foto y
 * decodificarla, ese segundo paso no aporta nada.
 *
 * Igual que en Android, no se pide permiso: desde iOS 11 el selector corre fuera del proceso de la
 * app y solo devuelve lo elegido, así que no hace falta `NSPhotoLibraryUsageDescription`.
 */
class IosImagePicker : ImagePicker {

    /**
     * Mantiene vivo el delegado mientras el selector está abierto.
     *
     * `UIImagePickerController.delegate` es una referencia **débil** en UIKit: sin guardarlo aquí,
     * el recolector de Kotlin/Native se llevaría el delegado antes de que el usuario elija nada y
     * el callback no llegaría nunca.
     */
    private var retainedDelegate: PickerDelegate? = null

    override suspend fun pickImage(): PickImageResult {
        val presenter = rootViewController()
            ?: return PickImageResult.Failed("No hay ninguna pantalla activa para abrir el selector")

        val image = suspendCancellableCoroutine { continuation ->
            val controller = UIImagePickerController().apply {
                sourceType =
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }

            val delegate = PickerDelegate { picked ->
                controller.dismissViewControllerAnimated(flag = true, completion = null)
                continuation.resume(picked)
            }
            retainedDelegate = delegate
            controller.delegate = delegate

            continuation.invokeOnCancellation {
                controller.dismissViewControllerAnimated(flag = true, completion = null)
            }

            presenter.presentViewController(controller, animated = true, completion = null)
        }

        retainedDelegate = null

        return image?.let { toScanImage(it) } ?: PickImageResult.Cancelled
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun toScanImage(image: UIImage): PickImageResult {
        // PNG y no JPEG: el escaneo depende de los bordes entre barras, y la compresión con pérdida
        // los emborrona justo donde el decodificador mira.
        val data = UIImagePNGRepresentation(image)
            ?: return PickImageResult.Failed("No se pudo convertir la imagen")

        val (width, height) = image.size.useContents { width to height }

        return PickImageResult.Picked(
            ScanImage(
                encoded = data.toByteArray(),
                mimeType = "image/png",
                widthPx = width.toInt(),
                heightPx = height.toInt(),
            ),
        )
    }

    private fun rootViewController(): UIViewController? = UIApplication.sharedApplication
        .connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull()
        ?.rootViewController
}

/** Traduce los dos callbacks de UIKit a un único resultado: la imagen, o `null` si se canceló. */
private class PickerDelegate(
    private val onResult: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        onResult(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        onResult(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val source = this
    val size = source.length.toInt()
    if (size == 0) return ByteArray(0)

    return ByteArray(size).apply {
        usePinned { destination -> memcpy(destination.addressOf(0), source.bytes, source.length) }
    }
}
