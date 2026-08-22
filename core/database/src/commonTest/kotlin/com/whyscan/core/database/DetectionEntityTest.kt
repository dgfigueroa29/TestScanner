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
        assertEquals(detection.barcode.rawValue, restored?.barcode?.rawValue)
        assertEquals(detection.barcode.format, restored?.barcode?.format)
        assertEquals(detection.engineId, restored?.engineId)
        assertEquals(detection.detectedAtMillis, restored?.detectedAtMillis)
        assertEquals(detection.latencyMillis, restored?.latencyMillis)
        assertEquals(detection.source, restored?.source)
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

        assertEquals(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA"), restored?.barcode?.format)
    }
}
