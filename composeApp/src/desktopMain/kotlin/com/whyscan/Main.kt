package com.whyscan

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.whyscan.di.initKoin

fun main() {
    initKoin()
    application {
        Window(onCloseRequest = ::exitApplication, title = "WhyScan") {
            App()
        }
    }
}
