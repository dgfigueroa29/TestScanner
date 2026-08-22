package com.whyscan.core.domain.scan

import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.BarcodeValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BarcodeValueParserTest {

    private fun parse(value: String, format: BarcodeFormat = BarcodeFormat.QrCode) =
        BarcodeValueParser.parse(value, format)

    @Test
    fun `reconoce una URL`() {
        val parsed = assertIs<BarcodeValueType.Url>(parse("https://anthropic.com/scanner"))
        assertEquals("https://anthropic.com/scanner", parsed.url)
    }

    @Test
    fun `normaliza una URL sin esquema`() {
        val parsed = assertIs<BarcodeValueType.Url>(parse("www.ejemplo.com"))
        assertEquals("https://www.ejemplo.com", parsed.url)
    }

    @Test
    fun `reconoce una red WiFi`() {
        val parsed = assertIs<BarcodeValueType.Wifi>(
            parse("WIFI:T:WPA;S:MiRed;P:clave-secreta;H:true;;"),
        )
        assertEquals("MiRed", parsed.ssid)
        assertEquals("clave-secreta", parsed.password)
        assertEquals(BarcodeValueType.WifiEncryption.WPA, parsed.encryption)
        assertTrue(parsed.hidden)
    }

    @Test
    fun `una red WiFi abierta no tiene contrasena`() {
        val parsed = assertIs<BarcodeValueType.Wifi>(parse("WIFI:T:nopass;S:Invitados;;"))
        assertEquals(BarcodeValueType.WifiEncryption.OPEN, parsed.encryption)
        assertEquals(null, parsed.password)
    }

    @Test
    fun `respeta el escape de punto y coma en el SSID`() {
        // Un SSID puede contener ';' legítimamente: partir por el separador sin más lo rompería.
        val parsed = assertIs<BarcodeValueType.Wifi>(parse("""WIFI:T:WPA;S:Red\;Rara;P:abc;;"""))
        assertEquals("Red;Rara", parsed.ssid)
        assertEquals("abc", parsed.password)
    }

    @Test
    fun `reconoce un email con asunto`() {
        val parsed = assertIs<BarcodeValueType.Email>(
            parse("mailto:hola@ejemplo.com?subject=Pedido&body=Texto"),
        )
        assertEquals("hola@ejemplo.com", parsed.address)
        assertEquals("Pedido", parsed.subject)
        assertEquals("Texto", parsed.body)
    }

    @Test
    fun `reconoce un telefono`() {
        assertEquals(BarcodeValueType.Phone("+34600123456"), parse("tel:+34600123456"))
    }

    @Test
    fun `reconoce un SMS con mensaje`() {
        val parsed = assertIs<BarcodeValueType.Sms>(parse("SMSTO:+34600123456:Voy de camino"))
        assertEquals("+34600123456", parsed.number)
        assertEquals("Voy de camino", parsed.message)
    }

    @Test
    fun `reconoce una coordenada`() {
        val parsed = assertIs<BarcodeValueType.GeoPoint>(parse("geo:41.3874,2.1686?q=Barcelona"))
        assertEquals(41.3874, parsed.latitude)
        assertEquals(2.1686, parsed.longitude)
        assertEquals("Barcelona", parsed.label)
    }

    @Test
    fun `reconoce una vCard`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            ORG:Analytical Engines
            TITLE:Matemática
            TEL:+34600111222
            EMAIL:ada@ejemplo.com
            END:VCARD
        """.trimIndent()

        val parsed = assertIs<BarcodeValueType.ContactInfo>(parse(vcard))
        assertEquals("Ada Lovelace", parsed.formattedName)
        assertEquals("Analytical Engines", parsed.organization)
        assertEquals(listOf("+34600111222"), parsed.phones)
        assertEquals(listOf("ada@ejemplo.com"), parsed.emails)
    }

    @Test
    fun `reconoce un evento de calendario`() {
        val event = """
            BEGIN:VEVENT
            SUMMARY:Demo de WhyScan
            DTSTART:20260801T100000Z
            DTEND:20260801T110000Z
            LOCATION:Remoto
            END:VEVENT
        """.trimIndent()

        val parsed = assertIs<BarcodeValueType.CalendarEvent>(parse(event))
        assertEquals("Demo de WhyScan", parsed.summary)
        assertEquals("Remoto", parsed.location)
    }

    @Test
    fun `un formato de producto se interpreta como GTIN y valida el checksum`() {
        val parsed = assertIs<BarcodeValueType.Product>(
            parse("4006381333931", BarcodeFormat.Ean13),
        )
        assertEquals("4006381333931", parsed.gtin)
        assertTrue(parsed.checksumValid)
    }

    @Test
    fun `un producto con checksum invalido se marca como tal en lugar de rechazarse`() {
        // Se conserva el valor leído: puede ser un código mal impreso, y esa es información útil.
        val parsed = assertIs<BarcodeValueType.Product>(
            parse("4006381333932", BarcodeFormat.Ean13),
        )
        assertTrue(!parsed.checksumValid)
    }

    @Test
    fun `el texto sin estructura reconocible queda como texto`() {
        assertEquals(BarcodeValueType.Text("cualquier cosa"), parse("cualquier cosa"))
    }
}
