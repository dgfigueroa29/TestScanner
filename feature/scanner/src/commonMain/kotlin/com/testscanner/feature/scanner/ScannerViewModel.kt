package com.testscanner.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testscanner.core.domain.repository.ScanPreferencesRepository
import com.testscanner.core.domain.repository.ScannerEngineRepository
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.scan.ResultActionsFactory
import com.testscanner.core.domain.usecase.DecodeImageUseCase
import com.testscanner.core.domain.usecase.ObserveEngineCatalogUseCase
import com.testscanner.core.domain.usecase.ObserveScanPreferencesUseCase
import com.testscanner.core.domain.usecase.SaveDetectionUseCase
import com.testscanner.core.domain.usecase.SetPreferredEngineUseCase
import com.testscanner.core.domain.usecase.SetScanFormatsUseCase
import com.testscanner.core.domain.usecase.StartScanSessionUseCase
import com.testscanner.core.model.BarcodeFormat
import com.testscanner.core.model.Detection
import com.testscanner.core.model.Permission
import com.testscanner.core.model.ScanImage
import com.testscanner.core.model.ScanRequest
import com.testscanner.core.model.ScanSource
import com.testscanner.core.model.ScannerEngineId
import com.testscanner.core.permissions.PermissionController
import com.testscanner.core.platform.ImagePicker
import com.testscanner.core.platform.PickImageResult
import com.testscanner.core.platform.PlatformActions
import com.testscanner.core.scanner.CameraControlEngine
import com.testscanner.core.scanner.ScanEvent
import com.testscanner.core.scanner.TextInputEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI de la pantalla de escaneo.
 *
 * El ViewModel **no conoce ningún motor concreto**: pide una sesión al caso de uso y reacciona a
 * los [ScanEvent] que llegan. Toda la lógica de selección y degradación vive en el dominio, así
 * que añadir un motor nuevo no toca este archivo (RNF-07).
 */
