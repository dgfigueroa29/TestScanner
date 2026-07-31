package com.testscanner.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.testscanner.core.data.repository.SettingsScanPreferencesRepository
import com.testscanner.core.database.DatabaseBuilderFactory
import com.testscanner.core.database.RoomScanHistoryRepository
import com.testscanner.core.database.ScanDatabase
import com.testscanner.core.database.build
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.IosPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.engines.vision.VisionScannerEngine
import com.testscanner.platform.IosPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/** Motores enlazados en el framework de iOS. Falta ZXing-cpp, pendiente del riesgo R9 del SDD. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Ios }

    single { VisionScannerEngine() }
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(get<VisionScannerEngine>(), get<ManualInputScannerEngine>())
    }

    single<PermissionController> { IosPermissionController() }

    // Historial persistente: Room sobre el driver bundled, igual en las tres plataformas.
    single { DatabaseBuilderFactory().create().build() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { IosPlatformActions() }
}
