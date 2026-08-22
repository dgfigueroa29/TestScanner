package com.whyscan.feature.scanner.di

import com.whyscan.feature.scanner.EnginePreviewResolver
import com.whyscan.feature.scanner.ResultActionRunner
import com.whyscan.feature.scanner.ScannerViewModel
import com.whyscan.feature.scanner.comparison.ComparisonViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val scannerFeatureModule: Module = module {
    factory { EnginePreviewResolver(get()) }
    factory { ResultActionRunner(get()) }
    viewModelOf(::ScannerViewModel)
    viewModelOf(::ComparisonViewModel)
}
