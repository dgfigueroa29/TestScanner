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
    fun `el CSV empieza por la cabecera aunque no haya nada que exportar`() {
        // Un archivo vacío del todo no se distingue de una exportación fallida.
        val result = HistoryExporter.export(emptyList(), ExportFormat.Csv)

        assertEquals("value,format,engine,detected_at,latency_ms,value_type,confidence", result.trim())
    }

    @Test
    fun `una fila lleva el valor, el formato y el motor que lo leyo`() {
        val lines = csv(detection("hola")).trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("hola,QR_CODE,mlkit_camerax,1700000000000,42,"), lines[1])
    }

    @Test
    fun `un valor con comas se entrecomilla`() {
        val line = csv(detection("BEGIN:VCARD,N:Perez")).trim().lines()[1]

        assertTrue(line.startsWith("\"BEGIN:VCARD,N:Perez\""), line)
    }

    @Test
    fun `unas comillas dentro del valor se duplican`() {
        // Es la regla de RFC 4180; sin ella el campo se corta a mitad al abrirlo.
        val line = csv(detection("dice \"hola\"")).trim().lines()[1]

        assertTrue(line.startsWith("\"dice \"\"hola\"\"\""), line)
    }

    @Test
    fun `un valor con salto de linea no parte la fila`() {
        val result = csv(detection("linea1\nlinea2"))

        assertTrue(result.contains("\"linea1\nlinea2\""), result)
    }

    @Test
    fun `un valor que parece formula se neutraliza`() {
        // Un QR con `=HYPERLINK(...)` dentro sería código ejecutándose en la máquina de quien abra
        // el archivo. El contenido de un código escaneado viene de fuera y no es de fiar.
        val line = csv(detection("=HYPERLINK(\"http://malo\")")).trim().lines()[1]

        assertTrue(line.startsWith("\"'=HYPERLINK"), line)
    }

    @Test
    fun `tambien se neutralizan los otros arranques de formula`() {
        listOf("+1", "-1", "@SUM(A1)").forEach { value ->
            val line = csv(detection(value)).trim().lines()[1]
            assertTrue(line.startsWith("'"), "no se protegió '$value': $line")
        }
    }

    @Test
    fun `un valor normal no se toca`() {
        val line = csv(detection("7501234567893")).trim().lines()[1]

        assertTrue(line.startsWith("7501234567893,"), line)
    }

    @Test
    fun `una latencia ausente deja la celda vacia en vez de un cero`() {
        // Cero significa "instantáneo"; vacío significa "no se midió". No es lo mismo.
        val line = csv(detection("hola", latency = null)).trim().lines()[1]

        assertTrue(line.contains(",1700000000000,,"), line)
    }

    @Test
    fun `el JSON conserva el valor crudo sin la proteccion del CSV`() {
        // En JSON no hay nada que ejecute nada, así que no hay motivo para alterar el dato.
        val result = json(detection("=HYPERLINK(\"http://malo\")"))

        assertTrue(result.contains("\\\"http://malo\\\""), result)
        assertTrue(!result.contains("'="), result)
    }

    @Test
    fun `el JSON dice cuantas detecciones deberia traer`() {
        val result = json(detection("a"), detection("b"))

        assertTrue(result.contains("\"count\": 2"), result)
    }

    @Test
    fun `el JSON reporta el tipo semantico del valor`() {
        val result = json(
            detection("https://a.b", valueType = BarcodeValueType.Url("https://a.b")),
        )

        assertTrue(result.contains("\"valueType\": \"Url\""), result)
    }

    @Test
    fun `el JSON omite la confianza cuando el motor no la reporta`() {
        val result = json(detection("hola"))

        assertTrue(result.contains("\"confidence\": null"), result)
    }

    @Test
    fun `el JSON incluye la confianza del OCR`() {
        val result = json(
            detection("7501234567893", engineId = ScannerEngineId.MlKitOcr, confidence = 0.8f),
        )

        assertTrue(result.contains("\"confidence\": 0.8"), result)
    }

    @Test
    fun `los nombres de archivo llevan la extension del formato`() {
        assertTrue(HistoryExporter.fileName(ExportFormat.Csv).endsWith(".csv"))
        assertTrue(HistoryExporter.fileName(ExportFormat.Json).endsWith(".json"))
    }

    @Test
    fun `cada formato declara su tipo MIME`() {
        assertEquals("text/csv", ExportFormat.Csv.mimeType)
        assertEquals("application/json", ExportFormat.Json.mimeType)
    }
}
