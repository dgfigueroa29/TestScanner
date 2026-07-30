package com.testscanner.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Cada plataforma sabe dónde vive su archivo de base de datos; el resto de la construcción es
 * idéntico y se centraliza en [build].
 */
expect class DatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<ScanDatabase>
}

/**
 * Se usa el driver **bundled** y no el del sistema para que las cuatro plataformas corran la misma
 * versión de SQLite. Con el driver del sistema, una consulta podría comportarse distinto en Android
 * 24 que en iOS 17 — y este proyecto existe para comparar plataformas, no para pelearse con ellas.
 */
fun RoomDatabase.Builder<ScanDatabase>.build(): ScanDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
