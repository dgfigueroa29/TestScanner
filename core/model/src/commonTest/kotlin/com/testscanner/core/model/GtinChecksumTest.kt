package com.testscanner.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GtinChecksumTest {

    @Test
    fun `acepta un EAN-13 real`() {
        assertTrue(GtinChecksum.isValid("4006381333931"))
    }

    @Test
    fun `acepta un EAN-8 real`() {
        assertTrue(GtinChecksum.isValid("96385074"))
    }

    @Test
    fun `acepta un UPC-A real`() {
        assertTrue(GtinChecksum.isValid("036000291452"))
    }

    @Test
    fun `rechaza un EAN-13 con el digito de control cambiado`() {
        assertFalse(GtinChecksum.isValid("4006381333932"))
    }

    @Test
    fun `rechaza longitudes que no son GTIN`() {
        assertFalse(GtinChecksum.isValid("12345"))
        assertFalse(GtinChecksum.isValid("123456789012345"))
    }

    @Test
    fun `rechaza valores no numericos`() {
        assertFalse(GtinChecksum.isValid("400638133393X"))
    }

    @Test
    fun `calcula el digito de control esperado`() {
        assertEquals(1, GtinChecksum.checkDigit("400638133393"))
        assertEquals(4, GtinChecksum.checkDigit("9638507"))
    }
}
