package com.testscanner.core.data.di

import com.testscanner.core.data.repository.ScannerEngineRepositoryImpl
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.domain.usecase.ClearScanHistoryUseCase
import com.testscanner.core.domain.usecase.DecodeImageUseCase
import com.testscanner.core.domain.usecase.ObserveEngineCatalogUseCase
import com.testscanner.core.domain.usecase.ObserveScanHistoryUseCase
import com.testscanner.core.domain.usecase.ObserveScanPreferencesUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.SelectScannerEngineUseCase
import com.testscanner.core.domain.usecase.SetPreferredEngineUseCase
import com.testscanner.core.domain.usecase.SetScanFormatsUseCase
import com.testscanner.core.domain.usecase.StartComparisonUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TimeProvider
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
    factory { ObserveEngineCatalogUseCase(get()) }
    factory { ObserveScanPreferencesUseCase(get()) }
    factory { SetPreferredEngineUseCase(get()) }
    factory { SetScanFormatsUseCase(get()) }
    factory { StartScanSessionUseCase(get(), get()) }
    factory { StartComparisonUseCase(get(), get()) }
    factory { DecodeImageUseCase(get(), get()) }
    factory { SaveDetectionUseCase(get()) }
    factory { ObserveScanHistoryUseCase(get()) }
    factory { ClearScanHistoryUseCase(get()) }
}
