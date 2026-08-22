package com.whyscan.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.domain.usecase.ScanSessions
import com.whyscan.core.domain.usecase.ScanSettings
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Permission
import com.whyscan.core.model.ScanImage
import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PickImageResult
import com.whyscan.core.scanner.CameraControlEngine
import com.whyscan.core.scanner.ScanEvent
import com.whyscan.core.scanner.TextInputEngine
import com.whyscan.core.scanner.capability
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
 * El ViewModel **no conoce ningún motor concreto**: pide una sesión a [ScanSessions] y reacciona a
 * los [ScanEvent] que llegan. Toda la lógica de selección y degradación vive en el dominio, así
 * que añadir un motor nuevo no toca este archivo (RNF-07).
 *
 * ### Por qué sigue teniendo supresiones
 * La deuda D16 se saldó agrupando: los ajustes en [ScanSettings], la sesión y el guardado en
 * [ScanSessions], y las acciones sobre el resultado en [ResultActionRunner]. De **doce
 * dependencias quedan seis**, y `LongParameterList` ya no hace falta silenciarla.
 *
 * `TooManyFunctions` sobrevive, y es un dato honesto: esta pantalla tiene catorce acciones de
 * usuario y cada una necesita su función. Partirla por partir movería el recuento a otro archivo
 * sin que nadie entienda mejor la pantalla. La supresión se pone aquí, a la vista, y no subiendo el
 * umbral global —que dejaría la regla midiendo siempre lo que hubiera.
 */
