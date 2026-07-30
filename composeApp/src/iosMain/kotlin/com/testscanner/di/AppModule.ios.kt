package com.testscanner.di

import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/** Motores enlazados en el framework de iOS. Fase 3 añadirá aquí Vision y ZXing-cpp. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Ios }

    single<List<BarcodeScannerEngine>> {
        listOf(ManualInputScannerEngine())
    }

    // Fase 3: sustituir por el controlador real sobre AVCaptureDevice.
    single<PermissionController> { AlwaysGrantedPermissionController() }
}
