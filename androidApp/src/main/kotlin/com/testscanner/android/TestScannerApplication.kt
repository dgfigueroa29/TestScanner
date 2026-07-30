package com.testscanner.android

import android.app.Application
import com.testscanner.di.initKoin
import org.koin.android.ext.koin.androidContext

/**
 * Arranca el grafo de dependencias con el `Context` de la aplicación.
 *
 * Es el único motivo por el que Android necesita una clase `Application`: los motores de cámara
 * requieren un Context, y debe ser el de aplicación y no el de una Activity para que no quede
 * retenido tras una rotación.
 */
class TestScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TestScannerApplication)
        }
    }
}
