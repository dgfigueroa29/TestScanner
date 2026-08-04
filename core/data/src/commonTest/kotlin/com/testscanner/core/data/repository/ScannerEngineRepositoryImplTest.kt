package com.testscanner.core.data.repository

import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScannerEngineRepositoryImplTest {

    private class StubEngine(
        override val id: ScannerEngineId,
        private var availability: EngineAvailability = EngineAvailability.Available,
    ) : BarcodeScannerEngine {
        var availabilityCalls = 0
            private set

        override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.byId(id)

        override suspend fun availability(): EngineAvailability {
            availabilityCalls++
            return availability
        }

        override fun scan(request: ScanRequest): Flow<ScanEvent> = emptyFlow()

        fun setAvailability(value: EngineAvailability) {
            availability = value
        }
    }

    private fun repository(
        platform: ScannerPlatform = ScannerPlatform.Android,
        engines: List<BarcodeScannerEngine> = emptyList(),
    ) = ScannerEngineRepositoryImpl(platform = platform, installedEngines = engines)

    @Test
    fun `el_catalogo_siempre_lista_todos_los_motores_esten_instalados_o_no`() = runTest {
        val catalog = repository(engines = listOf(StubEngine(ScannerEngineId.ManualInput)))
            .observeCatalog().first()

        assertEquals(ScannerEngineCatalog.all.size, catalog.size)
        assertEquals(ScannerEngineId.entries.toSet(), catalog.map { it.id }.toSet())
    }

    @Test
    fun `un_motor_instalado_reporta_su_propia_disponibilidad`() = runTest {
        val engine = StubEngine(ScannerEngineId.ManualInput)
        val status = repository(engines = listOf(engine)).status(ScannerEngineId.ManualInput)

        assertEquals(EngineAvailability.Available, status?.availability)
        assertTrue(status?.installed == true)
    }

    @Test
    fun `un_motor_de_otra_plataforma_se_reporta_como_no_soportado`() = runTest {
        // Vision existe en el catálogo pero no en un binario de Android: la UI debe poder decir
        // por qué, no simplemente ocultarlo.
        val status = repository(platform = ScannerPlatform.Android)
            .status(ScannerEngineId.VisionIos)

        val availability = status?.availability
        assertTrue(availability is EngineAvailability.Unsupported)
        assertTrue("Android" in availability.reason)
        assertTrue(status.installed.not())
    }

    @Test
    fun `un_motor_de_esta_plataforma_aun_sin_implementar_reporta_su_fase`() = runTest {
        val status = repository(platform = ScannerPlatform.Android)
            .status(ScannerEngineId.MlKitCameraX)

        val availability = status?.availability
        assertTrue(availability is EngineAvailability.NotImplemented)
        assertEquals(ScannerEngineCatalog.mlKitCameraX.plannedPhase, availability.plannedPhase)
    }

    @Test
    fun `cada_colecta_recalcula_la_disponibilidad_en_lugar_de_servir_un_valor_cacheado`() = runTest {
        // La disponibilidad cambia bajo los pies: el usuario concede el permiso, ML Kit termina de
        // descargar, otra app toma la cámara. Un estado inicial cacheado dejaría la UI mintiendo.
        val engine = StubEngine(ScannerEngineId.ManualInput)
        val repository = repository(engines = listOf(engine))

        repository.observeCatalog().first()
        engine.setAvailability(EngineAvailability.Unsupported("cámara ocupada"))
        val second = repository.observeCatalog().first()

        assertEquals(2, engine.availabilityCalls)
        assertTrue(
            second.first { it.id == ScannerEngineId.ManualInput }
                .availability is EngineAvailability.Unsupported,
        )
    }

    @Test
    fun `engine_devuelve_null_for_un_motor_no_enlazado_en_este_binario`() {
        assertNull(repository().engine(ScannerEngineId.VisionIos))
    }

    @Test
    fun `refresh_vuelve_a_emitir_el_catalogo`() = runTest {
        val engine = StubEngine(ScannerEngineId.ManualInput)
        val repository = repository(engines = listOf(engine))

        repository.observeCatalog().first()
        repository.refresh()
        repository.observeCatalog().first()

        assertEquals(2, engine.availabilityCalls)
    }

    @Test
    fun `solo_los_motores_instalados_y_disponibles_son_usables`() = runTest {
        val catalog = repository(engines = listOf(StubEngine(ScannerEngineId.ManualInput)))
            .observeCatalog().first()

        assertEquals(
            listOf(ScannerEngineId.ManualInput),
            catalog.filter { it.isUsable }.map { it.id },
        )
    }

    @Test
    fun `el_descriptor_conserva_las_capacidades_declaradas_en_el_catalogo`() = runTest {
        val status = repository(engines = listOf(StubEngine(ScannerEngineId.ManualInput)))
            .status(ScannerEngineId.ManualInput)

        assertTrue(
            ScanSource.ManualInput in status!!.descriptor.capabilities.sources,
        )
    }
}
