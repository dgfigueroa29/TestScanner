package com.testscanner.core.designsystem

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Canal para que cualquier pantalla muestre un mensaje efímero sin montar su propio `Scaffold`.
 *
 * El `SnackbarHost` vive una sola vez, en la raíz de la app, porque el mensaje debe sobrevivir a
 * cambiar de pestaña y porque anidar `Scaffold`s para tener uno por pantalla duplica insets y barras
 * sin ganar nada.
 *
 * Es `staticCompositionLocalOf` y no `compositionLocalOf`: el valor no cambia nunca durante la vida
 * de la app, así que no hace falta que Compose siga sus lecturas para recomponer.
 */
val LocalSnackbarHostState = staticCompositionLocalOf {
    error("No hay SnackbarHostState en el árbol: falta envolver la UI en el Scaffold de App()")
}
