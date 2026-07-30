package com.testscanner

import androidx.compose.runtime.Composable
import com.testscanner.core.designsystem.TestScannerTheme
import com.testscanner.di.appModules
import com.testscanner.feature.scanner.ScannerScreen
import org.koin.compose.KoinApplication

/**
 * Raíz de la app, compartida por Android, iOS, Desktop y Web.
 *
 * En la Fase 1 hay una sola pantalla, así que no hay navegación todavía (ver ADR-0005): el
 * navegador propio se introduce cuando el grafo tenga más de un destino real.
 */
@Composable
fun App() {
    KoinApplication(application = { modules(appModules()) }) {
        TestScannerTheme {
            ScannerScreen()
        }
    }
}
