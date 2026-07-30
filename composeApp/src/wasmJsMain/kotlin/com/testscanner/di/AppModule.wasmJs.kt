package com.testscanner.di

import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/** Motores enlazados en el bundle web. Fase 4 añadirá aquí el BarcodeDetector del navegador. */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Web }

    single<List<BarcodeScannerEngine>> {
        listOf(ManualInputScannerEngine())
    }

    // En web el permiso es implícito en getUserMedia(); se modelará al llegar el motor de cámara.
    single<PermissionController> { AlwaysGrantedPermissionController() }
}
