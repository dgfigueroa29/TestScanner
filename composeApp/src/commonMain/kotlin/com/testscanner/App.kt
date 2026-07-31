package com.testscanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.TestScannerTheme
import com.testscanner.feature.history.HistoryScreen
import com.testscanner.feature.scanner.ScannerScreen
import com.testscanner.feature.scanner.comparison.ComparisonScreen
import com.testscanner.navigation.Destination
import com.testscanner.navigation.Navigator
import org.koin.compose.KoinContext

/**
 * Raíz de la app, compartida por Android, iOS, Desktop y Web.
 *
 * No arranca Koin: eso lo hace `initKoin()` desde cada punto de entrada, porque Android necesita
 * entregar su `Context` antes de que exista cualquier composable. Aquí solo se consume el grafo ya
 * montado.
 *
 * El [Navigator] se recibe por parámetro para que Android pueda cederle el botón atrás del sistema
 * y para que la navegación sea testeable sin Compose (ADR-0005).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(navigator: Navigator = remember { Navigator() }) {
    KoinContext {
        TestScannerTheme {
            val backstack by navigator.backstack.collectAsStateWithLifecycle()
            val current = backstack.last()
            val snackbarHostState = remember { SnackbarHostState() }

            Scaffold(
                topBar = { TopAppBar(title = { Text(current.title()) }) },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar {
                        DESTINATIONS.forEach { destination ->
                            NavigationBarItem(
                                selected = current == destination,
                                onClick = { navigator.navigateTo(destination) },
                                icon = {},
                                label = { Text(destination.title()) },
                            )
                        }
                    }
                },
            ) { padding ->
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (current) {
                            Destination.Scanner -> ScannerScreen()
                            Destination.Comparison -> ComparisonScreen()
                            Destination.History -> HistoryScreen()
                        }
                    }
                }
            }
        }
    }
}

private val DESTINATIONS =
    listOf(Destination.Scanner, Destination.Comparison, Destination.History)

private fun Destination.title(): String = when (this) {
    Destination.Scanner -> "Escanear"
    Destination.Comparison -> "Comparar"
    Destination.History -> "Historial"
}
