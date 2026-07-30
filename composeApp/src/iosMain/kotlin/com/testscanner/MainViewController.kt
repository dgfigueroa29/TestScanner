package com.testscanner

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Punto de entrada que consume el host SwiftUI de `iosApp/`. */
@Suppress("FunctionNaming", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
