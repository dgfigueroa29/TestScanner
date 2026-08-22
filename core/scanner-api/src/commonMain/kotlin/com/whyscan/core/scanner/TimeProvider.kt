package com.whyscan.core.scanner

/**
 * Reloj inyectable. Ni el dominio ni los motores llaman a APIs de tiempo del sistema directamente:
 * las marcas temporales y las latencias son parte del resultado observable y los tests necesitan
 * controlarlas.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/** Reloj del sistema. Único punto donde se consulta la hora real. */
expect object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long
}

/** Reloj fijo para tests: avanza solo cuando se le pide. */
class FakeTimeProvider(private var now: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }

    fun setTo(millis: Long) {
        now = millis
    }
}
