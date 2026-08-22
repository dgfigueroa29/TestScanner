package com.whyscan.engines.manual

import com.whyscan.core.model.BarcodeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualCodeInterpreterTest {

    @Test
    fun `infiere EAN-13 cuando el checksum es valido`() {
        assertEquals(BarcodeFormat.Ean13, ManualCodeInterpreter.inferFormat("4006381333931"))
    }

    @Test
    fun `infiere EAN-8 cuando el checksum es valido`() {
        assertEquals(BarcodeFormat.Ean8, ManualCodeInterpreter.inferFormat("96385074"))
    }

    @Test
    fun `infiere UPC-A cuando el checksum es valido`() {
        assertEquals(BarcodeFormat.UpcA, ManualCodeInterpreter.inferFormat("036000291452"))
    }

    @Test
    fun `no afirma una simbologia de producto si el checksum falla`() {
        // 13 dígitos con checksum inválido no son un EAN-13: son texto que lo parece.
        assertEquals(BarcodeFormat.QrCode, ManualCodeInterpreter.inferFormat("4006381333932"))
    }

    @Test
    fun `el texto no numerico se trata como QR`() {
        assertEquals(BarcodeFormat.QrCode, ManualCodeInterpreter.inferFormat("https://a.b"))
    }

    @Test
    fun `recorta los espacios del valor tecleado`() {
        val barcode = ManualCodeInterpreter.interpret("  4006381333931  ")
        assertEquals("4006381333931", barcode?.rawValue)
    }

    @Test
    fun `un valor en blanco no produce codigo`() {
        assertNull(ManualCodeInterpreter.interpret("   "))
    }
}
