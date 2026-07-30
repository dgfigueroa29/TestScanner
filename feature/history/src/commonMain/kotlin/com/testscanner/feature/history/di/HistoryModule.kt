package com.testscanner.feature.history.di

import com.testscanner.feature.history.HistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val historyFeatureModule: Module = module {
    viewModelOf(::HistoryViewModel)
}
