package com.whyscan.core.data.repository

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsScanHistoryRepositoryTest {

    private fun detection(
        value: String,
        engineId: ScannerEngineId = ScannerEngineId.MlKitCameraX,
        format: BarcodeFormat = BarcodeFormat.QrCode,
        at: Long = 1_000L,
    ) = Detection.of(
        barcode = Barcode(rawValue = value, format = format),
        engineId = engineId,
        detectedAtMillis = at,
        latencyMillis = 20L,
    )

    @Test
    fun `lo_guardado_sobrevive_a_reconstruir_el_repositorio`() = runTest {
        // Es toda la razón de ser de esta clase: en Web el historial se perdía al recargar.
        val settings = MapSettings()
        SettingsScanHistoryRepository(settings).save(detection("hola"))

        val reopened = SettingsScanHistoryRepository(settings)

        assertEquals(listOf("hola"), reopened.observeHistory().first().map { it.barcode.rawValue })
    }

    @Test
    fun `conserva_el_motor_el_formato_y_la_latencia`() = runTest {
        // Sin esto el historial persistido no serviría para comparar motores, que es el objetivo.
        val settings = MapSettings()
        SettingsScanHistoryRepository(settings).save(
            detection("7501234567893", ScannerEngineId.ZXingCpp, BarcodeFormat.Ean13),
        )

        val restored = SettingsScanHistoryRepository(settings).observeHistory().first().single()

        assertEquals(ScannerEngineId.ZXingCpp, restored.engineId)
        assertEquals(BarcodeFormat.Ean13, restored.barcode.format)
        assertEquals(20L, restored.latencyMillis)
        assertEquals(ScanSource.LiveCamera, restored.source)
    }

    @Test
    fun `lo_mas_reciente_queda_primero`() = runTest {
        val repository = SettingsScanHistoryRepository(MapSettings())

        repository.save(detection("primero"))
        repository.save(detection("segundo"))

        assertEquals(
            listOf("segundo", "primero"),
            repository.observeHistory().first().map { it.barcode.rawValue },
        )
    }

    @Test
    fun `borrar_vacia_tambien_lo_persistido`() = runTest {
        val settings = MapSettings()
        val repository = SettingsScanHistoryRepository(settings)
        repository.save(detection("hola"))

        repository.clear()

        assertTrue(SettingsScanHistoryRepository(settings).observeHistory().first().isEmpty())
    }

    @Test
    fun `no_crece_sin_limite`() = runTest {
        // localStorage tiene cuota; sin techo, el historial acabaría no cabiendo.
        val repository = SettingsScanHistoryRepository(MapSettings(), maxEntries = 3)

        repeat(5) { repository.save(detection("codigo-$it")) }

        val stored = repository.observeHistory().first()
        assertEquals(3, stored.size)
        assertEquals("codigo-4", stored.first().barcode.rawValue)
    }

    @Test
    fun `un_historial_ilegible_se_descarta_en_vez_de_reventar`() = runTest {
        // Basta con que una versión anterior guardara otro formato. Perder el historial es malo;
        // no poder abrir la app es peor.
        val settings = MapSettings().apply { putString("scan_history", "{esto no es json}") }

        val repository = SettingsScanHistoryRepository(settings)

        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun `una_entrada_de_un_motor_que_ya_no_existe_se_ignora_al_leer`() = runTest {
        val settings = MapSettings().apply {
            putString(
                "scan_history",
                """
                [{"id":"1","rawValue":"hola","formatId":"QR_CODE","engineId":"motor_eliminado",
                  "sourceName":"LiveCamera","detectedAtMillis":1,"latencyMillis":null}]
                """.trimIndent(),
            )
        }

        assertTrue(SettingsScanHistoryRepository(settings).observeHistory().first().isEmpty())
    }

    @Test
    fun `un_formato_desconocido_se_conserva_en_vez_de_perderse`() = runTest {
        val settings = MapSettings()
        SettingsScanHistoryRepository(settings).save(
            detection("x", format = BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA")),
        )

        val restored = SettingsScanHistoryRepository(settings).observeHistory().first().single()

        assertEquals(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA"), restored.barcode.format)
    }

    @Test
    fun `si_el_almacen_esta_lleno_la_deteccion_sigue_en_pantalla`() = runTest {
        // El usuario acaba de escanear algo: convertir "no cabe" en un escaneo fallido sería peor
        // que quedarse sin persistirlo.
        val repository = SettingsScanHistoryRepository(FullSettings())

        repository.save(detection("hola"))

        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun `buscar_por_id_encuentra_lo_guardado`() = runTest {
        val repository = SettingsScanHistoryRepository(MapSettings())
        val detection = detection("hola")
        repository.save(detection)

        assertEquals(detection.id, repository.findById(detection.id)?.id)
        assertNull(repository.findById("no-existe"))
    }
}

/** Almacén que rechaza toda escritura, como `localStorage` al superar su cuota. */
private class FullSettings : Settings by MapSettings() {
    override fun putString(key: String, value: String): Unit = error("cuota superada")
}
