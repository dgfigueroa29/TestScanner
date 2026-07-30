package com.testscanner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.testscanner.App

/**
 * Shell de Android: no contiene lógica ni UI propia.
 *
 * Todo lo que se ve vive en `:composeApp`, compartido con iOS, Desktop y Web. Si algún día esta
 * clase crece, es señal de que algo específico de Android se coló donde no debía.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
