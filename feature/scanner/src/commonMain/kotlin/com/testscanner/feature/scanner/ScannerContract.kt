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
    val error: ScanError? = null,
) {
    val usableEngines: List<EngineStatus> get() = catalog.filter { it.isUsable }

    /** La entrada manual se muestra solo si el motor activo se alimenta de texto. */
    val isManualEntryActive: Boolean get() = activeEngineId == ScannerEngineId.ManualInput
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
    data object DismissError : ScannerAction
}

/** Eventos de una sola vez. No forman parte del estado: no deben re-emitirse al recomponer. */
sealed interface ScannerEffect {
    data class ShowMessage(val text: String) : ScannerEffect
    data class OpenUrl(val url: String) : ScannerEffect
}
