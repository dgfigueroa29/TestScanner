package com.whyscan.navigation

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
        // Se itera sobre `Destination.all` y no sobre una copia escrita aquí: con la copia, añadir
        // un destino y olvidar registrarlo dejaba este test en verde comprobando los de siempre.
        // Si alguien agrega un destino y olvida el id, esto lo caza antes que una release ofuscada.
        Destination.all.forEach { assertEquals(it, Destination.fromId(it.id)) }
    }

    @Test
    fun `los_ids_de_los_destinos_no_se_repiten`() {
        // Dos destinos con el mismo id harían que restaurar el backstack devolviera al usuario a
        // una pantalla que no es la que dejó, y `fromId` elegiría siempre el primero en silencio.
        assertEquals(Destination.all.size, Destination.all.map { it.id }.distinct().size)
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
    fun `podar_quita_los_destinos_que_dejaron_de_estar_disponibles`() {
        // Es lo que pasa al apagar el modo avanzado: el comparador desaparece de la barra.
        val navigator = Navigator()
        navigator.navigateTo(Destination.Comparison)
        navigator.navigateTo(Destination.History)

        navigator.pruneTo(listOf(Destination.Scanner, Destination.History, Destination.Settings))

        // El comparador se va también de en medio del backstack, no solo de la cima: si quedara
        // enterrado, el botón atrás devolvería a una pantalla que ya no se ofrece.
        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
        assertEquals(Destination.History, navigator.current)
    }

    @Test
    fun `podar_hasta_dejarlo_vacio_vuelve_al_destino_inicial`() {
        // Un backstack vacío no es representable: `current` es `last()`. Vale más volver a la raíz
        // que reventar con una IndexOutOfBounds en la siguiente composición.
        val navigator = Navigator()
        navigator.navigateTo(Destination.Comparison)

        navigator.pruneTo(emptyList())

        assertEquals(listOf(Destination.Scanner), navigator.backstack.value)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `restaurar_una_lista_vacia_deja_el_backstack_como_estaba`() {
        val navigator = Navigator()

        navigator.restoreState(emptyList())

        assertEquals(listOf(Destination.Scanner), navigator.backstack.value)
    }
}
