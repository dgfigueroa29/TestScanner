package com.testscanner.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

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