class ScannerViewModel(
    private val observeCatalog: ObserveEngineCatalogUseCase,
    private val observePreferences: ObserveScanPreferencesUseCase,
    private val setPreferredEngine: SetPreferredEngineUseCase,
    private val setScanFormats: SetScanFormatsUseCase,
    private val startScanSession: StartScanSessionUseCase,
    private val saveDetection: SaveDetectionUseCase,
    private val decodeImage: DecodeImageUseCase,
    private val preferencesRepository: ScanPreferencesRepository,
    private val engineRepository: ScannerEngineRepository,
    private val permissionController: PermissionController,
    private val platformActions: PlatformActions,
    private val imagePicker: ImagePicker,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState(canShare = platformActions.canShare))
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ScannerEffect>()
    val effects: SharedFlow<ScannerEffect> = _effects.asSharedFlow()

    private var sessionJob: Job? = null

    init {
        observeCatalogChanges()
        observePreferenceChanges()
    }

    fun onAction(action: ScannerAction) {
        when (action) {
            ScannerAction.Refresh -> refresh()
            ScannerAction.StartSession -> startSession()
            ScannerAction.StopSession -> stopSession()
            is ScannerAction.SelectEngine -> selectEngine(action.id)
            is ScannerAction.ToggleFormat -> toggleFormat(action.format)
            is ScannerAction.SetContinuous -> setContinuous(action.enabled)
            is ScannerAction.ManualInputChanged -> _state.update { it.copy(manualInput = action.value) }
            ScannerAction.SubmitManualInput -> submitManualInput()
            ScannerAction.ScanFromImage -> scanFromImage()
            is ScannerAction.RunResultAction -> runResultAction(action.detection, action.action)
            ScannerAction.ToggleTorch -> toggleTorch()
            is ScannerAction.SetZoom -> setZoom(action.ratio)
            ScannerAction.RequestCameraPermission -> requestCameraPermission()
            ScannerAction.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun observeCatalogChanges() {
        viewModelScope.launch {
            observeCatalog().collect { catalog ->
                _state.update { it.copy(catalog = catalog, isLoading = false) }
            }
        }
    }

    private fun observePreferenceChanges() {
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                _state.update {
                    it.copy(
                        selectedEngineId = preferences.preferredEngineId,
                        formats = preferences.formats,
                        continuous = preferences.continuous,
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch { engineRepository.refresh() }
    }

    private fun selectEngine(id: ScannerEngineId?) {
        viewModelScope.launch {
            setPreferredEngine(id)
            if (_state.value.sessionStatus == SessionStatus.Scanning) {
                stopSession()
                startSession()
            }
        }
    }

    private fun toggleFormat(format: BarcodeFormat) {
        viewModelScope.launch {
            val current = _state.value.formats
            setScanFormats(if (format in current) current - format else current + format)
        }
    }

    private fun setContinuous(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setContinuous(enabled) }
    }

    private fun startSession() {
        sessionJob?.cancel()
        _state.update {
            it.copy(
                sessionStatus = SessionStatus.Starting,
                detections = emptyList(),
                switchedFrom = null,
                error = null,
            )
        }

        sessionJob = viewModelScope.launch {
            val preferences = preferencesRepository.current()
            val request = ScanRequest(
                formats = preferences.formats,
                source = sourceFor(preferences.preferredEngineId),
                continuous = preferences.continuous,
                allowMultiple = preferences.allowMultiple,
            )

            startScanSession(request, preferences.preferredEngineId).collect(::reduce)
        }
    }

    /**
     * La entrada manual no consume frames de cámara. Sin esto, el selector descartaría el motor
     * manual por no soportar la fuente pedida justo cuando es el único disponible.
     */
    private fun sourceFor(engineId: ScannerEngineId?): ScanSource =
        if (engineId == ScannerEngineId.ManualInput) ScanSource.ManualInput else ScanSource.LiveCamera

    private fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        _state.update {
            it.copy(
                sessionStatus = SessionStatus.Idle,
                activeEngineId = null,
                torchEnabled = false,
                zoomRatio = 1f,
            )
        }
    }

    private suspend fun reduce(event: ScanEvent) {
        when (event) {
            is ScanEvent.SessionStarted -> _state.update {
                it.copy(sessionStatus = SessionStatus.Scanning, activeEngineId = event.engineId)
            }

            is ScanEvent.Detected -> {
                event.detections.forEach { saveDetection(it) }
                _state.update { it.copy(detections = event.detections + it.detections) }
            }

            is ScanEvent.EngineSwitched -> {
                _state.update { it.copy(switchedFrom = event.from, activeEngineId = event.to) }
                _effects.emit(
                    ScannerEffect.ShowMessage(
                        "Se cambió a otro motor porque el anterior no pudo continuar",
                    ),
                )
            }

            is ScanEvent.Failed -> if (event.error.isFatal) {
                _state.update { it.copy(error = event.error, sessionStatus = SessionStatus.Finished) }
            } else {
                _state.update { it.copy(error = event.error) }
            }

            is ScanEvent.SessionEnded -> _state.update {
                it.copy(sessionStatus = SessionStatus.Finished, activeEngineId = null)
            }

            is ScanEvent.FrameAnalyzed -> Unit
        }
    }

    private fun submitManualInput() {
        val value = _state.value.manualInput
        if (value.isBlank()) return

        viewModelScope.launch {
            val engine = engineRepository.engine(ScannerEngineId.ManualInput)
            if (engine is TextInputEngine) {
                engine.submit(value)
                _state.update { it.copy(manualInput = "") }
            } else {
                _effects.emit(ScannerEffect.ShowMessage("La entrada manual no está disponible"))
            }
        }
    }

    /**
     * Escanea una imagen elegida por el usuario (RF-07).
     *
     * Detiene la sesión en vivo antes de abrir el selector: en Android la cámara y el selector del
     * sistema compiten por la pantalla, y dejarla corriendo detrás gastaría batería mientras el
     * usuario busca la foto.
     *
     * No pide permiso de galería a propósito. El selector moderno de cada plataforma —el *photo
     * picker* de Android, `PHPicker` en iOS, el diálogo de archivos en escritorio y web— corre
     * fuera de la app y devuelve solo lo que el usuario elige, así que **no hay nada que pedir**.
     * Es la misma ventaja que hace que el Google Code Scanner encabece la cadena en Android.
     */
    private fun scanFromImage() {
        if (_state.value.isDecodingImage) return

        stopSession()
        _state.update { it.copy(isDecodingImage = true, error = null) }

        viewModelScope.launch {
            try {
                when (val picked = imagePicker.pickImage()) {
                    is PickImageResult.Cancelled -> Unit

                    is PickImageResult.Failed ->
                        _effects.emit(ScannerEffect.ShowMessage(picked.reason))

                    is PickImageResult.Picked -> decodePickedImage(picked.image)
                }
            } finally {
                _state.update { it.copy(isDecodingImage = false) }
            }
        }
    }

    private suspend fun decodePickedImage(image: ScanImage) {
        val preferences = preferencesRepository.current()
        val request = ScanRequest(
            formats = preferences.formats,
            source = ScanSource.StaticImage,
            allowMultiple = true,
        )

        decodeImage(image, request, preferences.preferredEngineId)
            .onSuccess { detections ->
                if (detections.isEmpty()) {
                    _effects.emit(ScannerEffect.ShowMessage("No se encontró ningún código en la imagen"))
                    return@onSuccess
                }
                // La imagen es una sesión puntual: sus resultados sustituyen a los anteriores, igual
                // que al arrancar una sesión de cámara.
                _state.update {
                    it.copy(
                        detections = detections,
                        activeEngineId = detections.first().engineId,
                        sessionStatus = SessionStatus.Finished,
                    )
                }
                detections.forEach { saveDetection(it) }
            }
            .onFailure { failure ->
                _effects.emit(
                    ScannerEffect.ShowMessage(
                        failure.message ?: "No se pudo leer la imagen",
                    ),
                )
            }
    }

    /**
     * Ejecuta una acción sobre un resultado (RF-13).
     *
     * El dominio decide **qué** se puede hacer con el código y la plataforma **cómo**; el ViewModel
     * solo une las dos mitades y avisa si la acción no prosperó — el portapapeles puede estar
     * bloqueado y no abrirse ninguna app para un esquema.
     */
    private fun runResultAction(detection: Detection, action: ResultAction) {
        viewModelScope.launch {
            val text = ResultActionsFactory.shareableText(detection.barcode)

            val (succeeded, failureMessage) = when (action) {
                ResultAction.Copy ->
                    platformActions.copyToClipboard(text) to "No se pudo copiar al portapapeles"

                ResultAction.Share ->
                    platformActions.share(text) to "No se pudo abrir la hoja de compartir"

                is ResultAction.Open ->
                    platformActions.openUrl(action.uri) to "Ninguna app puede abrir esto"
            }

            // Compartir y abrir son visibles por sí mismos: aparece una hoja o cambia de app.
            // Copiar no muestra nada, así que es la única que necesita confirmación.
            val message = when {
                !succeeded -> failureMessage
                action == ResultAction.Copy -> "Copiado"
                else -> null
            }

            message?.let { _effects.emit(ScannerEffect.ShowMessage(it)) }
        }
    }

    /**
     * La linterna se pide a través de la capacidad opcional, no del motor concreto. Si el motor
     * activo no la implementa no pasa nada: la UI ya no muestra el control, y aquí el `as?` cierra
     * el caso sin excepciones.
     */
    private fun toggleTorch() {
        viewModelScope.launch {
            val control = cameraControlOfActiveEngine() ?: return@launch

            val enabled = !_state.value.torchEnabled
            control.setTorch(enabled)
            _state.update { it.copy(torchEnabled = enabled) }
        }
    }

    private fun setZoom(ratio: Float) {
        viewModelScope.launch {
            val control = cameraControlOfActiveEngine() ?: return@launch
            control.setZoomRatio(ratio)
            _state.update { it.copy(zoomRatio = ratio) }
        }
    }

    private fun cameraControlOfActiveEngine(): CameraControlEngine? =
        _state.value.activeEngineId?.let(engineRepository::engine) as? CameraControlEngine

    /**
     * Tras conceder el permiso hay que refrescar el catálogo: la disponibilidad de los motores de
     * cámara cambia bajo los pies y el estado que la UI muestra quedaría obsoleto.
     */
    private fun requestCameraPermission() {
        viewModelScope.launch {
            val status = permissionController.request(Permission.Camera)
            engineRepository.refresh()

            if (!status.isGranted) {
                _effects.emit(
                    ScannerEffect.ShowMessage(
                        "Sin permiso de cámara solo quedan disponibles los motores que no la usan",
                    ),
                )
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }
}
