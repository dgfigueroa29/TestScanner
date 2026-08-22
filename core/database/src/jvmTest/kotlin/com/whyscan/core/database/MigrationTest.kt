package com.whyscan.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Salda la deuda **D22**: nada ejecutaba la migración de la base de datos.
 *
 * ## Qué cubría Room y qué no
 *
 * `@AutoMigration(from = 1, to = 2)` la genera y la valida el procesador **en compilación**, contra
 * los esquemas exportados a `schemas/`. Eso es bastante: garantiza que el SQL es correcto y que el
 * esquema resultante coincide con el que declara el código.
 *
 * Lo que no garantiza es lo único que le importa al usuario: **que sus datos sigan ahí después**. Un
 * esquema correcto es perfectamente compatible con haber borrado la tabla y haberla vuelto a crear,
 * que es justo lo que hacía este proyecto hasta la versión anterior con
 * `fallbackToDestructiveMigration`. Un test que valida el esquema le habría dado el visto bueno.
 *
 * Por eso este test no mira el esquema: **escribe una fila en una base v1 de verdad y comprueba que
 * sigue estando después de abrirla con el código v2.**
 *
 * ## Por qué la v1 se construye a mano
 *
 * No queda ninguna copia del código v1 con la que crearla, así que se levanta con el SQL exacto que
 * Room exportó en su día a `schemas/…/1.json` —el `createSql` de la entidad, literal— más las dos
 * cosas que Room necesita para reconocer una base como suya: el `room_master_table` con el
 * `identityHash` de esa versión y el `PRAGMA user_version`. Si alguien tocara el esquema v1 a
 * posteriori, el hash dejaría de cuadrar y este test lo diría.
 *
 * Corre en `jvmTest` y no en `commonTest` porque necesita un archivo temporal del sistema. No es una
 * limitación: la migración es la misma en las tres plataformas que llevan Room, porque el SQL lo
 * genera el mismo procesador a partir del mismo esquema.
 */
class MigrationTest {

    private lateinit var directory: File

    private val databasePath: String get() = File(directory, "migracion.db").absolutePath

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("whyscan-migracion").toFile()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `una lectura guardada en la v1 sigue ahi despues de migrar a la v2`() = runTest {
        createVersion1Database("1")

        val database = openWithCurrentCode()
        val rows = database.detectionDao().observeAll().first()
        database.close()

        assertEquals(1, rows.size, "la migración perdió la fila")
        assertEquals("4006381333931", rows.single().rawValue)
        assertEquals("mlkit_camerax", rows.single().engineId)
    }

    @Test
    fun `la columna nueva llega vacia y no rompe el mapeo al dominio`() = runTest {
        createVersion1Database("1")

        val database = openWithCurrentCode()
        val entry = database.detectionDao().observeAll().first().single().toDomain()
        database.close()

        assertNull(entry?.note, "una fila de la v1 no puede traer nota")
        assertEquals("4006381333931", entry?.detection?.barcode?.rawValue)
    }

    @Test
    fun `despues de migrar se puede escribir una nota en una fila que venia de la v1`() = runTest {
        // La prueba de que la migración no dejó la tabla a medias: la columna existe y acepta datos.
        createVersion1Database("1")

        val database = openWithCurrentCode()
        database.detectionDao().setNote("1", "factura de marzo")
        val entry = database.detectionDao().observeAll().first().single().toDomain()
        database.close()

        assertEquals("factura de marzo", entry?.note)
    }

    @Test
    fun `varias filas sobreviven, no solo la primera`() = runTest {
        createVersion1Database("1", "2", "3")

        val database = openWithCurrentCode()
        val rows = database.detectionDao().observeAll().first()
        database.close()

        assertEquals(3, rows.size)
    }

    @Test
    fun `sobre una base que no existe todavia se crea la v2 directamente`() = runTest {
        // El otro camino que recorre todo el mundo: la instalación nueva. Sin migración de por
        // medio, pero por el mismo `buildBundled()`.
        val database = openWithCurrentCode()
        database.detectionDao().insertIgnoringDuplicates(
            DetectionEntity(
                id = "nueva",
                rawValue = "hola",
                formatId = "QR_CODE",
                engineId = "manual_input",
                sourceName = "ManualEntry",
                detectedAtMillis = 1,
                latencyMillis = null,
                note = "con nota desde el primer día",
            ),
        )
        val rows = database.detectionDao().observeAll().first()
        database.close()

        assertTrue(rows.single().note == "con nota desde el primer día")
    }

    /**
     * Levanta una base con **el esquema exacto de la versión 1** y las filas que se le pidan.
     *
     * @param ids identificador de cada fila. El resto de columnas se rellena con los mismos
     *   valores en todas: lo que se comprueba es que la fila sobreviva, no su contenido.
     */
    private fun createVersion1Database(vararg ids: String) {
        val connection = BundledSQLiteDriver().open(databasePath)
        try {
            connection.execSQL(VERSION_1_TABLE)
            connection.execSQL(ROOM_MASTER_TABLE)
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '$VERSION_1_IDENTITY_HASH')",
            )
            connection.execSQL("PRAGMA user_version = 1")

            ids.forEach { rowId ->
                connection.execSQL(
                    """
                    INSERT INTO detections
                        (id, rawValue, formatId, engineId, sourceName, detectedAtMillis, latencyMillis)
                    VALUES ('$rowId', '4006381333931', 'EAN_13', 'mlkit_camerax', 'LiveCamera',
                            1700000000000, 120)
                    """.trimIndent(),
                )
            }
        } finally {
            connection.close()
        }
    }

    /** El mismo `buildBundled()` que usa la app: driver bundled, dispatcher y política de migración. */
    private fun openWithCurrentCode(): ScanDatabase =
        Room.databaseBuilder<ScanDatabase>(name = databasePath).buildBundled()

    private companion object {
        /**
         * Copiado literal del `createSql` que hay en
         * `schemas/com.whyscan.core.database.ScanDatabase/1.json`, con su marcador de nombre de
         * tabla resuelto. Escribirlo a mano sería inventarse la versión 1 en lugar de usarla.
         */
        const val VERSION_1_TABLE =
            "CREATE TABLE IF NOT EXISTS `detections` (`id` TEXT NOT NULL, `rawValue` TEXT NOT NULL, " +
                "`formatId` TEXT NOT NULL, `engineId` TEXT NOT NULL, `sourceName` TEXT NOT NULL, " +
                "`detectedAtMillis` INTEGER NOT NULL, `latencyMillis` INTEGER, PRIMARY KEY(`id`))"

        /**
         * La tabla con la que Room reconoce una base como suya. Sin ella, abrir la base v1 no sería
         * una migración sino un archivo ajeno, y el test estaría comprobando otra cosa.
         */
        const val ROOM_MASTER_TABLE =
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"

        /** El del `1.json`. Si el esquema v1 cambiara a posteriori, este test lo diría. */
        const val VERSION_1_IDENTITY_HASH = "abb3cbcafcca83bacbc4154fafcf31f0"
    }
}
