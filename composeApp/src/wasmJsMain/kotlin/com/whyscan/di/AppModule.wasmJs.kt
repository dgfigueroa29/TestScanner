package com.whyscan.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import com.whyscan.core.data.repository.SettingsScanHistoryRepository
import com.whyscan.core.data.repository.SettingsScanPreferencesRepository
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.AlwaysGrantedPermissionController
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.engines.browser.BrowserDetectorEngine
import com.whyscan.engines.manual.ManualInputScannerEngine
import com.whyscan.platform.WebFileSaver
import com.whyscan.platform.WebImagePicker
import com.whyscan.platform.WebPlatformActions
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

    // Room KMP no tiene target wasmJs, así que el historial se guarda como JSON en el almacén de
    // la plataforma —`localStorage`, el mismo que ya usan las preferencias—. Guarda exactamente los
    // mismos campos que la tabla de Room, de modo que el historial de Web y el de las otras tres
    // plataformas contienen lo mismo (deuda D9).
    single<ScanHistoryRepository> { SettingsScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { StorageSettings() }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { WebPlatformActions() }

    // Escaneo desde imagen (RF-07).
    single<ImagePicker> { WebImagePicker() }

    // Exportar el historial (RF-11).
    single<FileSaver> { WebFileSaver() }
}
