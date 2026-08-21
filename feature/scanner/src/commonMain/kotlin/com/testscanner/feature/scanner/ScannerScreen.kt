package com.testscanner.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testscanner.core.designsystem.LocalSnackbarHostState
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.detections_title
import com.testscanner.feature.scanner.resources.engines_title
import com.testscanner.feature.scanner.resources.message_camera_permission_denied
import com.testscanner.feature.scanner.resources.message_copied
import com.testscanner.feature.scanner.resources.message_copy_failed
import com.testscanner.feature.scanner.resources.message_engine_switched
import com.testscanner.feature.scanner.resources.message_manual_input_unavailable
import com.testscanner.feature.scanner.resources.message_no_code_in_image
import com.testscanner.feature.scanner.resources.message_open_failed
import com.testscanner.feature.scanner.resources.message_share_failed
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla con estado. Solo obtiene el ViewModel y resuelve el preview del motor activo; toda la UI
 * real vive en [ScannerContent], que es stateless y por tanto previsualizable y testeable sin DI.
 */
@Composable
fun ScannerScreen(
    advancedMode: Boolean = false,
    viewModel: ScannerViewModel = koinViewModel(),
    previewResolver: EnginePreviewResolver = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // `DisposableEffect` y no `LaunchedEffect`: hace falta el `onDispose`.
    //
    // Al entrar se refresca el catálogo —la disponibilidad cambia mientras la pantalla no está: el
    // usuario concede el permiso desde los ajustes, ML Kit termina de descargar su modelo, otra app
    // suelta la cámara— y la sesión arranca sola.
    //
    // Al salir se apaga. El ViewModel sobrevive a la navegación, así que sin esto la cámara seguía
    // capturando mientras el usuario mira el historial. En una app de escaneo eso no es solo
    // batería.
    DisposableEffect(viewModel) {
        viewModel.onAction(ScannerAction.ScreenShown)
        onDispose { viewModel.onAction(ScannerAction.ScreenHidden) }
    }

    // Sin esto los mensajes del ViewModel se emitían a un SharedFlow que nadie escuchaba, incluido
    // el aviso de degradación de motor, que es la señal visible del objetivo G4.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(resolve(effect.message))
            }
        }
    }

    ScannerContent(
        state = state,
        onAction = viewModel::onAction,
        advancedMode = advancedMode,
        previewEngine = previewResolver.previewFor(state.activeEngineId),
    )
}

/**
 * @param advancedMode elige entre las dos disposiciones, que no son la misma pantalla con cosas
 *   ocultas: son dos jerarquías distintas para dos intenciones distintas. Leer un código quiere el
 *   visor ocupando todo y el resultado a mano; comparar motores quiere la lista de motores visible y
 *   el visor como una pieza más. Fingir que es un solo layout con `if`s repartidos daba una pantalla
 *   que no servía bien para ninguna de las dos cosas.
 */
@Composable
fun ScannerContent(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
    advancedMode: Boolean = false,
    previewEngine: CameraPreviewEngine? = null,
) {
    if (advancedMode) {
        WorkbenchLayout(state, onAction, previewEngine, modifier)
    } else {
        ScannerLayout(state, onAction, previewEngine, modifier)
    }
}

/**
 * La disposición del producto: cámara arriba ocupando todo lo que queda, resultado abajo.
 *
 * El visor **no** está dentro de una lista desplazable, y esa es la diferencia de fondo con lo que
 * había antes. Un visor que es el primer elemento de un `LazyColumn` se va de la pantalla en cuanto
 * llega el segundo resultado, justo cuando el usuario quiere seguir apuntando.
 */
@Composable
private fun ScannerLayout(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    previewEngine: CameraPreviewEngine?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // `weight` y no una altura fija: la hoja de resultados crece con el contenido y el
                // visor cede exactamente lo que ella necesita, sin números mágicos que cuadren en
                // un teléfono y no en otro.
                .weight(1f)
                .padding(Spacing.md),
        ) {
            ViewfinderArea(state, previewEngine, onAction, advancedMode = false)
        }

        ResultsSheet(state = state, onAction = onAction, advancedMode = false)
    }
}

/**
 * La disposición del banco de pruebas: visor con proporción fija arriba y el catálogo debajo.
 *
 * Aquí el visor **sí** se desplaza con el resto: la pregunta que se responde en esta pantalla es
 * "qué motor lee mejor", y para contestarla hace falta ver los ocho motores y sus métricas, no
 * mantener el encuadre.
 */
@Composable
private fun WorkbenchLayout(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    previewEngine: CameraPreviewEngine?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(VIEWFINDER_ASPECT_RATIO)) {
                ViewfinderArea(state, previewEngine, onAction, advancedMode = true)
            }
        }

        if (state.isManualEntryActive) {
            item { ManualEntryField(state, onAction) }
        }

        item { WorkbenchControls(state, onAction) }

        item { FormatFilters(state, onAction) }

        item {
            SectionTitle(stringResource(Res.string.engines_title))
        }

        items(state.catalog, key = { it.id.id }) { status ->
            EngineCard(
                status = status,
                selected = status.id == state.selectedEngineId,
                active = status.id == state.activeEngineId,
                onSelect = { onAction(ScannerAction.SelectEngine(status.id)) },
            )
        }

        if (state.detections.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.detections_title)) }

            items(state.detections, key = { it.id }) { detection ->
                DetectionCard(
                    detection = detection,
                    canShare = state.canShare,
                    advancedMode = true,
                    highlighted = detection.id == state.latestDetection?.id,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

/**
 * Traduce un mensaje del ViewModel a texto.
 *
 * Es la única pieza que conoce las dos mitades: el ViewModel dice qué pasó y aquí se le pone
 * nombre. [ScannerMessage.Raw] pasa tal cual porque su texto lo produjo la plataforma.
 *
 * Es `suspend` y usa `getString` en lugar de ser `@Composable` con `stringResource`: se la llama
 * desde dentro de un `LaunchedEffect`, que es una corrutina y **no** un contexto composable.
 */
private suspend fun resolve(message: ScannerMessage): String = when (message) {
    ScannerMessage.EngineSwitched -> getString(Res.string.message_engine_switched)
    ScannerMessage.CameraPermissionDenied ->
        getString(Res.string.message_camera_permission_denied)

    ScannerMessage.ManualInputUnavailable ->
        getString(Res.string.message_manual_input_unavailable)

    ScannerMessage.Copied -> getString(Res.string.message_copied)
    ScannerMessage.CopyFailed -> getString(Res.string.message_copy_failed)
    ScannerMessage.ShareFailed -> getString(Res.string.message_share_failed)
    ScannerMessage.OpenFailed -> getString(Res.string.message_open_failed)
    ScannerMessage.NoCodeInImage -> getString(Res.string.message_no_code_in_image)
    is ScannerMessage.Raw -> message.text
}

private const val VIEWFINDER_ASPECT_RATIO = 3f / 4f
