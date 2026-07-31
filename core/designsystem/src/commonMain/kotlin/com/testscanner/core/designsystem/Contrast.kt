package com.testscanner.core.designsystem

import kotlin.math.pow

/**
 * Contraste según WCAG 2.1, para poder exigir AA (RNF-05) desde un test y no desde un documento.
 *
 * Es aritmética sobre enteros ARGB: no depende de Compose, no necesita dispositivo y por tanto se
 * ejecuta en `commonTest` como cualquier otra regla del dominio. Que la accesibilidad se pueda
 * romper sin que nada avise es lo que convierte un requisito en una intención.
 */
object Contrast {

    /** Texto normal. El umbral de la mayoría de la UI. */
    const val AA_NORMAL_TEXT = 4.5

    /** Texto grande (≥ 18 pt, o ≥ 14 pt en negrita) y componentes no textuales. */
    const val AA_LARGE_TEXT = 3.0

    /**
     * Razón de contraste entre dos colores ARGB, entre 1.0 (idénticos) y 21.0 (negro sobre blanco).
     *
     * El canal alfa se ignora: la fórmula de WCAG se aplica sobre el color ya compuesto, y aquí los
     * dos colores llegan opacos.
     */
    fun ratio(foreground: Int, background: Int): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + OFFSET) / (darker + OFFSET)
    }

    /** Luminancia relativa de un ARGB, tal como la define WCAG 2.1. */
    fun relativeLuminance(color: Int): Double {
        val red = channel((color shr 16) and BYTE)
        val green = channel((color shr 8) and BYTE)
        val blue = channel(color and BYTE)
        return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
    }

    /**
     * Linealiza un canal de 0..255. El tramo recto de abajo no es un detalle decorativo: sin él,
     * los colores muy oscuros salen con menos luminancia de la que tienen y el contraste calculado
     * quedaría por encima del real, que es el error que no queremos cometer.
     */
    private fun channel(value: Int): Double {
        val normalized = value / MAX_CHANNEL
        return if (normalized <= LINEAR_THRESHOLD) {
            normalized / LINEAR_DIVISOR
        } else {
            ((normalized + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        }
    }

    private const val BYTE = 0xFF
    private const val MAX_CHANNEL = 255.0
    private const val OFFSET = 0.05
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4
}
