package com.testscanner.core.database

import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
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
    fun `ida y vuelta conserva lo que el historial necesita`() {
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
    fun `los enums se persisten por su id estable, no por su nombre de Kotlin`() {
        // Renombrar una constante de Kotlin no debe invalidar el historial del usuario.
        val entity = detection.toEntity()
        assertEquals("mlkit_camerax", entity.engineId)
        assertEquals("EAN_13", entity.formatId)
    }

    @Test
    fun `una fila de un motor que ya no existe se ignora en lugar de romper el historial`() {
        val orphan = detection.toEntity().copy(engineId = "motor_eliminado")
        assertNull(orphan.toDomain())
    }

    @Test
    fun `un formato desconocido se conserva por nombre en lugar de perderse`() {
        val row = detection.toEntity().copy(formatId = "SIMBOLOGIA_FUTURA")
        val restored = row.toDomain()

        assertEquals(BarcodeFormat.Unknown("SIMBOLOGIA_FUTURA"), restored?.barcode?.format)
    }
}
