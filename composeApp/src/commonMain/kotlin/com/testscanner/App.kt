package com.testscanner

import androidx.compose.runtime.Composable
import com.testscanner.core.designsystem.TestScannerTheme
import com.testscanner.feature.scanner.ScannerScreen
import org.koin.compose.KoinContext

/**
 * Raíz de la app, compartida por Android, iOS, Desktop y Web.
 *
 * No arranca Koin: eso lo hace `initKoin()` desde cada punto de entrada, porque Android necesita
 * entregar su `Context` antes de que exista cualquier composable. Aquí solo se consume el grafo ya
 * montado.
 *
 * En la Fase 1 hay una sola pantalla, así que no hay navegación todavía (ver ADR-0005): el
 * navegador propio se introduce cuando el grafo tenga más de un destino real.
 */
@Composable
fun App() {
    KoinContext {
        TestScannerTheme {
            ScannerScreen()
        }
    }
}
