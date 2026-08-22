package com.whyscan.core.data.di

import com.whyscan.core.data.repository.ScannerEngineRepositoryImpl
import com.whyscan.core.data.repository.SettingsAppPreferencesRepository
import com.whyscan.core.domain.repository.AppPreferencesRepository
import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.domain.usecase.ClearScanHistoryUseCase
import com.whyscan.core.domain.usecase.DecodeImageUseCase
import com.whyscan.core.domain.usecase.ObserveScanHistoryUseCase
import com.whyscan.core.domain.usecase.SaveDetectionUseCase
import com.whyscan.core.domain.usecase.ScanSessions
import com.whyscan.core.domain.usecase.ScanSettings
import com.whyscan.core.domain.usecase.SelectScannerEngineUseCase
import com.whyscan.core.domain.usecase.StartComparisonUseCase
import com.whyscan.core.domain.usecase.StartScanSessionUseCase
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.SystemTimeProvider
import com.whyscan.core.scanner.TimeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Grafo de datos y dominio, común a las cuatro plataformas.
 *
 * Los motores concretos **no** se declaran aquí: los aporta cada plataforma a través de
 * `platformModule` (ver `:composeApp`), igual que el almacén del historial. Este módulo solo sabe
 * que existe una `List<BarcodeScannerEngine>` y un [ScannerPlatform] en el grafo.
 */
val dataModule: Module = module {

    single<TimeProvider> { SystemTimeProvider }

    single<ScannerEngineRepository> {
        ScannerEngineRepositoryImpl(
            platform = get<ScannerPlatform>(),
            installedEngines = get<List<BarcodeScannerEngine>>(),
        )
    }

    // Las preferencias de **app** —tema, idioma, modo avanzado— sí viven aquí, y la diferencia con
    // las de escaneo no es un descuido: `Settings` está en el grafo de las cuatro plataformas, así
    // que no hay nada específico que aportar y repetir esta línea cuatro veces solo daría cuatro
    // sitios donde olvidarse de cambiarla.
    single<AppPreferencesRepository> { SettingsAppPreferencesRepository(get()) }

    // Ni `ScanPreferencesRepository` ni `ScanHistoryRepository` se declaran aquí: los aporta
    // cada `platformModule`, porque su
    // almacén es específico de plataforma. Las preferencias persisten en las cuatro
    // (multiplatform-settings); el historial solo en tres, porque Room KMP no soporta wasmJs y en
    // Web es de sesión. Esa diferencia queda visible en el wiring en lugar de escondida tras un
    // expect/actual que fingiera que todas las plataformas hacen lo mismo.
}

/** Casos de uso. Separado de [dataModule] para poder sustituir repositorios en tests sin tocarlos. */
val domainModule: Module = module {
    factory { SelectScannerEngineUseCase(get()) }
    factory { StartScanSessionUseCase(get(), get()) }
    factory { StartComparisonUseCase(get(), get()) }
    factory { DecodeImageUseCase(get(), get()) }
    factory { SaveDetectionUseCase(get()) }
    factory { ObserveScanHistoryUseCase(get()) }
    factory { ClearScanHistoryUseCase(get()) }

    // Agrupadores: una sola dependencia para quien usa varios de los de arriba a la vez (D16).
    factory { ScanSettings(get()) }
    factory { ScanSessions(get(), get(), get()) }
}
