package com.testscanner.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun `arranca_en_el_escaner`() {
        val navigator = Navigator()
        assertEquals(Destination.Scanner, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `navegar_apila_el_destino`() {
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        assertEquals(Destination.History, navigator.current)
        assertTrue(navigator.canGoBack)
        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `navegar_al_destino_actual_no_apila_una_copia`() {
        // Si apilara, el botón atrás dejaría de hacer lo que el usuario espera.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)
        navigator.navigateTo(Destination.History)

        assertEquals(2, navigator.backstack.value.size)
    }

    @Test
    fun `volver_atras_desapila`() {
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        assertTrue(navigator.goBack())
        assertEquals(Destination.Scanner, navigator.current)
    }

    @Test
    fun `volver_atras_en_la_raiz_devuelve_false_para_que_la_plataforma_cierre`() {
        val navigator = Navigator()
        assertFalse(navigator.goBack())
        assertEquals(Destination.Scanner, navigator.current)
    }

    @Test
    fun `guardar_y_restaurar_deja_el_backstack_igual`() {
        // Es el caso de que el sistema recree la Activity: el Navigator es otro objeto.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)
        navigator.navigateTo(Destination.Comparison)

        val restored = Navigator().apply { restoreState(navigator.saveState()) }

        assertEquals(navigator.backstack.value, restored.backstack.value)
        assertEquals(Destination.Comparison, restored.current)
        assertTrue(restored.canGoBack)
    }

    @Test
    fun `todo_destino_se_puede_guardar_y_volver_a_encontrar`() {
        // Si alguien agrega un destino y olvida el id, esto lo caza antes que una release ofuscada.
        val destinos = listOf(Destination.Scanner, Destination.Comparison, Destination.History)

        destinos.forEach { assertEquals(it, Destination.fromId(it.id)) }
    }

    @Test
    fun `un_id_desconocido_se_ignora_y_el_resto_se_restaura`() {
        // Pasa al actualizar la app con estado ya guardado de una versión anterior.
        val navigator = Navigator()

        navigator.restoreState(listOf("scanner", "destino_que_ya_no_existe", "history"))

        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `restaurar_algo_ilegible_deja_el_backstack_como_estaba`() {
        // Mejor quedarse en la pantalla inicial que arrancar con un backstack vacío y reventar.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        navigator.restoreState(listOf("nada", "de", "esto"))

        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `restaurar_una_lista_vacia_deja_el_backstack_como_estaba`() {
        val navigator = Navigator()

        navigator.restoreState(emptyList())

        assertEquals(listOf(Destination.Scanner), navigator.backstack.value)
    }
}
