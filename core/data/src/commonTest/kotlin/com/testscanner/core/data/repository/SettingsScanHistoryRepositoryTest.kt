package com.testscanner.core.data.repository

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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
    fun `lo guardado sobrevive a reconstruir el repositorio`() = runTest {
        // Es toda la razón de ser de esta clase: en Web el historial se perdía al recargar.
        val settings = MapSettings()
        SettingsScanHistoryRepository(settings).save(detection("hola"))

        val reopened = SettingsScanHistoryRepository(settings)

        assertEquals(listOf("hola"), reopened.observeHistory().first().map { it.barcode.rawValue })
    }

    @Test
    fun `conserva el motor, el formato y la latencia`() = runTest {
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
    fun `lo mas reciente queda primero`() = runTest {
        val repository = SettingsScanHistoryRepository(MapSettings())

        repository.save(detection("primero"))
        repository.save(detection("segundo"))

        assertEquals(
            listOf("segundo", "primero"),
            repository.observeHistory().first().map { it.barcode.rawValue },
        )
    }

    @Test
    fun `borrar vacia tambien lo persistido`() = runTest {
        val settings = MapSettings()
        val repository = SettingsScanHistoryRepository(settings)
        repository.save(detection("hola"))

        repository.clear()

        assertTrue(SettingsScanHistoryRepository(settings).observeHistory().first().isEmpty())
    }

    @Test
    fun `no crece sin limite`() = runTest {
        // localStorage tiene cuota; sin techo, el historial acabaría no cabiendo.
        val repository = SettingsScanHistoryRepository(MapSettings(), maxEntries = 3)

        repeat(5) { repository.save(detection("codigo-$it")) }

        val stored = repository.observeHistory().first()
        assertEquals(3, stored.size)
        assertEquals("codigo-4", stored.first().barcode.rawValue)
    }

    @Test
    fun `un historial ilegible se descarta en vez de reventar`() = runTest {
        // Basta con que una versión anterior guardara otro formato. Perder el historial es malo;
        // no poder abrir la app es peor.
        val settings = MapSettings().apply { putString("scan_history", "{esto no es json}") }

        val repository = SettingsScanHistoryRepository(settings)

        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun `una entrada de un motor que ya no existe se ignora al leer`() = runTest {
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
    fun `un formato desconocido se conserva en vez de perderse`() = runTest {
        val settings = MapSettings()
        SettingsScanHistoryRepository(settings).save(
            detection("x", format = BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA")),
        )

        val restored = SettingsScanHistoryRepository(settings).observeHistory().first().single()

        assertEquals(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA"), restored.barcode.format)
    }

    @Test
    fun `si el almacen esta lleno la deteccion sigue en pantalla`() = runTest {
        // El usuario acaba de escanear algo: convertir "no cabe" en un escaneo fallido sería peor
        // que quedarse sin persistirlo.
        val repository = SettingsScanHistoryRepository(FullSettings())

        repository.save(detection("hola"))

        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun `buscar por id encuentra lo guardado`() = runTest {
        val repository = SettingsScanHistoryRepository(MapSettings())
        val detection = detection("hola")
        repository.save(detection)

        assertEquals(detection.id, repository.findById(detection.id)?.id)
        assertNull(repository.findById("no-existe"))
    }
}

/** Almacén que rechaza toda escritura, como `localStorage` al superar su cuota. */
private class FullSettings : Settings by MapSettings() {
    override fun putString(key: String, value: String): Unit =
        throw IllegalStateException("cuota superada")
}
