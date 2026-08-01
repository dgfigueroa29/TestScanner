package com.testscanner.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Cada plataforma sabe dónde vive su archivo de base de datos; el resto de la construcción es
 * idéntico y se centraliza en [build].
 */
expect class DatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<ScanDatabase>
}

/**
 * El hilo donde corren las consultas: siempre `Dispatchers.IO`, pero nombrado por plataforma.
 *
 * Escribir `Dispatchers.IO` aquí, en `commonMain`, **no compila para iOS**, y el motivo no es
 * obvio: en el artefacto JVM de coroutines, `IO` es un miembro público del objeto `Dispatchers`,
 * mientras que en el nativo el miembro es `internal` y lo público es una extensión declarada en
 * `concurrentMain`. Desde `commonMain` esa extensión no está en el ámbito —el `commonMain` de
 * coroutines no declara `IO`— así que la única candidata es el miembro interno, y el compilador
 * responde `Cannot access 'val IO': it is internal`. En Android y Escritorio la misma línea
 * compilaba porque ahí el miembro sí es público; por eso el fallo esperó al primer runner macOS.
 *
 * La salida es pedirlo desde cada plataforma, donde la extensión sí es visible. No hay
 * `wasmJs` en la lista porque Room no soporta ese target (SDD §11).
 */
internal expect val queryDispatcher: CoroutineDispatcher

/**
 * Se usa el driver **bundled** y no el del sistema para que las cuatro plataformas corran la misma
 * versión de SQLite. Con el driver del sistema, una consulta podría comportarse distinto en Android
 * 24 que en iOS 17 — y este proyecto existe para comparar plataformas, no para pelearse con ellas.
 */
fun RoomDatabase.Builder<ScanDatabase>.build(): ScanDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(queryDispatcher)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
