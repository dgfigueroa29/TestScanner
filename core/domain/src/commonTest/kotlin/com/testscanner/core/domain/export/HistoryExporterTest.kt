package com.testscanner.core.domain.export

import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.BarcodeValueType
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScannerEngineId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryExporterTest {

    private fun detection(
        value: String,
        format: BarcodeFormat = BarcodeFormat.QrCode,
        valueType: BarcodeValueType = BarcodeValueType.Text(value),
        engineId: ScannerEngineId = ScannerEngineId.MlKitCameraX,
        latency: Long? = 42L,
        confidence: Float? = null,
    ) = Detection.of(
        barcode = Barcode(
            rawValue = value,
            format = format,
            valueType = valueType,
            confidence = confidence,
        ),
        engineId = engineId,
        detectedAtMillis = 1_700_000_000_000L,
        latencyMillis = latency,
    )

    private fun csv(vararg detections: Detection) =
        HistoryExporter.export(detections.toList(), ExportFormat.Csv)

    private fun json(vararg detections: Detection) =
        HistoryExporter.export(detections.toList(), ExportFormat.Json)

    @Test
    fun `el_CSV_empieza_por_la_cabecera_aunque_no_haya_nada_que_exportar`() {
        // Un archivo vacío del todo no se distingue de una exportación fallida.
        val result = HistoryExporter.export(emptyList(), ExportFormat.Csv)

        assertEquals("value,format,engine,detected_at,latency_ms,value_type,confidence", result.trim())
    }

    @Test
    fun `una_fila_lleva_el_valor_el_formato_y_el_motor_que_lo_leyo`() {
        val lines = csv(detection("hola")).trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("hola,QR_CODE,mlkit_camerax,1700000000000,42,"), lines[1])
    }

    @Test
    fun `un_valor_con_comas_se_entrecomilla`() {
        val line = csv(detection("BEGIN:VCARD,N:Perez")).trim().lines()[1]

        assertTrue(line.startsWith("\"BEGIN:VCARD,N:Perez\""), line)
    }

    @Test
    fun `unas_comillas_dentro_del_valor_se_duplican`() {
        // Es la regla de RFC 4180; sin ella el campo se corta a mitad al abrirlo.
        val line = csv(detection("dice \"hola\"")).trim().lines()[1]

        assertTrue(line.startsWith("\"dice \"\"hola\"\"\""), line)
    }

    @Test
    fun `un_valor_con_salto_de_linea_no_parte_la_fila`() {
        val result = csv(detection("linea1\nlinea2"))

        assertTrue(result.contains("\"linea1\nlinea2\""), result)
    }

    @Test
    fun `un_valor_que_parece_formula_se_neutraliza`() {
        // Un QR con `=HYPERLINK(...)` dentro sería código ejecutándose en la máquina de quien abra
        // el archivo. El contenido de un código escaneado viene de fuera y no es de fiar.
        val line = csv(detection("=HYPERLINK(\"http://malo\")")).trim().lines()[1]

        assertTrue(line.startsWith("\"'=HYPERLINK"), line)
    }

    @Test
    fun `tambien_se_neutralizan_los_otros_arranques_de_formula`() {
        listOf("+1", "-1", "@SUM(A1)").forEach { value ->
            val line = csv(detection(value)).trim().lines()[1]
            assertTrue(line.startsWith("'"), "no se protegió '$value': $line")
        }
    }

    @Test
    fun `un_valor_normal_no_se_toca`() {
        val line = csv(detection("7501234567893")).trim().lines()[1]

        assertTrue(line.startsWith("7501234567893,"), line)
    }

    @Test
    fun `una_latencia_ausente_deja_la_celda_vacia_en_vez_de_un_cero`() {
        // Cero significa "instantáneo"; vacío significa "no se midió". No es lo mismo.
        val line = csv(detection("hola", latency = null)).trim().lines()[1]

        assertTrue(line.contains(",1700000000000,,"), line)
    }

    @Test
    fun `el_JSON_conserva_el_valor_crudo_sin_la_proteccion_del_CSV`() {
        // En JSON no hay nada que ejecute nada, así que no hay motivo para alterar el dato.
        val result = json(detection("=HYPERLINK(\"http://malo\")"))

        assertTrue(result.contains("\\\"http://malo\\\""), result)
        assertTrue(!result.contains("'="), result)
    }

    @Test
    fun `el_JSON_dice_cuantas_detecciones_deberia_traer`() {
        val result = json(detection("a"), detection("b"))

        assertTrue(result.contains("\"count\": 2"), result)
    }

    @Test
    fun `el_JSON_reporta_el_tipo_semantico_del_valor`() {
        val result = json(
            detection("https://a.b", valueType = BarcodeValueType.Url("https://a.b")),
        )

        assertTrue(result.contains("\"valueType\": \"URL\""), result)
    }

    @Test
    fun `el_JSON_omite_la_confianza_cuando_el_motor_no_la_reporta`() {
        val result = json(detection("hola"))

        assertTrue(result.contains("\"confidence\": null"), result)
    }

    @Test
    fun `el_JSON_incluye_la_confianza_del_OCR`() {
        val result = json(
            detection("7501234567893", engineId = ScannerEngineId.MlKitOcr, confidence = 0.8f),
        )

        assertTrue(result.contains("\"confidence\": 0.8"), result)
    }

    @Test
    fun `el_tipo_semantico_se_exporta_con_un_id_propio_y_no_con_el_nombre_de_la_clase`() {
        // `::class.simpleName` devolvería el nombre ofuscado en una build con R8, y el archivo
        // exportado diría "a" en vez de "URL".
        assertEquals("URL", BarcodeValueType.Url("https://a.b").id)
        assertEquals("TEXT", BarcodeValueType.Text("hola").id)
    }

    @Test
    fun `los_nombres_de_archivo_llevan_la_extension_del_formato`() {
        assertTrue(HistoryExporter.fileName(ExportFormat.Csv).endsWith(".csv"))
        assertTrue(HistoryExporter.fileName(ExportFormat.Json).endsWith(".json"))
    }

    @Test
    fun `cada_formato_declara_su_tipo_MIME`() {
        assertEquals("text/csv", ExportFormat.Csv.mimeType)
        assertEquals("application/json", ExportFormat.Json.mimeType)
    }
}
