package com.testscanner.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import com.testscanner.core.data.repository.InMemoryScanHistoryRepository
import com.testscanner.core.data.repository.SettingsScanPreferencesRepository
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.browser.BrowserDetectorEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.platform.WebPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

/** Motores enlazados en el bundle web. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Web }

    single { BrowserDetectorEngine() }
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(
            get<BrowserDetectorEngine>(),
            get<ManualInputScannerEngine>(),
        )
    }

    // En el navegador el permiso lo pide `getUserMedia` al abrir la sesión y no hay forma fiable de
    // consultarlo antes, así que aquí se concede siempre y la denegación aparece donde el navegador
    // la produce de verdad: como un fallo de sesión del motor.
    single<PermissionController> { AlwaysGrantedPermissionController() }

    // Room KMP no tiene target wasmJs. El historial de Web es de sesión, y eso queda visible aquí
    // en lugar de escondido tras un actual que fingiera persistir.
    single<ScanHistoryRepository> { InMemoryScanHistoryRepository() }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { StorageSettings() }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { WebPlatformActions() }
}
