package com.testscanner.core.designsystem

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {

    @Test
    fun `negro sobre blanco da el maximo de la escala`() {
        // Ancla la fórmula contra un valor conocido: si la linealización de canal se rompe, esto
        // deja de dar 21 y el resto de asertos dejarían de significar nada.
        val ratio = Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())

        assertTrue(abs(ratio - 21.0) < 0.01, "negro sobre blanco dio $ratio en vez de 21")
    }

    @Test
    fun `un color contra si mismo no tiene contraste`() {
        assertTrue(abs(Contrast.ratio(0xFF2563EB.toInt(), 0xFF2563EB.toInt()) - 1.0) < 0.001)
    }

    @Test
    fun `el orden de los colores no cambia el resultado`() {
        // La razón se define entre el más claro y el más oscuro, no entre texto y fondo: si
        // dependiera del orden, medir un texto claro sobre fondo oscuro daría otro número.
        val directo = Contrast.ratio(0xFF2563EB.toInt(), 0xFFFFFFFF.toInt())
        val inverso = Contrast.ratio(0xFFFFFFFF.toInt(), 0xFF2563EB.toInt())

        assertTrue(abs(directo - inverso) < 0.0001)
    }

    @Test
    fun `toda la paleta cumple el contraste AA para texto normal`() {
        // RNF-05. Incluye los pares que la UI usa de hecho —primary, tertiary y error como color
        // de texto sobre la tarjeta— y no solo los que Material garantiza por convención.
        val incumplen = ScannerPalette.measuredPairs()
            .map { it to Contrast.ratio(it.foreground, it.background) }
            .filter { (_, ratio) -> ratio < Contrast.AA_NORMAL_TEXT }
            .map { (pair, ratio) -> "${pair.name}: ${ratio.rounded()}" }

        assertTrue(incumplen.isEmpty(), "pares por debajo de AA (4.5:1):\n${incumplen.joinToString("\n")}")
    }

    @Test
    fun `los dos temas miden los mismos pares`() {
        // Un par que solo existe en claro es un par que nadie comprueba en oscuro. Es la forma
        // habitual de que el modo oscuro se degrade sin que nadie se entere.
        val (claros, oscuros) = ScannerPalette.measuredPairs().partition { it.name.startsWith("claro") }

        assertTrue(claros.isNotEmpty())
        assertTrue(
            claros.map { it.name.substringAfter(": ") } == oscuros.map { it.name.substringAfter(": ") },
            "los pares medidos en claro y en oscuro no coinciden",
        )
    }

    private fun Double.rounded(): String {
        val scaled = (this * 100).toInt()
        return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
    }
}