@Suppress("TooManyFunctions")
class ScannerViewModel(
    private val settings: ScanSettings,
    private val sessions: ScanSessions,
    private val engineRepository: ScannerEngineRepository,
    private val permissionController: PermissionController,
    private val imagePicker: ImagePicker,
    private val resultActions: ResultActionRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState(canShare = resultActions.canShare))
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ScannerEffect>()
    val effects: SharedFlow<ScannerEffect> = _effects.asSharedFlow()

    private var sessionJob: Job? = null

    /**
     * Hay un arranque automático esperando a que el catálogo diga si se puede. Ver [startIfPending].
     */
    private var autoStartPending: Boolean = false

    init {
        observeCatalogChanges()
        observePreferenceChanges()
    }

    /**
     * `CyclomaticComplexMethod` cuenta catorce ramas y tiene razón en el número, no en lo que
     * significa: es una tabla de despacho sobre un `sealed interface`, donde cada rama es una línea
     * y el compilador exige que estén todas. Partirla en dos `when` la haría peor de leer y bajaría
     * la métrica, que es justo la clase de arreglo que no sirve para nada.
     *
     * Se suprime aquí y no con `ignoreSimpleWhenEntries` en la configuración: esa opción dejaría de
     * contar los `when` de una línea en todo el proyecto, incluidos los que sí esconden complejidad.
     */
    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: ScannerAction) {
        when (action) {
            ScannerAction.Refresh -> refresh()
            ScannerAction.ScreenShown -> screenShown()
            ScannerAction.ScreenHidden -> stopSession()
            ScannerAction.ClearDetections -> _state.update { it.copy(detections = emptyList()) }
            ScannerAction.StartSession -> startSession()
            ScannerAction.StopSession -> stopSession()
            is ScannerAction.SelectEngine -> selectEngine(action.id)
            is ScannerAction.ToggleFormat -> toggleFormat(action.format)
            is ScannerAction.SetContinuous -> setContinuous(action.enabled)
            is ScannerAction.ManualInputChanged -> _state.update { it.copy(manualInput = action.value) }
            ScannerAction.SubmitManualInput -> submitManualInput()
            ScannerAction.ScanFromImage -> scanFromImage()
            is ScannerAction.RunResultAction -> runResultAction(action.action, action.text)
            ScannerAction.ToggleTorch -> toggleTorch()
            is ScannerAction.SetZoom -> setZoom(action.ratio)
            ScannerAction.RequestCameraPermission -> requestCameraPermission()
            ScannerAction.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun observeCatalogChanges() {
        viewModelScope.launch {
            engineRepository.observeCatalog().collect { catalog ->
                _state.update { it.copy(catalog = catalog, isLoading = false) }
                startIfPending()
            }
        }
    }

    /**
     * El arranque automático se resuelve **aquí** y no dentro de [screenShown], y no es un detalle.
     *
     * `refresh()` actualiza el catálogo publicando en un `Flow` que se colecta en otra corrutina, así
     * que cuando `refresh()` devuelve el estado todavía puede tener la disponibilidad vieja. Decidir
     * ahí si arrancar era una carrera: en un arranque en frío el catálogo aún estaba vacío, el
     * `hasLiveCameraEngine` daba `false` y la cámara no se abría nunca. Aquí llega ya resuelto.
     */
    private fun startIfPending() {
        if (!autoStartPending) return

        val state = _state.value
        // Sin permiso no se arranca: la pantalla enseña la explicación y el botón. Pedirlo sin que
        // el usuario haya tocado nada es la forma más rápida de que lo deniegue para siempre.
        if (state.needsCameraPermission || !state.hasLiveCameraEngine) return
        if (state.sessionStatus == SessionStatus.Scanning || state.sessionStatus == SessionStatus.Starting) {
            return
        }

        autoStartPending = false
        startSession()
    }

    private fun screenShown() {
        autoStartPending = true

        // Se intenta **ya** y además se refresca, y hacen falta las dos cosas:
        //
        //  - Volver a la pantalla con el catálogo ya cargado no produce ninguna emisión nueva —el
        //    `StateFlow` no reemite un valor igual—, así que esperar a `observeCatalogChanges` para
        //    arrancar dejaba la cámara apagada para siempre a partir de la segunda visita.
        //  - En un arranque en frío el catálogo todavía está vacío y aquí no se puede decidir nada;
        //    lo resuelve la emisión que llega después.
        startIfPending()
        refresh()
    }

    private fun observePreferenceChanges() {
        viewModelScope.launch {
            settings.observe().collect { preferences ->
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
            settings.preferEngine(id)
            if (_state.value.sessionStatus == SessionStatus.Scanning) {
                stopSession()
                startSession()
            }
        }
    }

    private fun toggleFormat(format: BarcodeFormat) {
        viewModelScope.launch {
            val current = _state.value.formats
            settings.setFormats(if (format in current) current - format else current + format)
        }
    }

    private fun setContinuous(enabled: Boolean) {
        viewModelScope.launch { settings.setContinuous(enabled) }
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
            sessions.start(settings.current()).collect(::reduce)
        }
    }

    private fun stopSession() {
        // Parar es también renunciar a un arranque pendiente: si no, volver de los ajustes
        // reabriría la cámara que el usuario acaba de cerrar.
        autoStartPending = false
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
                sessions.save(event.detections)
                // Con tope: una sesión continua larga acumulaba resultados sin límite y la lista
                // crecía hasta donde aguantara la memoria. Lo que se recorta aquí no se pierde —el
                // historial lo guarda todo—, solo deja de ocupar RAM en una pantalla donde nadie va
                // a desplazarse cien lecturas hacia abajo.
                _state.update {
                    it.copy(detections = (event.detections + it.detections).take(MAX_VISIBLE_DETECTIONS))
                }
            }

            is ScanEvent.EngineSwitched -> {
                _state.update { it.copy(switchedFrom = event.from, activeEngineId = event.to) }
                _effects.emit(ScannerEffect.ShowMessage(ScannerMessage.EngineSwitched))
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
                ?.capability<TextInputEngine>()

            if (engine != null) {
                engine.submit(value)
                _state.update { it.copy(manualInput = "") }
            } else {
                _effects.emit(ScannerEffect.ShowMessage(ScannerMessage.ManualInputUnavailable))
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
                        _effects.emit(ScannerEffect.ShowMessage(ScannerMessage.Raw(picked.reason)))

                    is PickImageResult.Picked -> decodePickedImage(picked.image)
                }
            } finally {
                _state.update { it.copy(isDecodingImage = false) }
            }
        }
    }

    private suspend fun decodePickedImage(image: ScanImage) {
        sessions.decode(image, settings.current())
            .onSuccess { detections ->
                if (detections.isEmpty()) {
                    _effects.emit(ScannerEffect.ShowMessage(ScannerMessage.NoCodeInImage))
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
                sessions.save(detections)
            }
            .onFailure { failure ->
                val reason = failure.message?.let(ScannerMessage::Raw) ?: ScannerMessage.NoCodeInImage
                _effects.emit(ScannerEffect.ShowMessage(reason))
            }
    }

    private fun runResultAction(action: ResultAction, text: String) {
        viewModelScope.launch {
            resultActions.run(action, text)
                ?.let { _effects.emit(ScannerEffect.ShowMessage(it)) }
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
        _state.value.activeEngineId
            ?.let(engineRepository::engine)
            ?.capability<CameraControlEngine>()

    /**
     * Tras conceder el permiso hay que refrescar el catálogo: la disponibilidad de los motores de
     * cámara cambia bajo los pies y el estado que la UI muestra quedaría obsoleto.
     */
    private fun requestCameraPermission() {
        viewModelScope.launch {
            val status = permissionController.request(Permission.Camera)
            engineRepository.refresh()

            if (!status.isGranted) {
                _effects.emit(ScannerEffect.ShowMessage(ScannerMessage.CameraPermissionDenied))
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /**
         * Cuántas lecturas se conservan en pantalla. El historial no tiene este tope: ahí sí se
         * guarda todo, porque su razón de ser es justamente conservarlo.
         */
        const val MAX_VISIBLE_DETECTIONS = 100
    }
}
