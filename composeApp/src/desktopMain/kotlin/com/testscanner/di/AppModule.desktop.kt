package com.testscanner.di

import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
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
}
