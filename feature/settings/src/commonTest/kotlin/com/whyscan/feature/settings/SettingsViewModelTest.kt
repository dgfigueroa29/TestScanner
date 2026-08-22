package com.whyscan.feature.settings

import app.cash.turbine.test
import com.whyscan.core.domain.repository.AppLanguage
import com.whyscan.core.domain.repository.AppPreferences
import com.whyscan.core.domain.repository.AppPreferencesRepository
import com.whyscan.core.domain.repository.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakeAppPreferencesRepository()

    @BeforeTest
    fun setUp() {
        // `viewModelScope` corre en `Dispatchers.Main`, que en `commonTest` no existe hasta que se
        // sustituye. Sin esto el ViewModel no llega ni a suscribirse al repositorio.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `el_estado_arranca_con_lo_que_hay_guardado_y_deja_de_cargar`() = runTest {
        repository.set(AppPreferences(themeMode = ThemeMode.Dark, advancedMode = true))

        viewModel().state.test {
            val state = awaitItem()

            assertEquals(ThemeMode.Dark, state.preferences.themeMode)
            assertEquals(true, state.preferences.advancedMode)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun `elegir_un_tema_lo_persiste`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.SetThemeMode(ThemeMode.Light))

        assertEquals(ThemeMode.Light, repository.current().themeMode)
    }

    @Test
    fun `elegir_un_idioma_lo_persiste`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.SetLanguage(AppLanguage.Spanish))

        assertEquals(AppLanguage.Spanish, repository.current().language)
    }

    @Test
    fun `en_una_plataforma_sin_soporte_de_idioma_no_se_guarda_nada`() = runTest {
        // El navegador no puede honrar un idioma distinto al suyo. Guardar la preferencia igual
        // dejaría un ajuste que la app ignora y que reaparecería en otro dispositivo, elegido por
        // nadie. Es el caso que justifica que `canChooseLanguage` llegue por constructor.
        val viewModel = viewModel(canChooseLanguage = false)

        viewModel.onAction(SettingsAction.SetLanguage(AppLanguage.Spanish))

        assertEquals(AppLanguage.System, repository.current().language)
    }

    @Test
    fun `el_modo_avanzado_se_persiste`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.SetAdvancedMode(true))

        assertEquals(true, repository.current().advancedMode)
    }

    @Test
    fun `un_cambio_en_el_repositorio_llega_al_estado`() = runTest {
        // La pantalla no es la única que puede escribir: el modo avanzado también se apaga desde
        // otra parte de la app. El estado tiene que venir del repositorio y no de un eco local.
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()

            repository.set(AppPreferences(themeMode = ThemeMode.Dark))

            assertEquals(ThemeMode.Dark, awaitItem().preferences.themeMode)
        }
    }

    private fun viewModel(canChooseLanguage: Boolean = true) =
        SettingsViewModel(preferences = repository, canChooseLanguage = canChooseLanguage)
}

private class FakeAppPreferencesRepository : AppPreferencesRepository {

    private val state = MutableStateFlow(AppPreferences())

    fun set(preferences: AppPreferences) {
        state.value = preferences
    }

    override fun observePreferences(): Flow<AppPreferences> = state.asStateFlow()

    override suspend fun current(): AppPreferences = state.first()

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        state.update { it.copy(language = language) }
    }

    override suspend fun setAdvancedMode(enabled: Boolean) {
        state.update { it.copy(advancedMode = enabled) }
    }
}
