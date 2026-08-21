package com.testscanner

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.ProvideAppLanguage
import com.testscanner.core.designsystem.ScanlyTheme
import com.testscanner.core.domain.repository.AppPreferences
import com.testscanner.core.domain.repository.AppPreferencesRepository
import com.testscanner.feature.history.HistoryScreen
import com.testscanner.feature.scanner.ScannerScreen
import com.testscanner.feature.scanner.comparison.ComparisonScreen
import com.testscanner.feature.settings.SettingsScreen
import com.testscanner.navigation.Destination
import com.testscanner.navigation.Navigator
import com.testscanner.resources.Res
import com.testscanner.resources.destination_comparison
import com.testscanner.resources.destination_history
import com.testscanner.resources.destination_scanner
import com.testscanner.resources.destination_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

/**
 * Raíz de la app, compartida por Android, iOS, Desktop y Web.
 *
 * No arranca Koin: eso lo hace `initKoin()` desde cada punto de entrada, porque Android necesita
 * entregar su `Context` antes de que exista cualquier composable. Aquí solo se consume el grafo ya
 * montado.
 *
 * El [Navigator] se recibe por parámetro para que Android pueda cederle el botón atrás del sistema
 * y para que la navegación sea testeable sin Compose (ADR-0005).
 *
 * @param onDarkThemeResolved lo llama la app cada vez que cambia el claro/oscuro **ya resuelto**.
 *   Existe porque las barras del sistema no las pinta Compose: en Android, con `enableEdgeToEdge`,
 *   los iconos de la barra de estado siguen al tema del *sistema*, así que un usuario con el
 *   teléfono en claro y la app forzada a oscuro se quedaba con iconos oscuros sobre fondo oscuro.
 *   Las plataformas que no tienen barras que ajustar no pasan nada y no se enteran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    navigator: Navigator = remember { Navigator() },
    onDarkThemeResolved: (Boolean) -> Unit = {},
) {
    KoinContext {
        val preferencesRepository = koinInject<AppPreferencesRepository>()
        val preferences by preferencesRepository.observePreferences()
            .collectAsStateWithLifecycle(AppPreferences())

        val darkTheme = preferences.themeMode.isDark(isSystemInDarkTheme())
        LaunchedEffect(darkTheme) { onDarkThemeResolved(darkTheme) }

        // El idioma envuelve al tema y no al revés: cambiar de idioma recompone el subárbol entero
        // (ver `ProvideAppLanguage`), y no hay motivo para volver a construir el `ColorScheme` por
        // eso. Al revés sí lo habría.
        ProvideAppLanguage(preferences.language.tag) {
            ScanlyTheme(darkTheme = darkTheme) {
                AppScaffold(navigator = navigator, advancedMode = preferences.advancedMode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(navigator: Navigator, advancedMode: Boolean) {
    val backstack by navigator.backstack.collectAsStateWithLifecycle()
    val destinations = destinationsFor(advancedMode)
    val current = backstack.last()
    val snackbarHostState = remember { SnackbarHostState() }

    // Apagar el modo avanzado con el comparador en pantalla dejaba al usuario en un destino que ya
    // no aparece en ninguna barra. Se poda el backstack entero y no solo la pantalla actual: si el
    // comparador quedara enterrado más abajo, el botón atrás acabaría volviendo a él.
    LaunchedEffect(destinations) { navigator.pruneTo(destinations) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title()) },
                // El contenedor de la barra iguala al del `NavigationBar` de abajo: con el color
                // por defecto, la superior salía del tono de `surface` y la inferior de
                // `surfaceContainer`, y la pantalla quedaba enmarcada por dos grises distintos.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = { navigator.navigateTo(destination) },
                        // El icono estaba vacío (`icon = {}`), que es lo que dejaba la barra como
                        // una fila de etiquetas sueltas sin el indicador de píldora de Material 3:
                        // ese indicador se dibuja **alrededor del icono**, así que sin icono no
                        // había nada que resaltara el destino activo.
                        icon = {
                            Icon(
                                imageVector = destination.icon(),
                                // La etiqueta va justo debajo y dice lo mismo. Describir también el
                                // icono haría que el lector de pantalla leyera cada ítem dos veces.
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.title()) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (current) {
                    Destination.Scanner -> ScannerScreen(advancedMode = advancedMode)
                    Destination.Comparison -> ComparisonScreen()
                    Destination.History -> HistoryScreen(advancedMode = advancedMode)
                    Destination.Settings -> SettingsScreen()
                }
            }
        }
    }
}

/**
 * Qué destinos se ofrecen.
 *
 * Comparar motores solo aparece en modo avanzado: es la pantalla más específica de todas —un banco
 * de pruebas dentro del producto— y ocupaba un cuarto de la barra de navegación para alguien que
 * abre la app a leer un QR.
 */
private fun destinationsFor(advancedMode: Boolean): List<Destination> =
    Destination.all.filter { it != Destination.Comparison || advancedMode }

@Composable
private fun Destination.title(): String = when (this) {
    Destination.Scanner -> stringResource(Res.string.destination_scanner)
    Destination.Comparison -> stringResource(Res.string.destination_comparison)
    Destination.History -> stringResource(Res.string.destination_history)
    Destination.Settings -> stringResource(Res.string.destination_settings)
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.Scanner -> Icons.Filled.QrCodeScanner
    Destination.Comparison -> Icons.Filled.Speed
    Destination.History -> Icons.Filled.History
    Destination.Settings -> Icons.Filled.Settings
}
