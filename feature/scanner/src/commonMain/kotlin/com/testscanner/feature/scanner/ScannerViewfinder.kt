package com.testscanner.feature.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.testscanner.core.designsystem.Radius
import com.testscanner.core.designsystem.Spacing
import com.testscanner.core.scanner.ui.CameraPreviewEngine
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.a11y_detections_in_view
import com.testscanner.feature.scanner.resources.a11y_no_detections
import com.testscanner.feature.scanner.resources.a11y_viewfinder
import com.testscanner.feature.scanner.resources.a11y_zoom
import com.testscanner.feature.scanner.resources.action_grant_camera
import com.testscanner.feature.scanner.resources.action_resume
import com.testscanner.feature.scanner.resources.action_scan_from_image
import com.testscanner.feature.scanner.resources.action_stop
import com.testscanner.feature.scanner.resources.no_camera_body
import com.testscanner.feature.scanner.resources.no_camera_title
import com.testscanner.feature.scanner.resources.permission_body
import com.testscanner.feature.scanner.resources.permission_title
import com.testscanner.feature.scanner.resources.session_paused
import com.testscanner.feature.scanner.resources.session_scanning
import com.testscanner.feature.scanner.resources.session_scanning_simple
import com.testscanner.feature.scanner.resources.session_starting
import com.testscanner.feature.scanner.resources.torch_off
import com.testscanner.feature.scanner.resources.torch_on
import com.testscanner.feature.scanner.resources.zoom_ratio
import org.jetbrains.compose.resources.stringResource

/**
 * El visor y todo lo que se superpone a él.
 *
 * Cuatro cosas pueden ocupar este espacio y son excluyentes: el catálogo cargando, la petición de
 * permiso, el aviso de que aquí no hay cámara, o la cámara de verdad. Resolverlo con un `when` y no
 * con condiciones sueltas es lo que impide el estado imposible de siempre — el visor negro con un
 * cartel de permiso encima.
 */
@Composable
internal fun ViewfinderArea(
    state: ScannerState,
    previewEngine: CameraPreviewEngine?,
    onAction: (ScannerAction) -> Unit,
    advancedMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()

            state.needsCameraPermission -> PermissionRequest(onAction)

            previewEngine != null -> CameraViewfinder(previewEngine, state)

            !state.hasLiveCameraEngine -> NoCameraAvailable(state, onAction)

            // Hay motor de cámara pero todavía no está pintando: la sesión arranca sola en cuanto
            // el catálogo confirma que puede (ver `ScannerAction.ScreenShown`).
            else -> CircularProgressIndicator()
        }

        SessionBadge(
            state = state,
            advancedMode = advancedMode,
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md),
        )

        ViewfinderControls(
            state = state,
            onAction = onAction,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
        )

        if (state.canControlZoom) {
            ZoomControl(
                state = state,
                onAction = onAction,
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
            )
        }
    }
}

/**
 * Visor: superficie nativa del motor abajo, overlay de Compose común encima.
 *
 * La pantalla no sabe qué motor está pintando ni con qué API — solo que implementa
 * `CameraPreviewEngine`. Añadir el motor de iOS o el del navegador no toca este archivo.
 */
@Composable
private fun CameraViewfinder(previewEngine: CameraPreviewEngine, state: ScannerState) {
    // Ni la superficie nativa ni el Canvas del overlay producen semántica: para un lector de
    // pantalla, el visor es un agujero. Describirlo con cuántos códigos hay dentro es lo que
    // convierte el encuadre en información y no en un rectángulo mudo (RNF-05).
    val inView = if (state.detections.isEmpty()) {
        stringResource(Res.string.a11y_no_detections)
    } else {
        stringResource(Res.string.a11y_detections_in_view, state.detections.size)
    }
    // El texto se arma fuera del `semantics`: su lambda no es composable y `stringResource` sí.
    val description = "${stringResource(Res.string.a11y_viewfinder)}. $inView"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = description },
    ) {
        previewEngine.CameraPreview(Modifier.fillMaxSize())
        // En el navegador el vídeo es un elemento del DOM sobre el canvas: el overlay se dibujaría
        // debajo y no se vería. Mejor no pintarlo que dejar código que no produce nada.
        if (!previewEngine.occludesOverlay) {
            ScanOverlay(detections = state.detections)
        }
    }
}

/**
 * Petición de permiso con su motivo delante.
 *
 * Ocupa el visor entero en lugar de ser un botón pequeño porque es lo único que se puede hacer en
 * esta pantalla hasta que se conceda, y porque el motivo importa: la razón por la que alguien
 * concede la cámara a un escáner es entender que las imágenes no salen del teléfono.
 */
@Composable
private fun PermissionRequest(onAction: (ScannerAction) -> Unit) {
    ViewfinderMessage(
        icon = Icons.Filled.PhotoCamera,
        title = stringResource(Res.string.permission_title),
        body = stringResource(Res.string.permission_body),
    ) {
        Button(onClick = { onAction(ScannerAction.RequestCameraPermission) }) {
            Text(stringResource(Res.string.action_grant_camera))
        }
    }
}

/**
 * Estado permanente del escritorio: hay decodificador de archivos y entrada manual, pero ninguna
 * captura de webcam. Decirlo y ofrecer la salida es mejor que un visor negro para siempre.
 */
