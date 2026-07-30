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
}
