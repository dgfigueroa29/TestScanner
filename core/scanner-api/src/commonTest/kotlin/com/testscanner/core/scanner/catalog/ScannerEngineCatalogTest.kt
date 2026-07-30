package com.testscanner.core.scanner.catalog

import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integridad del catálogo. Estas comprobaciones son las que evitan la deriva entre
 * `docs/ENGINES.md` y el código (riesgo R8 del SDD).
 */
class ScannerEngineCatalogTest {

    @Test
    fun `el catalogo cubre todos los identificadores de motor`() {
        val catalogIds = ScannerEngineCatalog.all.map { it.id }.toSet()
        assertEquals(ScannerEngineId.entries.toSet(), catalogIds)
    }

    @Test
    fun `no hay identificadores duplicados`() {
        val ids = ScannerEngineCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `todo motor declara al menos una plataforma y una fuente`() {
        ScannerEngineCatalog.all.forEach { descriptor ->
            assertTrue(
                descriptor.platforms.isNotEmpty(),
                "${descriptor.id} no declara plataformas",
            )
            assertTrue(
                descriptor.capabilities.sources.isNotEmpty(),
                "${descriptor.id} no declara fuentes",
            )
            assertTrue(
                descriptor.capabilities.supportedFormats.isNotEmpty(),
                "${descriptor.id} no declara formatos",
            )
        }
    }

    @Test
    fun `la fase planificada es valida`() {
        ScannerEngineCatalog.all.forEach { descriptor ->
            assertTrue(
                descriptor.plannedPhase in 1..MAX_PHASE,
                "${descriptor.id} declara una fase fuera de rango: ${descriptor.plannedPhase}",
            )
        }
    }

    @Test
    fun `la entrada manual esta disponible en las cuatro plataformas`() {
        // Es lo que garantiza que la cadena de fallback nunca se quede vacía (G4).
        assertEquals(
            ScannerPlatform.entries.toSet(),
            ScannerEngineCatalog.manualInput.platforms,
        )
        assertTrue(ScanSource.ManualInput in ScannerEngineCatalog.manualInput.capabilities.sources)
    }

    @Test
    fun `cada plataforma tiene al menos un motor en el catalogo`() {
        ScannerPlatform.entries.forEach { platform ->
            assertTrue(
                ScannerEngineCatalog.forPlatform(platform).isNotEmpty(),
                "No hay ningún motor para $platform",
            )
        }
    }

    @Test
    fun `un motor con UI propia no puede prometer control de camara`() {
        // El Google Code Scanner es el caso: si declarase linterna, el selector lo elegiría para
        // peticiones que exigen linterna y fallaría en tiempo de ejecución.
        ScannerEngineCatalog.all
            .filter { it.capabilities.providesOwnUi }
            .forEach { descriptor ->
                assertTrue(
                    !descriptor.capabilities.supportsTorch && !descriptor.capabilities.supportsZoom,
                    "${descriptor.id} declara UI propia y a la vez control de cámara",
                )
            }
    }

    private companion object {
        const val MAX_PHASE = 5
    }
}
