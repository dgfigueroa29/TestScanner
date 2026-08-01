package com.testscanner.core.domain.usecase

import com.testscanner.core.domain.repository.ScanPreferences
import com.testscanner.core.model.Detection
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.scanner.ScanEvent
import kotlinx.coroutines.flow.Flow

/**
 * Escanear y quedarse con lo leído, como **un solo colaborador** (deuda D16).
 *
 * Agrupa los tres casos de uso que el ViewModel usaba siempre juntos —arrancar una sesión en vivo,
 * decodificar una imagen y persistir lo detectado—. A diferencia de los de preferencias, estos
 * **no** se borraron: cada uno tiene lógica propia y sus tests. Lo que se agrupa es su uso.
 *
 * Se lleva además la traducción de [ScanPreferences] a [ScanRequest], que antes vivía en el
 * ViewModel. Ahí era una regla de dominio escondida en la UI y sin test propio; aquí se comprueba
 * sin levantar un ViewModel ni un dispatcher.
 */
class ScanSessions(
    private val startSession: StartScanSessionUseCase,
    private val decodeImage: DecodeImageUseCase,
    private val saveDetection: SaveDetectionUseCase,
) {

    /** Sesión en vivo con los ajustes actuales del usuario. */
    fun start(preferences: ScanPreferences): Flow<ScanEvent> = startSession(
        ScanRequest(
            formats = preferences.formats,
            source = sourceFor(preferences.preferredEngineId),
            continuous = preferences.continuous,
            allowMultiple = preferences.allowMultiple,
        ),
        preferences.preferredEngineId,
    )

    /**
     * Decodifica una imagen ya capturada (RF-07).
     *
     * `allowMultiple` va fijo a `true` y no se lee de las preferencias: en una foto los códigos ya
     * están todos ahí, y descartar los demás por un ajuste pensado para el vídeo en vivo solo haría
     * perder trabajo ya hecho.
     */
    suspend fun decode(image: ScanImage, preferences: ScanPreferences): Result<List<Detection>> =
        decodeImage(
            image,
            ScanRequest(
                formats = preferences.formats,
                source = ScanSource.StaticImage,
                allowMultiple = true,
            ),
            preferences.preferredEngineId,
        )

    /** Persiste lo detectado en el historial (RF-11). */
    suspend fun save(detections: List<Detection>) = detections.forEach { saveDetection(it) }

    /**
     * La entrada manual no consume frames de cámara. Sin esto, el selector descartaría el motor
     * manual por no soportar la fuente pedida justo cuando es el único disponible.
     */
    private fun sourceFor(engineId: ScannerEngineId?): ScanSource =
        if (engineId == ScannerEngineId.ManualInput) ScanSource.ManualInput else ScanSource.LiveCamera
}
