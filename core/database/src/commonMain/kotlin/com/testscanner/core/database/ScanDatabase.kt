package com.testscanner.core.database

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {

    /** Más reciente primero: es el orden en que el historial tiene sentido para el usuario. */
    @Query("SELECT * FROM detections ORDER BY detectedAtMillis DESC")
    fun observeAll(): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun findById(id: String): DetectionEntity?

    /**
     * `REPLACE` y no `ABORT`: el id de una detección es determinista (motor + instante + valor), así
     * que en modo continuo el mismo código puede reinsertarse. Reemplazar es idempotente; abortar
     * obligaría a la capa de arriba a distinguir un caso que no le interesa.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detection: DetectionEntity)

    @Query("DELETE FROM detections")
    suspend fun clear()

    /** Poda el historial para que no crezca sin límite en un dispositivo del usuario. */
    @Query(
        """
        DELETE FROM detections
        WHERE id NOT IN (
            SELECT id FROM detections ORDER BY detectedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}

@Database(entities = [DetectionEntity::class], version = 1)
@ConstructedBy(ScanDatabaseConstructor::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao

    companion object {
        const val FILE_NAME = "testscanner.db"
    }
}

/**
 * Room genera el `actual` de este objeto por plataforma. La declaración `expect` es lo que permite
 * que la base de datos viva en `commonMain` y no en tres implementaciones paralelas.
 */
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object ScanDatabaseConstructor : RoomDatabaseConstructor<ScanDatabase> {
    override fun initialize(): ScanDatabase
}
