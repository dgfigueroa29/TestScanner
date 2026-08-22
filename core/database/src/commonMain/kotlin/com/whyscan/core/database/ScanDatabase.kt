package com.whyscan.core.database

import androidx.room.AutoMigration
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

    /**
     * `IGNORE` y no `REPLACE`, y el cambio **arregla un defecto**.
     *
     * El id de una detección es determinista —motor, instante y valor—, así que una fila en conflicto
     * es por construcción la misma lectura: todos sus campos de máquina coinciden. Antes se
     * reinsertaba con `REPLACE`, que en SQLite es un borrado seguido de un alta, y eso **se llevaba
     * por delante la nota del usuario**: bastaba con volver a leer el mismo código en el mismo
     * milisegundo para perderla. Ignorar el alta conserva la fila entera y es igual de idempotente.
     *
     * Es además lo que ya hacía el historial en memoria, que comprobaba el id antes de añadir. Las
     * tres implementaciones coinciden ahora en la misma regla.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(detection: DetectionEntity)

    /** `null` borra la nota. La fila puede no existir —podada o borrada—: entonces no hace nada. */
    @Query("UPDATE detections SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM detections")
    suspend fun clear()

    /**
     * Poda el historial para que no crezca sin límite en un dispositivo del usuario.
     *
     * **Las filas con nota no se podan.** Una nota es la señal más clara que da el usuario de que esa
     * lectura le importa; que la app la borre sola por antigüedad es perder datos que alguien se
     * molestó en escribir. El techo pasa a ser un techo de lecturas *sin anotar*, que es lo que
     * genera volumen: una sesión continua produce cientos de filas y ninguna nota.
     */
    @Query(
        """
        DELETE FROM detections
        WHERE note IS NULL
          AND id NOT IN (
            SELECT id FROM detections ORDER BY detectedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}

/**
 * ## Versión 2: la nota del usuario, migrando en lugar de borrando
 *
 * El salto de la 1 a la 2 solo añade una columna `note` que admite `null`, que es exactamente el
 * caso que `@AutoMigration` resuelve sola a partir de los esquemas exportados a
 * `core/database/schemas/`.
 *
 * Declararla no es ceremonia. Hasta esta versión la construcción de la base pedía
 * `fallbackToDestructiveMigration(dropAllTables = true)`, así que **el primer cambio de esquema
 * habría borrado el historial de todo el mundo sin decir nada** — en una app cuyo historial es el
 * único dato que el usuario acumula, y justo en la versión que le invita a anotarlo. Nunca llegó a
 * pasar porque nunca hubo una versión 2; esta es la que lo habría provocado.
 */
@Database(
    entities = [DetectionEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@ConstructedBy(ScanDatabaseConstructor::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao

    companion object {
        const val FILE_NAME = "whyscan.db"
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
