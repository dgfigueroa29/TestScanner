package com.testscanner.core.scanner

import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerCapabilitiesTest {

    private val cameraOnly2d = ScannerCapabilities(
        supportedFormats = BarcodeFormat.twoDimensional,
        sources = setOf(ScanSource.LiveCamera),
        supportsContinuousScan = false,
        supportsMultipleCodes = false,
        supportsTorch = false,
    )

    @Test
    fun `satisface una peticion que cubre`() {
        val request = ScanRequest(formats = setOf(BarcodeFormat.QrCode))
        assertTrue(cameraOnly2d.satisfies(request))
    }

    @Test
    fun `no satisface si la fuente no coincide`() {
        val request = ScanRequest(
            formats = setOf(BarcodeFormat.QrCode),
            source = ScanSource.StaticImage,
        )
        assertFalse(cameraOnly2d.satisfies(request))
    }

    @Test
    fun `no satisface si no cubre ningun formato pedido`() {
        val request = ScanRequest(formats = setOf(BarcodeFormat.Ean13))
        assertFalse(cameraOnly2d.satisfies(request))
    }

    @Test
    fun `no satisface si se exige continuo y no lo soporta`() {
        val request = ScanRequest(formats = setOf(BarcodeFormat.QrCode), continuous = true)
        assertFalse(cameraOnly2d.satisfies(request))
    }

    @Test
    fun `no satisface si se exige linterna y no la soporta`() {
        val request = ScanRequest(
            formats = setOf(BarcodeFormat.QrCode),
            requireTorchControl = true,
        )
        assertFalse(cameraOnly2d.satisfies(request))
    }

    @Test
    fun `satisface con cobertura parcial de formatos`() {
        // Cubrir parte de lo pedido basta para ser viable; la UI avisa de lo que queda fuera.
        val request = ScanRequest(formats = setOf(BarcodeFormat.QrCode, BarcodeFormat.Ean13))
        assertTrue(cameraOnly2d.satisfies(request))
        assertEquals(setOf(BarcodeFormat.QrCode), cameraOnly2d.coveredFormats(request))
        assertEquals(setOf(BarcodeFormat.Ean13), cameraOnly2d.uncoveredFormats(request))
    }
}
