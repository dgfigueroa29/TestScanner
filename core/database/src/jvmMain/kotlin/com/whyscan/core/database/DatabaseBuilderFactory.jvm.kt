package com.whyscan.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

actual class DatabaseBuilderFactory {
    actual fun create(): RoomDatabase.Builder<ScanDatabase> {
        // En escritorio no hay un directorio de datos que el SO nos asigne, así que se usa el
        // convenio de cada plataforma bajo el home del usuario.
        val directory = File(System.getProperty("user.home"), APP_DIRECTORY).apply { mkdirs() }
        return Room.databaseBuilder(File(directory, ScanDatabase.FILE_NAME).absolutePath)
    }

    private companion object {
        const val APP_DIRECTORY = ".whyscan"
    }
}

/** Ver la nota en `commonMain`: aquí `Dispatchers.IO` sí resuelve. */
internal actual val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
