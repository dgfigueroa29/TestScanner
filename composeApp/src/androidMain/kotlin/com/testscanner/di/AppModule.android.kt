package com.testscanner.di

import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AndroidPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.gms.GmsCodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import com.testscanner.engines.mlkit.MlKitCameraXEngine
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
    single { ManualInputScannerEngine() }

    single<List<BarcodeScannerEngine>> {
        listOf(
            get<GmsCodeScannerEngine>(),
            get<MlKitCameraXEngine>(),
            get<ManualInputScannerEngine>(),
        )
    }

    single { AndroidPermissionController(androidContext()) }
    single<PermissionController> { get<AndroidPermissionController>() }
}
