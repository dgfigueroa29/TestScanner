package com.whyscan.feature.scanner

import com.whyscan.core.domain.scan.ResultAction
import com.whyscan.core.platform.PlatformActions

/**
 * Ejecuta una acción sobre un resultado y decide **si hay que avisar** (RF-13).
 *
 * El reparto es el de siempre: el dominio decide *qué* se puede hacer con el código
 * ([ResultAction]), la plataforma lo *ejecuta* ([PlatformActions]) y la pantalla lo *redacta*. Lo
 * que faltaba era el punto de unión, que estaba en el ViewModel y no se podía probar sin levantar
 * uno entero con sus doce colaboradores.
 *
 * Aquí ya no hace falta: es una clase con una función y sin estado (deuda D16).
 */
class ResultActionRunner(
    private val actions: PlatformActions,
) {

    /** Si el sistema ofrece una hoja de compartir. En escritorio no la hay. */
    val canShare: Boolean get() = actions.canShare

    /**
     * Devuelve el aviso a mostrar, o `null` si no hace falta ninguno.
     *
     * Compartir y abrir son visibles por sí mismos: aparece una hoja o cambia de app. Copiar no
     * muestra nada, así que es la única que confirma cuando sale bien.
     */
    suspend fun run(action: ResultAction, text: String): ScannerMessage? {
        val (succeeded, failure) = when (action) {
            ResultAction.Copy -> actions.copyToClipboard(text) to ScannerMessage.CopyFailed
            ResultAction.Share -> actions.share(text) to ScannerMessage.ShareFailed
            is ResultAction.Open -> actions.openUrl(action.uri) to ScannerMessage.OpenFailed
        }

        return when {
            !succeeded -> failure
            action == ResultAction.Copy -> ScannerMessage.Copied
            else -> null
        }
    }
}