@Composable
private fun NoCameraAvailable(state: ScannerState, onAction: (ScannerAction) -> Unit) {
    ViewfinderMessage(
        icon = Icons.Filled.NoPhotography,
        title = stringResource(Res.string.no_camera_title),
        body = stringResource(Res.string.no_camera_body),
    ) {
        if (state.canScanFromImage) {
            Button(
                onClick = { onAction(ScannerAction.ScanFromImage) },
                enabled = !state.isDecodingImage,
            ) {
                Text(stringResource(Res.string.action_scan_from_image))
            }
        }
    }
}

/** La forma común de los tres estados que sustituyen al visor: icono, título, explicación y salida. */
@Composable
private fun ViewfinderMessage(
    icon: ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            // Un solo nodo para el lector de pantalla: icono, título y cuerpo son una sola idea, y
            // por separado obligan a tres gestos para enterarse de una cosa.
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = icon,
            // Decorativo: lo que el icono dice ya lo dice el título justo debajo.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Spacing.xl),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MESSAGE_MAX_WIDTH),
        )
        action()
    }
}

/**
 * Píldora de estado sobre el visor.
 *
 * Es una región viva: cuando la sesión arranca, se degrada a otro motor o termina, un lector de
 * pantalla lo anuncia solo. Sin esto, quien no ve la pantalla no tiene forma de saber que pasó algo.
 */
@Composable
private fun SessionBadge(
    state: ScannerState,
    advancedMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when (state.sessionStatus) {
        SessionStatus.Starting -> stringResource(Res.string.session_starting)

        // Qué motor está leyendo es la respuesta a la pregunta del banco de pruebas, y ruido para
        // quien solo quiere leer un QR: el id es interno y no le dice nada.
        SessionStatus.Scanning -> if (advancedMode) {
            stringResource(Res.string.session_scanning, state.activeEngineId?.id ?: "-")
        } else {
            stringResource(Res.string.session_scanning_simple)
        }

        SessionStatus.Idle, SessionStatus.Finished -> stringResource(Res.string.session_paused)
    }

    AnimatedVisibility(
        visible = state.hasLiveCameraEngine && !state.needsCameraPermission,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.pill),
            // Sobre el vídeo hay que garantizar contraste sin saber qué está enfocando la cámara.
            // El contenedor inverso da un fondo opaco y su `on` correspondiente, que es un par que
            // `ContrastTest` mide.
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Los controles que flotan sobre el visor: linterna y escanear desde imagen.
 *
 * Aparecen solo si el motor activo declara la capacidad, que es la misma regla de siempre: la UI no
 * nombra motores, lee capacidades. Por eso el Google Code Scanner esconde la linterna sin que este
 * archivo sepa que existe.
 */
@Composable
private fun ViewfinderControls(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.canControlTorch) {
            val torchLabel = stringResource(
                if (state.torchEnabled) Res.string.torch_on else Res.string.torch_off,
            )
            FilledIconButton(
                onClick = { onAction(ScannerAction.ToggleTorch) },
                colors = if (state.torchEnabled) {
                    IconButtonDefaults.filledIconButtonColors()
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
            ) {
                Icon(
                    imageVector = if (state.torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = torchLabel,
                )
            }
        }

        // Pausar existe aunque la sesión arranque sola: apagar la cámara sin salir de la pantalla
        // es lo que se hace para leer algo en el resultado sin que siga escaneando por debajo.
        if (state.hasLiveCameraEngine && !state.needsCameraPermission) {
            val scanning = state.sessionStatus == SessionStatus.Scanning ||
                state.sessionStatus == SessionStatus.Starting
            FilledIconButton(
                onClick = {
                    onAction(if (scanning) ScannerAction.StopSession else ScannerAction.StartSession)
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(
                    imageVector = if (scanning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (scanning) Res.string.action_stop else Res.string.action_resume,
                    ),
                )
            }
        }

        if (state.canScanFromImage && state.detections.isNotEmpty()) {
            // Con resultados en pantalla la hoja ya no ofrece "desde imagen" —ahí manda el
            // resultado—, así que el acceso se mantiene aquí arriba en lugar de desaparecer.
            FilledIconButton(
                onClick = { onAction(ScannerAction.ScanFromImage) },
                enabled = !state.isDecodingImage,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = stringResource(Res.string.action_scan_from_image),
                )
            }
        }
    }
}

@Composable
private fun ZoomControl(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomLabel = stringResource(Res.string.zoom_ratio, state.zoomRatio.toInt())
    val zoomName = stringResource(Res.string.a11y_zoom)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = zoomLabel, style = MaterialTheme.typography.labelMedium)
            // Un Slider sin nombre se anuncia como un porcentaje suelto. Con el nombre y el aumento
            // en el estado, se lee "Zoom de la cámara, 3×" en vez de "60 %".
            Slider(
                value = state.zoomRatio,
                onValueChange = { onAction(ScannerAction.SetZoom(it)) },
                valueRange = MIN_ZOOM..MAX_ZOOM,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = zoomName
                    stateDescription = zoomLabel
                },
            )
        }
    }
}

private val MESSAGE_MAX_WIDTH = Spacing.xxl * 8

// El rango real depende de la cámara; el motor recorta al máximo que admita el dispositivo.
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
