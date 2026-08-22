package com.whyscan.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import com.whyscan.core.data.repository.SettingsScanPreferencesRepository
import com.whyscan.core.database.DatabaseBuilderFactory
import com.whyscan.core.database.RoomScanHistoryRepository
import com.whyscan.core.database.ScanDatabase
import com.whyscan.core.database.buildBundled
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.IosPermissionController
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.engines.manual.ManualInputScannerEngine
import com.whyscan.engines.vision.VisionScannerEngine
import com.whyscan.engines.zxing.ZXingCppEngine
import com.whyscan.platform.IosFileSaver
import com.whyscan.platform.IosImagePicker
import com.whyscan.platform.IosPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/** Motores enlazados en el framework de iOS. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Ios }

    single { VisionScannerEngine() }
    single { ZXingCppEngine() }
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(
            get<VisionScannerEngine>(),
            get<ZXingCppEngine>(),
            get<ManualInputScannerEngine>(),
        )
    }

    single<PermissionController> { IosPermissionController() }

    // Historial persistente: Room sobre el driver bundled, igual en las tres plataformas.
    single { DatabaseBuilderFactory().create().buildBundled() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { IosPlatformActions() }

    // Escaneo desde imagen (RF-07).
    single<ImagePicker> { IosImagePicker() }

    // Exportar el historial (RF-11).
    single<FileSaver> { IosFileSaver() }
}
