package com.testscanner.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual class DatabaseBuilderFactory {

    @OptIn(ExperimentalForeignApi::class)
    actual fun create(): RoomDatabase.Builder<ScanDatabase> {
        // Documents y no Caches: el historial es dato del usuario, no algo que el sistema pueda
        // borrar cuando le haga falta espacio.
        val documents: NSURL = requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            ),
        ) { "No se pudo resolver el directorio Documents" }

        return Room.databaseBuilder(
            name = requireNotNull(documents.path) + "/" + ScanDatabase.FILE_NAME,
        )
    }
}

/** Ver la nota en `commonMain`: aquí `Dispatchers.IO` sí resuelve. */
internal actual val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
