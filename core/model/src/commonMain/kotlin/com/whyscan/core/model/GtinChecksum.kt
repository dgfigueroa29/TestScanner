package com.whyscan.core.model

/**
 * Dígito de control mod-10 de GTIN-8/12/13/14.
 *
 * Vive en el modelo — y no en el dominio — porque lo necesitan también los **motores**: el de
 * entrada manual, para inferir la simbología de lo que se teclea, y el de OCR, para saber si el
 * número que ha *leído* lo ha leído bien. Un motor no puede depender del dominio.
 */
object GtinChecksum {

    private val VALID_LENGTHS = setOf(8, 12, 13, 14)

    private const val ODD_WEIGHT = 3
    private const val EVEN_WEIGHT = 1
    private const val MODULUS = 10

    fun isValid(digits: String): Boolean {
        if (digits.length !in VALID_LENGTHS) return false
        if (!digits.all { it.isDigit() }) return false
        return checkDigit(digits.dropLast(1)) == digits.last().digitToInt()
    }

    /** Dígito de control de un payload **sin** su dígito de control. */
    fun checkDigit(payload: String): Int {
        val sum = payload.reversed()
            .mapIndexed { index, char ->
                char.digitToInt() * if (index % 2 == 0) ODD_WEIGHT else EVEN_WEIGHT
            }
            .sum()
        return (MODULUS - sum % MODULUS) % MODULUS
    }
}
