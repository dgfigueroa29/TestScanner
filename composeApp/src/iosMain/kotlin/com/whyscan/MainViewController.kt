package com.whyscan

import androidx.compose.ui.window.ComposeUIViewController
import com.whyscan.di.initKoin
import platform.UIKit.UIViewController

/**
 * Punto de entrada que consume el host SwiftUI de `iosApp/`.
 *
 * `initKoin()` se llama aquí y no desde Swift para que el grafo exista sí o sí antes del primer
 * composable; es idempotente, así que recrear el controlador no rearranca nada.
 */
@Suppress("FunctionNaming", "unused")
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
