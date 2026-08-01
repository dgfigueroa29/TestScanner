package com.testscanner.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<ScanDatabase> {
        // `applicationContext` y no el que llegue: la base vive más que cualquier Activity.
        val appContext = context.applicationContext
        return Room.databaseBuilder(
            context = appContext,
            name = appContext.getDatabasePath(ScanDatabase.FILE_NAME).absolutePath,
        )
    }
}

/** Ver la nota en `commonMain`: aquí `Dispatchers.IO` sí resuelve. */
internal actual val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
