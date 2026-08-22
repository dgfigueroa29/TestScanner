package com.whyscan.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Destinos de la app. Ver ADR-0005 para por qué la navegación es propia en esta fase. */
sealed interface Destination {

    /**
     * Identificador estable, para guardar y restaurar el backstack.
     *
     * Escrito a mano y no derivado del nombre de la clase, por lo mismo que en `BarcodeValueType`:
     * `::class.simpleName` devuelve el nombre ofuscado en una build con R8, así que restaurar
     * dejaría de encontrar el destino justo en release.
     */
    val id: String

    data object Scanner : Destination {
        override val id: String get() = "scanner"
    }

    data object Comparison : Destination {
        override val id: String get() = "comparison"
    }

    data object History : Destination {
        override val id: String get() = "history"
    }

    data object Settings : Destination {
        override val id: String get() = "settings"
    }

    companion object {
        /**
         * Todos los destinos, en el orden en que aparecen en la barra de navegación.
         *
         * Es público para que el test que comprueba que cada uno se puede guardar y recuperar
         * itere sobre **esta** lista y no sobre una copia escrita a mano. Con la copia, añadir un
         * destino y olvidar su id dejaba el test en verde: comprobaba los tres de siempre.
         */
        val all: List<Destination> = listOf(Scanner, Comparison, History, Settings)

        fun fromId(id: String): Destination? = all.firstOrNull { it.id == id }
    }
}

/**
 * Backstack mínimo.
 *
 * Es lógica pura sobre una lista, así que se testea sin Compose. ADR-0005 fija la condición de
 * salida: si el grafo pasa de seis destinos o aparece la necesidad de deep links, se migra a
 * `navigation-compose` multiplataforma reimplementando esta clase, sin tocar pantallas.
 */
class Navigator(private val initial: Destination = Destination.Scanner) {

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

    /**
     * Deja en el backstack solo los destinos que siguen estando disponibles.
     *
     * Hace falta porque la lista de destinos **cambia en caliente**: apagar el modo avanzado retira
     * el comparador. Sin esto, quien lo apagara teniéndolo en pantalla se quedaba en un destino que
     * ya no sale en ninguna barra —sin ítem activo y sin forma de volver salvo el botón atrás—, y
     * apilar encima el escáner tampoco servía: el atrás lo devolvía justo a donde no debía estar.
     *
     * Si no queda nada, se vuelve al destino inicial en lugar de dejar el backstack vacío, que es lo
     * único que este tipo no puede representar: `current` es `last()`.
     */
    fun pruneTo(available: Collection<Destination>) {
        _backstack.update { stack ->
            stack.filter { it in available }.ifEmpty { listOf(initial) }
        }
    }

    /** Devuelve `false` si no había nada que desapilar, para que la plataforma cierre la pantalla. */
    fun goBack(): Boolean {
        if (!canGoBack) return false
        _backstack.update { it.dropLast(1) }
        return true
    }

    /**
     * Backstack como ids, para que la plataforma lo guarde donde sepa (parte de la deuda D4).
     *
     * Sin esto, cualquier recreación devolvía al usuario a la pantalla de escaneo: el backstack
     * vivía solo en memoria. No es un problema de la navegación propia —`navigation-compose`
     * tampoco lo resuelve solo— sino de no haberlo guardado nunca.
     */
    fun saveState(): List<String> = _backstack.value.map { it.id }

    /**
     * Restaura un backstack guardado. Ignora lo que no reconozca.
     *
     * Un id desconocido significa que la app cambió de versión con el estado ya guardado; quedarse
     * con lo que sí existe es mejor que descartarlo todo, y mucho mejor que reventar al arrancar.
     * Si no queda nada utilizable, se deja el backstack como estaba.
     */
    fun restoreState(ids: List<String>) {
        val restored = ids.mapNotNull(Destination::fromId)
        if (restored.isEmpty()) return
        _backstack.value = restored
    }
}
