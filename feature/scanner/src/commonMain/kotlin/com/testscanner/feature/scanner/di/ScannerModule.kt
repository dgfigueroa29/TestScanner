package com.testscanner.feature.scanner.di

import com.testscanner.feature.scanner.ScannerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scannerFeatureModule: Module = module {
    viewModelOf(::ScannerViewModel)
}
