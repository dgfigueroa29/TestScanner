package com.whyscan.core.domain

import com.whyscan.core.domain.model.EngineStatus
import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.ScanRequest
import com.whyscan.core.model.ScanSource
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.CameraControlEngine
import com.whyscan.core.scanner.EngineAvailability
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.ScannerCapabilities
import com.whyscan.core.scanner.ScannerEngineDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Motor controlable para los tests del dominio.
 *
 * Que esta clase sea suficiente para probar selección y fallback es la evidencia de que el SPI
 * aísla bien la plataforma: no hay cámara, ni dispositivo, ni Compose en ninguno de esos tests.
 */
class FakeScannerEngine(
    override val id: ScannerEngineId,
    private val availability: EngineAvailability = EngineAvailability.Available,
    private val events: List<ScanEvent> = emptyList(),
    capabilities: ScannerCapabilities = defaultCapabilities(),
    platforms: Set<ScannerPlatform> = setOf(ScannerPlatform.Android),
) : BarcodeScannerEngine, CameraControlEngine {

    var scanInvocations: Int = 0
        private set

    override val descriptor: ScannerEngineDescriptor = ScannerEngineDescriptor(
        id = id,
        displayName = id.id,
        vendor = "test",
        description = "motor de prueba",
        platforms = platforms,
        capabilities = capabilities,
        plannedPhase = 1,
        strength = "-",
        limitation = "-",
    )

    override suspend fun availability(): EngineAvailability = availability

    // Implementa el control de cámara porque sus capacidades por defecto lo declaran, y la suite de
    // contrato exige que lo declarado tenga a alguien que lo cumpla. No hace nada: es un fake.
    override suspend fun setTorch(enabled: Boolean) = Unit

    override suspend fun setZoomRatio(ratio: Float) = Unit

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        scanInvocations++
        emit(ScanEvent.SessionStarted(id))
        events.forEach { emit(it) }
        emit(ScanEvent.SessionEnded(id))
    }

    fun status(installed: Boolean = true): EngineStatus = EngineStatus(
        descriptor = descriptor,
        availability = availability,
        installed = installed,
    )

    companion object {
        fun defaultCapabilities(
            formats: Set<BarcodeFormat> = BarcodeFormat.all,
            sources: Set<ScanSource> = setOf(ScanSource.LiveCamera),
            continuous: Boolean = true,
            multiple: Boolean = true,
            torch: Boolean = true,
        ) = ScannerCapabilities(
            supportedFormats = formats,
            sources = sources,
            supportsContinuousScan = continuous,
            supportsMultipleCodes = multiple,
            supportsTorch = torch,
        )

        fun detection(
            engineId: ScannerEngineId,
            value: String = "hola",
            format: BarcodeFormat = BarcodeFormat.QrCode,
            atMillis: Long = 1_000L,
        ): Detection = Detection.of(
            barcode = Barcode(rawValue = value, format = format),
            engineId = engineId,
            detectedAtMillis = atMillis,
        )
    }
}
