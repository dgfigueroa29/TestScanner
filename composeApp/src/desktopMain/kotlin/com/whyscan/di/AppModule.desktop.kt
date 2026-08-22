package com.whyscan.di

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.whyscan.core.data.repository.SettingsScanPreferencesRepository
import com.whyscan.core.database.DatabaseBuilderFactory
import com.whyscan.core.database.RoomScanHistoryRepository
import com.whyscan.core.database.ScanDatabase
import com.whyscan.core.database.buildBundled
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.AlwaysGrantedPermissionController
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.engines.manual.ManualInputScannerEngine
import com.whyscan.engines.zxingjava.ZXingJavaEngine
import com.whyscan.platform.DesktopFileSaver
import com.whyscan.platform.DesktopImagePicker
import com.whyscan.platform.DesktopPlatformActions
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
    single { DatabaseBuilderFactory().create().buildBundled() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("whyscan")) }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { DesktopPlatformActions() }

    // Escaneo desde imagen (RF-07).
    single<ImagePicker> { DesktopImagePicker() }

    // Exportar el historial (RF-11).
    single<FileSaver> { DesktopFileSaver() }
}
