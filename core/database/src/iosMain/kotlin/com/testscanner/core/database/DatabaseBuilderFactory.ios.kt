package com.testscanner.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

/**
 * Ver la nota en `commonMain`. Aquí `Dispatchers.IO` sí resuelve, pero **solo con el import de
 * arriba**: en Kotlin/Native `IO` no es miembro de `Dispatchers` —ese es `internal`— sino una
 * extensión declarada en `concurrentMain`, y una extensión no viaja con el import del receptor.
 *
 * `import kotlinx.coroutines.IO` parece un import sin usar y no lo es: quitarlo devuelve el
 * `Cannot access 'val IO': it is internal` que tumbó el job de iOS. Mover la declaración de
 * `commonMain` a cada plataforma era necesario pero no suficiente.
 */
internal actual val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
