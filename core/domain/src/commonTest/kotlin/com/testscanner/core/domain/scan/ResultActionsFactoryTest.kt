package com.testscanner.core.domain.scan

import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.BarcodeValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultActionsFactoryTest {

    private fun barcode(
        value: String,
        type: BarcodeValueType,
        format: BarcodeFormat = BarcodeFormat.QrCode,
    ) = Barcode(rawValue = value, format = format, valueType = type)

    private fun openAction(barcode: Barcode) = ResultActionsFactory
        .actionsFor(barcode, canShare = true)
        .filterIsInstance<ResultAction.Open>()
        .singleOrNull()

    @Test
    fun `copiar siempre esta disponible`() {
        val actions = ResultActionsFactory.actionsFor(
            barcode("cualquier cosa", BarcodeValueType.Text("cualquier cosa")),
            canShare = false,
        )

        assertTrue(ResultAction.Copy in actions)
    }

    @Test
    fun `sin hoja de compartir no se ofrece compartir`() {
        // Un botón que no hace nada es peor que no tener botón.
        val actions = ResultActionsFactory.actionsFor(
            barcode("x", BarcodeValueType.Text("x")),
            canShare = false,
        )

        assertTrue(ResultAction.Share !in actions)
    }

    @Test
    fun `una URL se puede abrir`() {
        val action = openAction(barcode("https://a.b", BarcodeValueType.Url("https://a.b")))

        assertEquals("https://a.b", action?.uri)
        assertEquals("Abrir enlace", action?.label)
    }

    @Test
    fun `un telefono se abre con el esquema tel aunque el codigo no lo traiga`() {
        // Es lo que aporta el parseo semántico: el valor crudo no es abrible, el interpretado sí.
        val action = openAction(barcode("+34600111222", BarcodeValueType.Phone("+34600111222")))

        assertEquals("tel:+34600111222", action?.uri)
        assertEquals("Llamar", action?.label)
    }

    @Test
    fun `un email conserva el asunto al abrirse`() {
        val action = openAction(
            barcode("x", BarcodeValueType.Email(address = "a@b.com", subject = "Pedido")),
        )

        assertEquals("mailto:a@b.com?subject=Pedido", action?.uri)
    }

    @Test
    fun `una coordenada se abre en el mapa`() {
        val action = openAction(
            barcode("x", BarcodeValueType.GeoPoint(latitude = 41.38, longitude = 2.16)),
        )

        assertEquals("geo:41.38,2.16", action?.uri)
    }

    @Test
    fun `un producto no se abre porque el codigo no contiene ningun destino`() {
        // Mandarlo a un buscador sería inventar información que el código no trae.
        val action = openAction(
            barcode(
                value = "4006381333931",
                type = BarcodeValueType.Product("4006381333931", checksumValid = true),
                format = BarcodeFormat.Ean13,
            ),
        )

        assertEquals(null, action)
    }

    @Test
    fun `un texto plano solo se copia o comparte`() {
        val actions = ResultActionsFactory.actionsFor(
            barcode("hola", BarcodeValueType.Text("hola")),
            canShare = true,
        )

        assertEquals(listOf(ResultAction.Copy, ResultAction.Share), actions)
    }

    @Test
    fun `el WiFi se comparte legible y no como QR crudo`() {
        // Pegarle a alguien "WIFI:T:WPA;S:...;;" no le sirve de nada.
        val text = ResultActionsFactory.shareableText(
            barcode(
                value = "WIFI:T:WPA;S:MiRed;P:clave;;",
                type = BarcodeValueType.Wifi(
                    ssid = "MiRed",
                    password = "clave",
                    encryption = BarcodeValueType.WifiEncryption.WPA,
                ),
            ),
        )

        assertEquals("Red: MiRed · Clave: clave", text)
    }

    @Test
    fun `un contacto se comparte con sus datos en una linea`() {
        val text = ResultActionsFactory.shareableText(
            barcode(
                value = "BEGIN:VCARD...",
                type = BarcodeValueType.ContactInfo(
                    formattedName = "Ada Lovelace",
                    organization = "Analytical Engines",
                    phones = listOf("+34600111222"),
                    emails = listOf("ada@ejemplo.com"),
                ),
            ),
        )

        assertEquals("Ada Lovelace · Analytical Engines · +34600111222 · ada@ejemplo.com", text)
    }

    @Test
    fun `para todo lo demas se comparte el valor crudo`() {
        val text = ResultActionsFactory.shareableText(
            barcode("https://a.b", BarcodeValueType.Url("https://a.b")),
        )

        assertEquals("https://a.b", text)
    }

    @Test
    fun `abrir aparece primero, porque es la accion principal`() {
        val actions = ResultActionsFactory.actionsFor(
            barcode("https://a.b", BarcodeValueType.Url("https://a.b")),
            canShare = true,
        )

        assertTrue(actions.first() is ResultAction.Open)
    }
}
