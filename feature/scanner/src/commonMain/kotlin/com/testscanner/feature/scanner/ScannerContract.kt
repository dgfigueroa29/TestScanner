package com.testscanner.feature.scanner

import com.testscanner.core.domain.model.EngineStatus
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScannerEngineId

/** Estado de la sesión de escaneo, tal y como lo ve la UI. */
enum class SessionStatus { Idle, Starting, Scanning, Finished }

/**
 * Estado de la pantalla de escaneo.
 *
 * [selectedEngineId] es lo que el usuario eligió; [activeEngineId] es el motor que está corriendo
 * de verdad. Pueden diferir cuando la cadena de fallback degrada, y la UI necesita ambos para
 * poder explicárselo al usuario en lugar de mostrar un error (G4).
 */
data class ScannerState(
    val isLoading: Boolean = true,
    val catalog: List<EngineStatus> = emptyList(),
    val selectedEngineId: ScannerEngineId? = null,
    val activeEngineId: ScannerEngineId? = null,
    val switchedFrom: ScannerEngineId? = null,
    val formats: Set<BarcodeFormat> = BarcodeFormat.all,
    val continuous: Boolean = false,
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val detections: List<Detection> = emptyList(),
    val manualInput: String = "",
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1f,
    /** Si el sistema ofrece hoja de compartir; en escritorio no la hay. */
    val canShare: Boolean = false,
    /** Hay una imagen decodificándose (RF-07). Puede tardar: bloquea el botón y muestra progreso. */
    val isDecodingImage: Boolean = false,
    val error: ScanError? = null,
) {
    val usableEngines: List<EngineStatus> get() = catalog.filter { it.isUsable }

    /**
     * Escanear desde imagen se ofrece solo si algún motor sabe hacerlo. Es la misma regla que
     * oculta la linterna: la UI no nombra motores, lee capacidades.
     *
     * La condición es `canDecodeImages` y no `isUsable` porque un motor al que le falta el permiso
     * de cámara sigue sabiendo leer un archivo — y ese es justo el momento en que la foto es la
     * única salida. La regla vive en el dominio para que el selector aplique la misma.
     */
    val canScanFromImage: Boolean get() = catalog.any { it.canDecodeImages }

    /** La entrada manual se muestra solo si el motor activo se alimenta de texto. */
    val isManualEntryActive: Boolean get() = activeEngineId == ScannerEngineId.ManualInput

    private val activeCapabilities get() =
        catalog.firstOrNull { it.id == activeEngineId }?.descriptor?.capabilities

    /**
     * Los controles de cámara se derivan de las capacidades declaradas, no de una lista de motores
     * que los tengan. Por eso el Google Code Scanner esconde la linterna sin que la UI lo nombre.
     */
    val canControlTorch: Boolean get() = activeCapabilities?.supportsTorch == true

    val canControlZoom: Boolean get() = activeCapabilities?.supportsZoom == true

    /** Motores que el usuario puede desbloquear concediendo un permiso o descargando un modelo. */
    val actionableEngines: List<EngineStatus>
        get() = catalog.filter { it.installed && it.availability.isActionable }
}

sealed interface ScannerAction {
    data object Refresh : ScannerAction
    data object StartSession : ScannerAction
    data object StopSession : ScannerAction
    data class SelectEngine(val id: ScannerEngineId?) : ScannerAction
    data class ToggleFormat(val format: BarcodeFormat) : ScannerAction
    data class SetContinuous(val enabled: Boolean) : ScannerAction
    data class ManualInputChanged(val value: String) : ScannerAction
    data object SubmitManualInput : ScannerAction
    /** Elegir una imagen del dispositivo y decodificarla (RF-07). */
    data object ScanFromImage : ScannerAction
    /**
     * Ejecutar una acción sobre un resultado (RF-13).
     *
     * Lleva el texto ya redactado porque redactarlo es cosa de la pantalla: el dominio dice qué
     * datos son relevantes (`ShareableContent`) y la UI los pasa por sus recursos traducibles.
     */
    data class RunResultAction(val action: ResultAction, val text: String) : ScannerAction
    data object ToggleTorch : ScannerAction
    data class SetZoom(val ratio: Float) : ScannerAction
    data object RequestCameraPermission : ScannerAction
    data object DismissError : ScannerAction
}

/**
 * Eventos de una sola vez. No forman parte del estado: no deben re-emitirse al recomponer.
 *
 * Un solo caso basta: abrir un enlace no es un efecto de la UI sino una acción de plataforma que
 * ejecuta `PlatformActions` (RF-13), y lo único que vuelve aquí es si hay algo que contar.
 */
sealed interface ScannerEffect {
    data class ShowMessage(val message: ScannerMessage) : ScannerEffect
}
