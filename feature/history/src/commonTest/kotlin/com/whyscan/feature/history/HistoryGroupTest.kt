package com.whyscan.feature.history

import com.whyscan.core.model.Barcode
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.Detection
import com.whyscan.core.model.HistoryEntry
import com.whyscan.core.model.ScannerEngineId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La agrupación por día, con la zona horaria como parámetro.
 *
 * Que la función la reciba en lugar de leer `TimeZone.currentSystemDefault()` es lo que hace posible
 * este archivo: el segundo test comprueba precisamente que el día depende de la zona, y con la del
 * sistema dentro pasaría o fallaría según dónde corriera el runner.
 */
class HistoryGroupTest {

    private fun entryAt(millis: Long, value: String = "x") = HistoryEntry(
        Detection.of(
            barcode = Barcode(rawValue = value, format = BarcodeFormat.QrCode),
            engineId = ScannerEngineId.ManualInput,
            detectedAtMillis = millis,
        ),
    )

    @Test
    fun `dos lecturas del mismo dia van al mismo grupo`() {
        val groups = listOf(
            entryAt(BASE + HORA, "tarde"),
            entryAt(BASE, "mediodia"),
        ).groupByDay(TimeZone.UTC)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().entries.size)
    }

    @Test
    fun `el dia depende de la zona horaria, no del instante`() {
        // El instante base son las 22:13 UTC del 14, que en +09:00 ya son las 07:13 del 15. Es el
        // caso que hace que agrupar no sea aritmética sobre milisegundos, y la razón de que la
        // función reciba la zona en lugar de leer la del sistema.
        val noche = entryAt(BASE)

        val enUtc = listOf(noche).groupByDay(TimeZone.UTC).single().date
        val enTokio = listOf(noche).groupByDay(TimeZone.of("UTC+09:00")).single().date

        assertEquals(LocalDate(2023, 11, 14), enUtc)
        assertEquals(LocalDate(2023, 11, 15), enTokio)
    }

    @Test
    fun `los grupos conservan el orden de entrada`() {
        // El almacén entrega de más reciente a más antiguo; agrupar no debe reordenar nada.
        val groups = listOf(
            entryAt(BASE + DIA, "hoy"),
            entryAt(BASE, "ayer"),
        ).groupByDay(TimeZone.UTC)

        assertEquals(2, groups.size)
        assertTrue(groups[0].date > groups[1].date, "el grupo más reciente va primero")
    }

    @Test
    fun `dias distintos dan grupos distintos`() {
        val groups = List(3) { entryAt(BASE + it * DIA, "codigo-$it") }.groupByDay(TimeZone.UTC)

        assertEquals(3, groups.size)
        assertTrue(groups.all { it.entries.size == 1 })
    }

    @Test
    fun `una lista vacia no produce grupos`() {
        assertTrue(emptyList<HistoryEntry>().groupByDay(TimeZone.UTC).isEmpty())
    }

    @Test
    fun `la fecha del grupo es la del dia local`() {
        val groups = listOf(entryAt(BASE)).groupByDay(TimeZone.UTC)

        assertEquals(LocalDate(2023, 11, 14), groups.single().date)
    }

    private companion object {
        /** 2023-11-14T22:13:20Z. Un instante fijo para que las fechas esperadas sean legibles. */
        const val BASE = 1_700_000_000_000L

        const val HORA = 3_600_000L
        const val DIA = 86_400_000L
    }
}
