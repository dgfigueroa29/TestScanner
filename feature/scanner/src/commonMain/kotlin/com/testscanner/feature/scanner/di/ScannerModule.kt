package com.testscanner.feature.scanner.di

import com.testscanner.feature.scanner.EnginePreviewResolver
import com.testscanner.feature.scanner.ScannerViewModel
import com.testscanner.feature.scanner.comparison.ComparisonViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scannerFeatureModule: Module = module {
    factory { EnginePreviewResolver(get()) }
    viewModelOf(::ScannerViewModel)
    viewModelOf(::ComparisonViewModel)
}
