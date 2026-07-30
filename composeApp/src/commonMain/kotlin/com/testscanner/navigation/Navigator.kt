package com.testscanner.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Destinos de la app. Ver ADR-0005 para por qué la navegación es propia en esta fase. */
sealed interface Destination {
    data object Scanner : Destination
    data object Comparison : Destination
    data object History : Destination
}

/**
 * Backstack mínimo.
 *
 * Es lógica pura sobre una lista, así que se testea sin Compose. ADR-0005 fija la condición de
 * salida: si el grafo pasa de seis destinos o aparece la necesidad de deep links, se migra a
 * `navigation-compose` multiplataforma reimplementando esta clase, sin tocar pantallas.
 */
class Navigator(initial: Destination = Destination.Scanner) {

    private val _backstack = MutableStateFlow(listOf(initial))
    val backstack: StateFlow<List<Destination>> = _backstack.asStateFlow()

    val current: Destination get() = _backstack.value.last()

    val canGoBack: Boolean get() = _backstack.value.size > 1

    fun navigateTo(destination: Destination) {
        // Navegar al destino en el que ya estás no debe apilar una copia: el botón atrás dejaría de
        // hacer lo que el usuario espera.
        if (current == destination) return
        _backstack.update { it + destination }
    }

    /** Devuelve `false` si no había nada que desapilar, para que la plataforma cierre la pantalla. */
    fun goBack(): Boolean {
        if (!canGoBack) return false
        _backstack.update { it.dropLast(1) }
        return true
    }
}
