package com.whyscan.feature.settings.di

import com.whyscan.core.designsystem.PlatformSupportsLanguageOverride
import com.whyscan.feature.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `viewModel { }` y no `viewModelOf(::SettingsViewModel)`: el segundo resuelve **todos** los
 * parámetros del constructor desde el grafo, y `canChooseLanguage` es un `Boolean`. Registrar un
 * `Boolean` suelto en Koin es pedir que dentro de dos meses otra cosa pida un booleano y se lleve
 * este por error.
 */
val settingsFeatureModule: Module = module {
    viewModel {
        SettingsViewModel(
            preferences = get(),
            canChooseLanguage = PlatformSupportsLanguageOverride,
        )
    }
}
