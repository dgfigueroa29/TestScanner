package com.testscanner.di

import com.testscanner.core.data.di.dataModule
import com.testscanner.core.data.di.domainModule
import com.testscanner.feature.scanner.di.scannerFeatureModule
import org.koin.core.module.Module

/**
 * Composition root.
 *
 * [platformModule] es el único punto de todo el proyecto que sabe qué motores existen en cada
 * plataforma. Un motor nuevo se enchufa ahí y en ningún sitio más (RNF-07).
 */
expect fun platformModule(): Module

fun appModules(): List<Module> = listOf(
    platformModule(),
    dataModule,
    domainModule,
    scannerFeatureModule,
)
