package com.whyscan.feature.history

import com.whyscan.core.model.HistoryEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Las lecturas de un mismo día, con el día delante.
 *
 * ## Por qué el historial deja de ser una lista plana
 *
 * Doscientas filas seguidas son doscientas filas seguidas: se recorren pasando el dedo y esperando
 * reconocer algo. Una cabecera por día le da a esa lista lo único que el usuario **sí** recuerda de
 * una lectura que no anotó — cuándo la hizo—, y convierte el desplazamiento en navegación.
 *
 * ## Por qué esto obligó a traer `kotlinx-datetime`
 *
 * El SDD (§9.7) dice que la exportación guarda la fecha en milisegundos desde época y **no** en
 * ISO-8601, precisamente para no arrastrar una librería de fechas por una columna de un CSV. Ese
 * razonamiento sigue siendo correcto para aquello y deja de serlo aquí: agrupar por día no es
 * aritmética sobre milisegundos.
 *
 * "El mismo día" depende de la **zona horaria** del usuario —dos lecturas separadas por un minuto
 * pueden caer en días distintos, y la medianoche no está cada 86 400 000 ms exactos por el horario
 * de verano—, y "el día anterior" depende del **calendario**. Escribir eso a mano es reimplementar
 * peor lo que hace una librería de primera parte y multiplataforma.
 */
data class HistoryGroup(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
)

/**
 * Agrupa por día local, conservando el orden de entrada.
 *
 * Recibe la [TimeZone] en lugar de leer la del sistema, y no es ceremonia: hace que la función sea
 * **pura y probable**. Con `TimeZone.currentSystemDefault()` dentro, un test de agrupación pasaría o
 * fallaría según dónde corriera el runner, que es exactamente la clase de test que un día se pone en
 * rojo sin que nadie haya tocado nada.
 *
 * Las entradas ya llegan de más reciente a más antigua —lo garantiza el almacén— así que agrupar
 * conservando el orden deja los grupos también ordenados sin volver a ordenar nada.
 */
fun List<HistoryEntry>.groupByDay(timeZone: TimeZone): List<HistoryGroup> =
    groupBy { it.detection.detectedAtMillis.toLocalDate(timeZone) }
        .map { (date, entries) -> HistoryGroup(date, entries) }

/** El día local en el que cayó un instante. */
internal fun Long.toLocalDate(timeZone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date
