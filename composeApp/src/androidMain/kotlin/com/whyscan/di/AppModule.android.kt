package com.whyscan.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.whyscan.core.data.repository.SettingsScanPreferencesRepository
import com.whyscan.core.database.DatabaseBuilderFactory
import com.whyscan.core.database.RoomScanHistoryRepository
import com.whyscan.core.database.ScanDatabase
import com.whyscan.core.database.buildBundled
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.AndroidPermissionController
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.engines.gms.GmsCodeScannerEngine
import com.whyscan.engines.manual.ManualInputScannerEngine
import com.whyscan.engines.mlkit.MlKitCameraXEngine
import com.whyscan.engines.ocr.MlKitOcrEngine
import com.whyscan.engines.zxing.ZXingCppEngine
import com.whyscan.platform.AndroidFileSaver
import com.whyscan.platform.AndroidImagePicker
import com.whyscan.platform.AndroidPlatformActions
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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
    //
    // El tipo declarado es `Executor` y **no** `ExecutorService`, aunque la fábrica devuelva lo
    // segundo. Koin indexa cada definición por el tipo con el que se declara y resuelve por
    // igualdad exacta: no recorre supertipos. Los tres motores de cámara piden un `Executor` en su
    // constructor, así que registrarlo como `ExecutorService` dejaba ese `get()` sin nada que
    // encontrar y la app moría al componer la primera pantalla con
    // `NoDefinitionFoundException: No definition found for type 'java.util.concurrent.Executor'`.
    //
    // Nadie necesita la API de `ExecutorService` —no se apaga en ningún sitio, vive lo que vive el
    // proceso—, así que se declara exactamente lo que se consume en lugar de registrar los dos.
    single<Executor> { Executors.newSingleThreadExecutor() }

    single { GmsCodeScannerEngine(androidContext()) }
    single { MlKitCameraXEngine(context = androidContext(), analysisExecutor = get()) }
    single { MlKitOcrEngine(context = androidContext(), analysisExecutor = get()) }
    single { ZXingCppEngine(context = androidContext(), analysisExecutor = get()) }
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(
            get<GmsCodeScannerEngine>(),
            get<MlKitCameraXEngine>(),
            get<ZXingCppEngine>(),
            get<MlKitOcrEngine>(),
            get<ManualInputScannerEngine>(),
        )
    }

    single { AndroidPermissionController(androidContext()) }
    single<PermissionController> { get<AndroidPermissionController>() }

    // Historial persistente: Room sobre el driver bundled, igual en las tres plataformas.
    single { DatabaseBuilderFactory(androidContext()).create().buildBundled() }
    single { get<ScanDatabase>().detectionDao() }
    single<ScanHistoryRepository> { RoomScanHistoryRepository(get()) }

    // Preferencias persistentes: multiplatform-settings cubre las cuatro plataformas, así que
    // aquí no hay excepciones como sí las hay con el historial.
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("whyscan", Context.MODE_PRIVATE),
        )
    }
    single<ScanPreferencesRepository> { SettingsScanPreferencesRepository(get()) }

    // Acciones sobre el resultado (RF-13): copiar, compartir y abrir.
    single<PlatformActions> { AndroidPlatformActions(androidContext()) }

    // Escaneo desde imagen (RF-07). Se expone también por su tipo concreto porque la Activity le
    // presta su launcher, igual que hace con el controlador de permisos.
    single { AndroidImagePicker(androidContext()) }
    single<ImagePicker> { get<AndroidImagePicker>() }

    // Exportar el historial (RF-11): también toma prestado el launcher de la Activity.
    single { AndroidFileSaver(androidContext()) }
    single<FileSaver> { get<AndroidFileSaver>() }
}
