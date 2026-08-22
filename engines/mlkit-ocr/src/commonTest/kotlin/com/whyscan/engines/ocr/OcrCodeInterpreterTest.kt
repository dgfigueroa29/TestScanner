package com.whyscan.engines.ocr

import com.whyscan.core.model.BarcodeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrCodeInterpreterTest {

    private fun lines(vararg text: String) = text.map { OcrLine(it) }

    @Test
    fun `lee el numero impreso bajo el codigo`() {
        val result = OcrCodeInterpreter.interpret(lines("7501234567893"))

        assertEquals(1, result.size)
        assertEquals("7501234567893", result.first().rawValue)
        assertEquals(BarcodeFormat.Ean13, result.first().format)
    }

    @Test
    fun `tolera los espacios con los que va impreso`() {
        // Bajo un EAN-13 real se lee "7 501234 567893", no la cadena seguida.
        val result = OcrCodeInterpreter.interpret(lines("7 501234 567893"))

        assertEquals(listOf("7501234567893"), result.map { it.rawValue })
    }

    @Test
    fun `un numero con checksum invalido no es un codigo`() {
        // Es la regla que sostiene todo el motor: sin ella, cualquier cifra del envase pasaría.
        val result = OcrCodeInterpreter.interpret(lines("7501234567890"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignora numeros del envase que no son codigos`() {
        val result = OcrCodeInterpreter.interpret(
            lines("Atencion al cliente 900 123 456", "Lote L4417", "Cons. pref. 12-2027"),
        )

        assertTrue(result.isEmpty(), "no debería inventar códigos: $result")
    }

    @Test
    fun `distingue las simbologias por longitud`() {
        val result = OcrCodeInterpreter.interpret(
            lines("12345670", "012345678905", "7501234567893", "12345678901231"),
        )

        assertEquals(
            listOf(
                BarcodeFormat.Ean8,
                BarcodeFormat.UpcA,
                BarcodeFormat.Ean13,
                BarcodeFormat.Itf,
            ),
            result.map { it.format },
        )
    }

    @Test
    fun `recupera un codigo con letras confundidas por digitos`() {
        // "O" por "0" y "S" por "5" son los errores clásicos sobre una etiqueta gastada.
        val result = OcrCodeInterpreter.interpret(lines("75O1234S67893"))

        assertEquals(listOf("7501234567893"), result.map { it.rawValue })
    }

    @Test
    fun `una lectura con sustituciones vale menos que una literal`() {
        val literal = OcrCodeInterpreter.interpret(lines("7501234567893")).first()
        val guessed = OcrCodeInterpreter.interpret(lines("75O1234S67893")).first()

        assertTrue(
            guessed.confidence!! < literal.confidence!!,
            "una conjetura no puede reportarse con la misma confianza que una lectura",
        )
    }

    @Test
    fun `las sustituciones no se mezclan con lo que ya se leyo bien`() {
        // Si la línea literal ya dio un código, probar variantes solo añadiría ruido peor puntuado.
        val result = OcrCodeInterpreter.interpret(lines("7501234567893", "l2345670"))

        assertEquals(listOf("7501234567893"), result.map { it.rawValue })
    }

    @Test
    fun `propaga la confianza que reporta la plataforma`() {
        val result = OcrCodeInterpreter.interpret(listOf(OcrLine("7501234567893", confidence = 0.8f)))

        assertEquals(0.8f, result.first().confidence)
    }

    @Test
    fun `no repite el mismo codigo leido en varias lineas`() {
        val result = OcrCodeInterpreter.interpret(lines("7501234567893", "7 501234 567893"))

        assertEquals(1, result.size)
    }

    @Test
    fun `un numero pegado a otro texto no se contamina`() {
        val result = OcrCodeInterpreter.interpret(lines("EAN7501234567893"))

        assertEquals(listOf("7501234567893"), result.map { it.rawValue })
    }

    @Test
    fun `una cadena mas larga que un GTIN no se recorta para que cuadre`() {
        // Recortar ventanas hasta encontrar un checksum válido convertiría el motor en un generador
        // de coincidencias: con módulo 10, una de cada diez ventanas pasa por azar.
        val result = OcrCodeInterpreter.interpret(lines("99975012345678930"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `no devuelve nada si no hay texto`() {
        assertTrue(OcrCodeInterpreter.interpret(emptyList()).isEmpty())
        assertTrue(OcrCodeInterpreter.interpret(lines("", "   ")).isEmpty())
    }
}
