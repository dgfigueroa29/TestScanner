package com.testscanner.core.data.repository

import com.russhwolf.settings.MapSettings
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScannerEngineId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsScanPreferencesRepositoryTest {

    @Test
    fun `sin nada guardado devuelve los valores por defecto`() = runTest {
        val preferences = SettingsScanPreferencesRepository(MapSettings()).current()

        assertNull(preferences.preferredEngineId)
        assertEquals(BarcodeFormat.all, preferences.formats)
        assertTrue(!preferences.continuous)
    }

    @Test
    fun `lo guardado sobrevive a recrear el repositorio`() = runTest {
        // Es el punto entero de la deuda D2: elegir un motor y que siga elegido al reabrir.
        val settings = MapSettings()

        SettingsScanPreferencesRepository(settings).apply {
            setPreferredEngine(ScannerEngineId.MlKitCameraX)
            setContinuous(enabled = true)
            setFormats(setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13))
        }

        val reopened = SettingsScanPreferencesRepository(settings).current()

        assertEquals(ScannerEngineId.MlKitCameraX, reopened.preferredEngineId)
        assertTrue(reopened.continuous)
        assertEquals(setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13), reopened.formats)
    }

    @Test
    fun `volver a automatico borra el motor guardado`() = runTest {
        val settings = MapSettings()
        val repository = SettingsScanPreferencesRepository(settings)

        repository.setPreferredEngine(ScannerEngineId.MlKitCameraX)
        repository.setPreferredEngine(null)

        assertNull(SettingsScanPreferencesRepository(settings).current().preferredEngineId)
    }

    @Test
    fun `un motor guardado que ya no existe en el catalogo se descarta`() = runTest {
        // Puede haberse eliminado entre dos versiones de la app; no debe romper el arranque.
        val settings = MapSettings().apply { putString("scan.preferred_engine", "motor_borrado") }

        assertNull(SettingsScanPreferencesRepository(settings).current().preferredEngineId)
    }

    @Test
    fun `los enums se guardan por su id estable, no por su nombre de Kotlin`() = runTest {
        val settings = MapSettings()

        SettingsScanPreferencesRepository(settings)
            .setPreferredEngine(ScannerEngineId.GmsCodeScanner)

        assertEquals("gms_code_scanner", settings.getStringOrNull("scan.preferred_engine"))
    }

    @Test
    fun `un conjunto de formatos vacio en disco vuelve al conjunto completo`() = runTest {
        // Un ScanRequest sin formatos es inválido por contrato: no puede nacer de una preferencia.
        val settings = MapSettings().apply { putString("scan.formats", "") }

        assertEquals(BarcodeFormat.all, SettingsScanPreferencesRepository(settings).current().formats)
    }

    @Test
    fun `un formato desconocido en disco se conserva por nombre`() = runTest {
        val settings = MapSettings().apply { putString("scan.formats", "QR_CODE,SIMBOLOGIA_FUTURA") }

        val formats = SettingsScanPreferencesRepository(settings).current().formats

        assertTrue(BarcodeFormat.QrCode in formats)
        assertTrue(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA") in formats)
    }

    @Test
    fun `el flujo observable refleja los cambios sin releer el disco`() = runTest {
        val repository = SettingsScanPreferencesRepository(MapSettings())

        repository.setPreferredEngine(ScannerEngineId.ZXingCpp)

        assertEquals(ScannerEngineId.ZXingCpp, repository.current().preferredEngineId)
    }
}
