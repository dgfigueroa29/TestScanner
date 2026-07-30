package com.testscanner.feature.scanner

import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.ui.CameraPreviewEngine

/**
 * Resuelve si el motor activo aporta superficie de preview.
 *
 * Existe para que la pantalla no tenga que hablar con el repositorio ni el ViewModel tenga que
 * guardar objetos de UI en su estado. Es la única pieza que hace el `as?` hacia una capacidad
 * opcional, y lo hace **sin nombrar ningún motor concreto**: da igual que el preview lo aporte
 * CameraX hoy o AVFoundation mañana.
 */
class EnginePreviewResolver(
    private val repository: ScannerEngineRepository,
) {
    fun previewFor(engineId: ScannerEngineId?): CameraPreviewEngine? =
        engineId?.let { repository.engine(it) as? CameraPreviewEngine }
}
