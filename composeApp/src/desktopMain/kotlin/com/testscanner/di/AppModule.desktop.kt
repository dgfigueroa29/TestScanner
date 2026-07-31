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
import com.testscanner.core.platform.FileSaver
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.engines.zxingjava.ZXingJavaEngine
import com.testscanner.platform.DesktopFileSaver
import com.testscanner.platform.DesktopImagePicker
import com.testscanner.platform.DesktopPlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

/**
 * Motores enlazados en el binario de escritorio.
 *
 * ZXing-cpp no está y no va a estar: no publica artefacto JVM (ADR-0008). Su hueco lo cubre el
 * ZXing original en Java, que es otro motor y se declara como tal.
 */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Desktop }

    single { ZXingJavaEngine() }
    single { ManualInputScannerEngine() }

    // ZXing primero: decodifica archivos, así que atiende lo que el usuario elige de disco. La
    // entrada manual cierra la cadena, como en las otras tres plataformas.
    single<List<BarcodeScannerEngine>> {
        listOf(
            get<ZXingJavaEngine>(),
            get<ManualInputScannerEngine>(),
        )
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

    // Exportar el historial (RF-11).
    single<FileSaver> { DesktopFileSaver() }
}
