package com.testscanner.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.testscanner.core.data.repository.SettingsScanPreferencesRepository
import com.testscanner.core.database.DatabaseBuilderFactory
import com.testscanner.core.database.RoomScanHistoryRepository
import com.testscanner.core.database.ScanDatabase
import com.testscanner.core.database.build
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.platform.DesktopImagePicker
import com.testscanner.platform.DesktopPlatformActions
import java.util.prefs.Preferences
import org.koin.core.module.Module
import org.koin.dsl.module

/** Motores enlazados en el binario de escritorio. Fase 3 añadirá aquí ZXing-cpp. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Desktop }

    single<List<BarcodeScannerEngine>> {
        listOf(ManualInputScannerEngine())
    }

    // En escritorio el SO gestiona el acceso a la webcam al abrirla: no hay permiso que pedir.
    single<PermissionController> { AlwaysGrantedPermissionController() }

    // Historial persistente: Room sobre el driver bundled, igual en las tres plataformas.
    single { DatabaseBuilderFactory().create().build() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("testscanner")) }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { DesktopPlatformActions() }

    // Escaneo desde imagen (RF-07).
    single<ImagePicker> { DesktopImagePicker() }
}
