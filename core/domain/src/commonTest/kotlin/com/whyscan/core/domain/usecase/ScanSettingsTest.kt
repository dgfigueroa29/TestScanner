package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Los ajustes de escaneo, probados sin ViewModel.
 *
 * Antes la única regla que hay aquí —el conjunto vacío de formatos— solo se ejercitaba a través del
 * ViewModel de escaneo, con sus doce colaboradores montados. Al agrupar (deuda D16) se puede
 * comprobar donde vive.
 */
class ScanSettingsTest {

    private class InMemoryPreferences : ScanPreferencesRepository {
        private val state = MutableStateFlow(ScanPreferences())

        override fun observePreferences(): Flow<ScanPreferences> = state.asStateFlow()
        override suspend fun current(): ScanPreferences = state.value

        override suspend fun setPreferredEngine(id: ScannerEngineId?) {
            state.value = state.value.copy(preferredEngineId = id)
        }

        override suspend fun setFormats(formats: Set<BarcodeFormat>) {
            state.value = state.value.copy(formats = formats)
        }

        override suspend fun setContinuous(enabled: Boolean) {
            state.value = state.value.copy(continuous = enabled)
        }

        override suspend fun setAllowMultiple(enabled: Boolean) {
            state.value = state.value.copy(allowMultiple = enabled)
        }
    }

    private val repository = InMemoryPreferences()
    private val settings = ScanSettings(repository)

    @Test
    fun `quedarse sin formatos significa todos, no ninguno`() = runTest {
        settings.setFormats(emptySet())

        assertEquals(BarcodeFormat.all, repository.current().formats)
    }

    @Test
    fun `una seleccion concreta de formatos se guarda tal cual`() = runTest {
        val only = setOf(BarcodeFormat.QrCode)

        settings.setFormats(only)

        assertEquals(only, repository.current().formats)
    }

    @Test
    fun `null como motor preferido vuelve a seleccion automatica`() = runTest {
        settings.preferEngine(ScannerEngineId.MlKitCameraX)
        assertEquals(ScannerEngineId.MlKitCameraX, settings.current().preferredEngineId)

        settings.preferEngine(null)

        assertNull(settings.current().preferredEngineId)
    }

    @Test
    fun `lo que se escribe se ve al observar`() = runTest {
        settings.setContinuous(enabled = true)

        assertEquals(true, settings.current().continuous)
    }
}
