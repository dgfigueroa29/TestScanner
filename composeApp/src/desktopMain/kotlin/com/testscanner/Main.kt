package com.testscanner

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.testscanner.di.initKoin

fun main() {
    initKoin()
    application {
        Window(onCloseRequest = ::exitApplication, title = "Scanly") {
            App()
        }
    }
}
