package com.testscanner.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.testscanner.core.data.repository.SettingsScanPreferencesRepository
import com.testscanner.core.database.DatabaseBuilderFactory
import com.testscanner.core.database.RoomScanHistoryRepository
import com.testscanner.core.database.ScanDatabase
import com.testscanner.core.database.build
import com.testscanner.core.domain.repository.ScanHistoryRepository
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.gms.GmsCodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.engines.mlkit.MlKitCameraXEngine
import com.testscanner.engines.ocr.MlKitOcrEngine
import com.testscanner.platform.AndroidPlatformActions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Motores enlazados en el binario de Android.
 *
 * El orden de esta lista no decide nada: la prioridad la fija `EnginePriorityPolicy` en el dominio,
 * a partir de datos y no de accidentes de cableado.
 */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Android }

    // El análisis de frames no puede correr en el hilo principal: bloquearía la UI justo mientras
    // la cámara produce imágenes.
    single<ExecutorService> { Executors.newSingleThreadExecutor() }

    single { GmsCodeScannerEngine(androidContext()) }
    single { MlKitCameraXEngine(context = androidContext(), analysisExecutor = get()) }
    single { MlKitOcrEngine(context = androidContext(), analysisExecutor = get()) }
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(
            get<GmsCodeScannerEngine>(),
            get<MlKitCameraXEngine>(),
            get<MlKitOcrEngine>(),
            get<ManualInputScannerEngine>(),
        )
    }

    single { AndroidPermissionController(androidContext()) }
    single<PermissionController> { get<AndroidPermissionController>() }

    // Historial persistente: Room sobre el driver bundled, igual en las tres plataformas.
    single { DatabaseBuilderFactory(androidContext()).create().build() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> { SharedPreferencesSettings(
            androidContext().getSharedPreferences("testscanner", Context.MODE_PRIVATE),
        ) }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { AndroidPlatformActions(androidContext()) }
}
