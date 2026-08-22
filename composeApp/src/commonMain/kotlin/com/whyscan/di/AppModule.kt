package com.whyscan.di

import com.whyscan.core.data.di.dataModule
import com.whyscan.core.data.di.domainModule
import com.whyscan.feature.history.di.historyFeatureModule
import com.whyscan.feature.scanner.di.scannerFeatureModule
import com.whyscan.feature.settings.di.settingsFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatformTools

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
    historyFeatureModule,
    settingsFeatureModule,
)

/**
 * Arranca el grafo. Cada punto de entrada de plataforma lo llama antes de pintar nada: Android
 * necesita entregar su `Context`, y las demás plataformas no tienen nada que aportar.
 *
 * Es idempotente porque en iOS el `UIViewController` puede recrearse sin que el proceso muera, y
 * un segundo `startKoin` lanzaría.
 */
fun initKoin(configure: KoinApplication.() -> Unit = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    startKoin {
        configure()
        modules(appModules())
    }
}
