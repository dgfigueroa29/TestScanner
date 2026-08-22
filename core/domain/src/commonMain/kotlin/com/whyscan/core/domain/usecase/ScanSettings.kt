package com.whyscan.core.domain.usecase

import com.whyscan.core.domain.repository.ScanPreferences
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.ScannerEngineId
import kotlinx.coroutines.flow.Flow

/**
 * Los ajustes de escaneo, como **un solo colaborador**.
 *
 * ### Por qué existe (deuda D16)
 * El ViewModel de escaneo dependía de cuatro cosas para lo mismo: tres casos de uso de una línea
 * —observar, fijar motor, fijar formatos— y además el repositorio, porque `current()` y
 * `setContinuous()` no tenían caso de uso propio. Cuatro nombres para una sola idea.
 *
 * Los tres casos de uso se borraron en vez de envolverse: delegaban al repositorio sin añadir nada
 * salvo un nombre más largo. El único que sí tenía una regla —que un conjunto de formatos vacío
 * significa *sin filtro*, no *no escanear nada*— la trajo consigo, y vive en [setFormats].
 *
 * El catálogo de motores no pasó por aquí a propósito: no tiene ninguna regla que guardar, así que
 * quien lo necesita usa `ScannerEngineRepository` directamente y no hay una capa de más.
 */
class ScanSettings(
    private val repository: ScanPreferencesRepository,
) {

    fun observe(): Flow<ScanPreferences> = repository.observePreferences()

    suspend fun current(): ScanPreferences = repository.current()

    /** `null` vuelve a selección automática (RF-02). */
    suspend fun preferEngine(id: ScannerEngineId?) = repository.setPreferredEngine(id)

    /**
     * Cambia los formatos a detectar (RF-06).
     *
     * Quedarse sin ninguno seleccionado es una forma fácil de dejar la app inservible sin entender
     * por qué, así que un conjunto vacío se guarda como *todos*.
     */
    suspend fun setFormats(formats: Set<BarcodeFormat>) {
        repository.setFormats(formats.ifEmpty { BarcodeFormat.all })
    }

    suspend fun setContinuous(enabled: Boolean) = repository.setContinuous(enabled)
}
