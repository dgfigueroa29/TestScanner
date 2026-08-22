package com.whyscan.core.database

import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectionEntityTest {

    private val detection = Detection(
        id = "id-1",
        barcode = Barcode(rawValue = "4006381333931", format = BarcodeFormat.Ean13),
        engineId = ScannerEngineId.MlKitCameraX,
        detectedAtMillis = 1_700_000_000_000,
        latencyMillis = 120,
        source = ScanSource.LiveCamera,
    )

    @Test
    fun `ida_y_vuelta_conserva_lo_que_el_historial_necesita`() {
        val restored = detection.toEntity().toDomain()

        assertEquals(detection.id, restored?.id)
        assertEquals(detection.barcode.rawValue, restored?.detection?.barcode?.rawValue)
        assertEquals(detection.barcode.format, restored?.detection?.barcode?.format)
        assertEquals(detection.engineId, restored?.detection?.engineId)
        assertEquals(detection.detectedAtMillis, restored?.detection?.detectedAtMillis)
        assertEquals(detection.latencyMillis, restored?.detection?.latencyMillis)
        assertEquals(detection.source, restored?.detection?.source)
    }

    @Test
    fun `guardar_una_deteccion_no_escribe_nota`() {
        // Es lo que hace correcto el `INSERT OR IGNORE` del DAO: si este mapeo pusiera `null`,
        // reinsertar la misma lectura borraría lo que el usuario hubiera escrito.
        assertNull(detection.toEntity().note)
    }

    @Test
    fun `la_nota_va_y_vuelve`() {
        val restored = detection.toEntity().copy(note = "factura de marzo").toDomain()

        assertEquals("factura de marzo", restored?.note)
    }

    @Test
    fun `una_nota_en_blanco_se_lee_como_ausencia_de_nota`() {
        // Una fila escrita por una versión anterior podría traer `""`. "Tiene nota" tiene que
        // significar lo mismo en las cuatro plataformas.
        val restored = detection.toEntity().copy(note = "   ").toDomain()

        assertNull(restored?.note)
    }

    @Test
    fun `los_enums_se_persisten_por_su_id_estable_no_por_su_nombre_de_Kotlin`() {
        // Renombrar una constante de Kotlin no debe invalidar el historial del usuario.
        val entity = detection.toEntity()
        assertEquals("mlkit_camerax", entity.engineId)
        assertEquals("EAN_13", entity.formatId)
    }

    @Test
    fun `una_fila_de_un_motor_que_ya_no_existe_se_ignora_en_lugar_de_romper_el_historial`() {
        val orphan = detection.toEntity().copy(engineId = "motor_eliminado")
        assertNull(orphan.toDomain())
    }

    @Test
    fun `un_formato_desconocido_se_conserva_por_nombre_en_lugar_de_perderse`() {
        val row = detection.toEntity().copy(formatId = "SIMBOLOGIA_FUTURA")
        val restored = row.toDomain()

        assertEquals(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA"), restored?.detection?.barcode?.format)
    }
}
