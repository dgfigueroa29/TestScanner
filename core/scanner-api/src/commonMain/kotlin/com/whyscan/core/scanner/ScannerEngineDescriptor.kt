package com.whyscan.core.scanner

import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform

/**
 * Ficha declarativa de un motor: identidad, procedencia, plataformas y capacidades.
 *
 * Existe con independencia de que el motor esté implementado, lo que permite que el catálogo esté
 * completo desde la Fase 1 (ver [EngineAvailability.NotImplemented]).
 *
 * @param plannedPhase fase del roadmap en la que llega. `1` significa disponible ya.
 * @param requiresDependency artefacto o framework del que depende, para mostrarlo en la ficha.
 */
data class ScannerEngineDescriptor(
    val id: ScannerEngineId,
    val displayName: String,
    val vendor: String,
    val description: String,
    val platforms: Set<ScannerPlatform>,
    val capabilities: ScannerCapabilities,
    val plannedPhase: Int,
    val requiresDependency: String? = null,
    val strength: String,
    val limitation: String,
) {
    fun runsOn(platform: ScannerPlatform): Boolean = platform in platforms
}
