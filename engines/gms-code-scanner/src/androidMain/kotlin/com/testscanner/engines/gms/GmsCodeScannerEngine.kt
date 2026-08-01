// El `*` sobre un array es obligado: la API del SDK recibe `vararg` y no hay sobrecarga
// que acepte una colección. La copia que señala detekt la impone la firma ajena.
@file:Suppress("SpreadOperator")

package com.testscanner.engines.gms

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.testscanner.core.model.Barcode
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanError
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.BarcodeScannerEngine
import com.testscanner.core.scanner.EngineAvailability
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.ScannerEngineDescriptor
import com.testscanner.core.scanner.SystemTimeProvider
import com.testscanner.core.scanner.TimeProvider
import com.testscanner.core.scanner.catalog.ScannerEngineCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode

/**
 * Escáner del sistema (Google Play Services).
 *
 * Su rasgo distintivo es que **el escaneo ocurre fuera de la app**: Play Services abre su propia
 * pantalla, y por eso no necesita el permiso de cámara. A cambio no admite overlay, ni linterna, ni
 * modo continuo — capacidades que el descriptor declara como ausentes para que el selector
 * simplemente no lo elija cuando la petición las exige, en lugar de fallar en tiempo de ejecución.
 *
 * Es intrínsecamente *one-shot*: se adapta al SPI emitiendo
 * `SessionStarted → Detected → SessionEnded`.
 */
class GmsCodeScannerEngine(
    private val context: Context,
    private val time: TimeProvider = SystemTimeProvider,
) : BarcodeScannerEngine {

    override val id: ScannerEngineId = ScannerEngineId.GmsCodeScanner

    override val descriptor: ScannerEngineDescriptor = ScannerEngineCatalog.gmsCodeScanner

    /**
     * Depende de que Play Services esté presente y actualizado. Es la razón principal por la que
     * la cadena de fallback existe: en dispositivos sin Play Services este motor nunca arranca, y
     * la app debe seguir escaneando igualmente.
     */
    override suspend fun availability(): EngineAvailability {
        val status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)

        return when (status) {
            ConnectionResult.SUCCESS -> EngineAvailability.Available
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> EngineAvailability.Unsupported(
                "Google Play Services necesita actualizarse",
            )

            else -> EngineAvailability.Unsupported("Google Play Services no está disponible")
        }
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.SessionStarted(id))
        val startedAtMillis = time.nowMillis()

        val result = runCatching { startScan(request) }

        result
            .onSuccess { mlKitBarcode ->
                val detection = mlKitBarcode?.toDetection(startedAtMillis)
                if (detection == null) {
                    emit(ScanEvent.Failed(ScanError.Cancelled, engineId = id))
                } else {
                    emit(ScanEvent.Detected(listOf(detection)))
                }
            }
            .onFailure { throwable ->
                emit(
                    ScanEvent.Failed(
                        error = ScanError.EngineUnavailable(
                            engineId = id,
                            reason = throwable.message ?: "El escáner del sistema falló",
                        ),
                        engineId = id,
                    ),
                )
            }

        emit(ScanEvent.SessionEnded(id))
    }

    private suspend fun startScan(request: ScanRequest): MlKitBarcode? {
        val options = GmsBarcodeScannerOptions.Builder()
            .apply {
                MlKitFormatMapper.toMlKitFormats(request.formats)?.let { formats ->
                    setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
                }
            }
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)

        return suspendCancellableCoroutine { continuation ->
            scanner.startScan()
                .addOnSuccessListener { barcode -> continuation.resume(barcode) }
                // Cancelar es lo que ocurre cuando el usuario cierra la pantalla del sistema: no
                // es un error, es una sesión sin resultado.
                .addOnCanceledListener { continuation.resume(null) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    private fun MlKitBarcode.toDetection(startedAtMillis: Long): Detection? {
        val value = rawValue ?: return null
        val now = time.nowMillis()

        return Detection.of(
            barcode = Barcode(
                rawValue = value,
                format = MlKitFormatMapper.fromMlKit(format),
                rawBytes = rawBytes,
            ),
            engineId = id,
            detectedAtMillis = now,
            latencyMillis = now - startedAtMillis,
        )
    }
}
