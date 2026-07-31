package com.testscanner.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun `arranca en el escaner`() {
        val navigator = Navigator()
        assertEquals(Destination.Scanner, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `navegar apila el destino`() {
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        assertEquals(Destination.History, navigator.current)
        assertTrue(navigator.canGoBack)
        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `navegar al destino actual no apila una copia`() {
        // Si apilara, el botón atrás dejaría de hacer lo que el usuario espera.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)
        navigator.navigateTo(Destination.History)

        assertEquals(2, navigator.backstack.value.size)
    }

    @Test
    fun `volver atras desapila`() {
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        assertTrue(navigator.goBack())
        assertEquals(Destination.Scanner, navigator.current)
    }

    @Test
    fun `volver atras en la raiz devuelve false para que la plataforma cierre`() {
        val navigator = Navigator()
        assertFalse(navigator.goBack())
        assertEquals(Destination.Scanner, navigator.current)
    }

    @Test
    fun `guardar y restaurar deja el backstack igual`() {
        // Es el caso de rotar el teléfono: la Activity se recrea y el Navigator es otro objeto.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)
        navigator.navigateTo(Destination.Comparison)

        val restored = Navigator().apply { restoreState(navigator.saveState()) }

        assertEquals(navigator.backstack.value, restored.backstack.value)
        assertEquals(Destination.Comparison, restored.current)
        assertTrue(restored.canGoBack)
    }

    @Test
    fun `todo destino se puede guardar y volver a encontrar`() {
        // Si alguien agrega un destino y olvida el id, esto lo caza antes que una release ofuscada.
        val destinos = listOf(Destination.Scanner, Destination.Comparison, Destination.History)

        destinos.forEach { assertEquals(it, Destination.fromId(it.id)) }
    }

    @Test
    fun `un id desconocido se ignora y el resto se restaura`() {
        // Pasa al actualizar la app con estado ya guardado de una versión anterior.
        val navigator = Navigator()

        navigator.restoreState(listOf("scanner", "destino_que_ya_no_existe", "history"))

        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `restaurar algo ilegible deja el backstack como estaba`() {
        // Mejor quedarse en la pantalla inicial que arrancar con un backstack vacío y reventar.
        val navigator = Navigator()
        navigator.navigateTo(Destination.History)

        navigator.restoreState(listOf("nada", "de", "esto"))

        assertEquals(listOf(Destination.Scanner, Destination.History), navigator.backstack.value)
    }

    @Test
    fun `restaurar una lista vacia deja el backstack como estaba`() {
        val navigator = Navigator()

        navigator.restoreState(emptyList())

        assertEquals(listOf(Destination.Scanner), navigator.backstack.value)
    }
}
