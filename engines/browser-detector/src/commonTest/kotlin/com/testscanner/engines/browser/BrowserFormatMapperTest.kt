package com.testscanner.engines.browser

import com.testscanner.core.model.BarcodeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserFormatMapperTest {

    @Test
    fun `traduce los nombres de la API del navegador`() {
        assertEquals(BarcodeFormat.QrCode, BrowserFormatMapper.fromBrowser("qr_code"))
        assertEquals(BarcodeFormat.Ean13, BrowserFormatMapper.fromBrowser("ean_13"))
        assertEquals(BarcodeFormat.Pdf417, BrowserFormatMapper.fromBrowser("pdf417"))
    }

    @Test
    fun `un formato desconocido se conserva en vez de perderse`() {
        // Si un navegador añade una simbología, verla en el historial vale más que descartarla.
        val format = BrowserFormatMapper.fromBrowser("rmqr")

        assertEquals(BarcodeFormat.Unknown("RMQR"), format)
    }

    @Test
    fun `el filtro es la lista de nombres que entiende el navegador`() {
        val filter = BrowserFormatMapper.toBrowserFilter(
            setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13),
        )

        assertEquals("ean_13,qr_code", filter)
    }

    @Test
    fun `si algun formato pedido no existe en la API no se filtra nada`() {
        // Filtrar con una lista incompleta haría que el motor dejara de ver códigos que sí sabe
        // leer. Se detecta todo y el dominio recorta después, igual en los ocho motores.
        val filter = BrowserFormatMapper.toBrowserFilter(
            setOf(BarcodeFormat.QrCode, BarcodeFormat.MicroQrCode),
        )

        assertNull(filter)
    }

    @Test
    fun `pedirlo todo tampoco produce filtro`() {
        assertNull(BrowserFormatMapper.toBrowserFilter(BarcodeFormat.all))
    }

    @Test
    fun `la traduccion es reversible para los formatos que la API conoce`() {
        val supported = listOf(
            BarcodeFormat.Aztec, BarcodeFormat.Codabar, BarcodeFormat.Code39, BarcodeFormat.Code93,
            BarcodeFormat.Code128, BarcodeFormat.DataMatrix, BarcodeFormat.Ean8, BarcodeFormat.Ean13,
            BarcodeFormat.Itf, BarcodeFormat.Pdf417, BarcodeFormat.QrCode, BarcodeFormat.UpcA,
            BarcodeFormat.UpcE,
        )

        supported.forEach { format ->
            val name = BrowserFormatMapper.toBrowserFilter(setOf(format))
            assertTrue(name != null, "$format debería tener nombre en la API")
            assertEquals(format, BrowserFormatMapper.fromBrowser(name), "ida y vuelta de $format")
        }
    }
}
