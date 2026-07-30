package com.testscanner.di

import com.testscanner.core.model.ScannerPlatform
import com.testscanner.core.permissions.AlwaysGrantedPermissionController
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.engines.manual.ManualInputScannerEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Motores enlazados en el binario de Android.
 *
 * Fase 2 añadirá aquí `GmsCodeScannerEngine()` y `MlKitCameraXEngine()`. Hasta entonces el
 * catálogo los muestra como `NotImplemented` con su fase, en lugar de ocultarlos.
 */
actual fun platformModule(): Module = module {
    single { ScannerPlatform.Android }

    single<List<BarcodeScannerEngine>> {
        listOf(ManualInputScannerEngine())
    }

    // Fase 2: sustituir por el controlador real basado en ActivityResultContracts.
    single<PermissionController> { AlwaysGrantedPermissionController() }
}
