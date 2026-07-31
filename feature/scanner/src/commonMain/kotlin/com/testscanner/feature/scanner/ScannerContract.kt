package com.testscanner.feature.scanner

import com.testscanner.core.domain.model.EngineStatus
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
    val error: ScanError? = null,
) {
    val usableEngines: List<EngineStatus> get() = catalog.filter { it.isUsable }

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
    data object ToggleTorch : ScannerAction
    data class SetZoom(val ratio: Float) : ScannerAction
    data object RequestCameraPermission : ScannerAction
    data object DismissError : ScannerAction
}

/**
 * Eventos de una sola vez. No forman parte del estado: no deben re-emitirse al recomponer.
 *
 * Solo hay un caso porque solo hay uno que ocurra. Había un `OpenUrl` declarado que ningún código
 * emitía nunca; volverá cuando RF-13 (copiar, compartir, abrir enlace) exista de verdad.
 */
sealed interface ScannerEffect {
    data class ShowMessage(val text: String) : ScannerEffect
}
