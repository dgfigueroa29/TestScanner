package com.whyscan.feature.scanner

import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.scanner.capability
import com.whyscan.core.scanner.ui.CameraPreviewEngine

/**
 * Resuelve si el motor activo aporta superficie de preview.
 *
 * Existe para que la pantalla no tenga que hablar con el repositorio ni el ViewModel tenga que
 * guardar objetos de UI en su estado. Busca la capacidad **sin nombrar ningún motor concreto**: da
 * igual que el preview lo aporte CameraX, AVFoundation o un `<video>` del navegador.
 *
 * Usa `capability()` y no un `as?` directo porque el `as?` da `null` si el motor viene envuelto en
 * decoradores. Hoy el repositorio los devuelve sin envolver, así que da lo mismo — pero era una
 * suposición implícita, y la suite de contrato la dejó a la vista.
 */
class EnginePreviewResolver(
    private val repository: ScannerEngineRepository,
) {
    fun previewFor(engineId: ScannerEngineId?): CameraPreviewEngine? =
        engineId?.let { repository.engine(it)?.capability<CameraPreviewEngine>() }
}
