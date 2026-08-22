package com.whyscan.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whyscan.core.designsystem.LocalSnackbarHostState
import com.whyscan.core.designsystem.Spacing
import com.whyscan.core.domain.export.ExportFormat
import com.whyscan.core.domain.scan.ShareableContent
import com.whyscan.feature.history.resources.Res
import com.whyscan.feature.history.resources.history_clear
import com.whyscan.feature.history.resources.history_clear_body
import com.whyscan.feature.history.resources.history_clear_cancel
import com.whyscan.feature.history.resources.history_clear_confirm
import com.whyscan.feature.history.resources.history_clear_title
import com.whyscan.feature.history.resources.history_date
import com.whyscan.feature.history.resources.history_empty
import com.whyscan.feature.history.resources.history_export_csv
import com.whyscan.feature.history.resources.history_export_json
import com.whyscan.feature.history.resources.history_export_text
import com.whyscan.feature.history.resources.history_filter_all
import com.whyscan.feature.history.resources.history_no_matches
import com.whyscan.feature.history.resources.history_search
import com.whyscan.feature.history.resources.history_search_clear
import com.whyscan.feature.history.resources.history_today
import com.whyscan.feature.history.resources.history_yesterday
import com.whyscan.feature.history.resources.message_copied
import com.whyscan.feature.history.resources.message_copy_failed
import com.whyscan.feature.history.resources.message_entry_deleted
import com.whyscan.feature.history.resources.message_exported
import com.whyscan.feature.history.resources.message_exported_to
import com.whyscan.feature.history.resources.message_note_removed
import com.whyscan.feature.history.resources.message_note_saved
import com.whyscan.feature.history.resources.message_nothing_to_export
import com.whyscan.feature.history.resources.message_open_failed
import com.whyscan.feature.history.resources.message_share_failed
import com.whyscan.feature.history.resources.message_undo
import com.whyscan.feature.history.resources.month_1
import com.whyscan.feature.history.resources.month_10
import com.whyscan.feature.history.resources.month_11
import com.whyscan.feature.history.resources.month_12
import com.whyscan.feature.history.resources.month_2
import com.whyscan.feature.history.resources.month_3
import com.whyscan.feature.history.resources.month_4
import com.whyscan.feature.history.resources.month_5
import com.whyscan.feature.history.resources.month_6
import com.whyscan.feature.history.resources.month_7
import com.whyscan.feature.history.resources.month_8
import com.whyscan.feature.history.resources.month_9
import com.whyscan.feature.history.resources.share_separator
import com.whyscan.feature.history.resources.share_wifi
import com.whyscan.feature.history.resources.share_wifi_with_password
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(
    advancedMode: Boolean = false,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resolve(effect.message),
                        actionLabel = if (effect.undoable) getString(Res.string.message_undo) else null,
                        // Deshacer sin prisa: el aviso se queda hasta que el usuario decide. Un
                        // borrado que se puede revertir durante dos segundos y medio no se puede
                        // revertir de verdad — no da tiempo ni a leer qué desapareció.
                        duration = if (effect.undoable) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onAction(HistoryAction.UndoDelete)
                    }
                }
            }
        }
    }

    HistoryContent(state = state, onAction = viewModel::onAction, advancedMode = advancedMode)
}

/**
 * @param advancedMode muestra los filtros por motor y la latencia de cada lectura. Filtrar por motor
 *   es lo que convierte el historial en una herramienta de comparación (G5) y no tiene sentido para
 *   quien nunca eligió uno: los chips se llaman `mlkit-camerax` y `zxing-cpp`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryContent(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
    advancedMode: Boolean = false,
) {
    if (state.isConfirmingClear) {
        ClearConfirmation(count = state.entries.size, onAction = onAction)
    }

    when {
        state.isLoading -> Centered(modifier) { CircularProgressIndicator() }

        state.isEmpty -> Centered(modifier) { EmptyMessage(stringResource(Res.string.history_empty)) }

        else -> Column(modifier = modifier.fillMaxSize().padding(Spacing.md)) {
            SearchField(query = state.query, onAction = onAction)

            HistoryToolbar(state, onAction, advancedMode)

            // Un historial lleno cuyo filtro no deja nada no es lo mismo que un historial vacío, y
            // decir "todavía no escaneaste nada" cuando hay cien lecturas detrás es mentir.
            if (state.isFilteredEmpty) {
                Centered(Modifier) { EmptyMessage(stringResource(Res.string.history_no_matches)) }
            } else {
                // La zona horaria se lee aquí, en el borde: la agrupación es pura y recibe la que le
                // den, para que un test no dependa de dónde corra el runner.
                val timeZone = TimeZone.currentSystemDefault()
                val today = remember(timeZone) { Clock.System.now().toLocalDateTime(timeZone).date }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.visibleGroups(timeZone).forEach { group ->
                        // Pegajosa: mientras se recorre un día largo, la cabecera se queda arriba
                        // diciendo cuál es. Sin eso, a la tercera pantalla ya no se sabe dónde uno
                        // está — que es justo el problema que las cabeceras vienen a resolver.
                        //
                        // Sin `import`: `stickyHeader` es un **miembro** de `LazyListScope`, no una
                        // extensión de nivel superior, así que se resuelve por el receptor. Importarlo
                        // da `Unresolved reference`, que es un error confuso para lo que en realidad
                        // es un import de más.
                        stickyHeader(key = group.date.toString()) { DayHeader(group.date, today) }

                        items(group.entries, key = { it.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                canShare = state.canShare,
                                advancedMode = advancedMode,
                                isEditingNote = state.editingNoteFor == entry.id,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cabecera de un día.
 *
 * **"Hoy" y "Ayer" en lugar de la fecha**, porque es lo que una persona reconoce sin leer: nadie
 * piensa "lo escaneé el 22 de agosto", piensa "fue ayer". A partir del antepenúltimo día la fecha
 * vuelve a ser la única referencia útil y se escribe entera.
 *
 * El día de hoy llega como parámetro y no se lee aquí. Es la misma razón que en la agrupación: leer
 * el reloj dentro de un composable lo hace impredecible, y un `remember` lo dejaría además congelado
 * en el instante en que se compuso — con la app abierta a medianoche, "Hoy" pasaría a ser mentira sin
 * que nada la recompusiera.
 */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val label = when (date) {
        today -> stringResource(Res.string.history_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.history_yesterday)
        else -> stringResource(
            Res.string.history_date,
            date.dayOfMonth,
            stringResource(date.month.nameResource()),
            date.year,
        )
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
    }
}

/**
 * El nombre del mes, traducible.
 *
 * Sobre el **enum** y no sobre el número de mes, y no es cosmético: un `when` exhaustivo sobre un
 * enum no lleva `else`, así que **el compilador obliga a cubrir los doce**. La primera versión de
 * esto iba sobre `Int`, necesitaba un `else` —que en la práctica era "y si no, diciembre"— y detekt
 * la rechazó con nueve `MagicNumber` seguidos. Tenía razón: una lista de números sueltos en un
 * `when` es justo donde se cuela un mes desplazado que nadie ve hasta que llega ese mes.
 */
private fun Month.nameResource(): StringResource = when (this) {
    Month.JANUARY -> Res.string.month_1
    Month.FEBRUARY -> Res.string.month_2
    Month.MARCH -> Res.string.month_3
    Month.APRIL -> Res.string.month_4
    Month.MAY -> Res.string.month_5
    Month.JUNE -> Res.string.month_6
    Month.JULY -> Res.string.month_7
    Month.AUGUST -> Res.string.month_8
    Month.SEPTEMBER -> Res.string.month_9
    Month.OCTOBER -> Res.string.month_10
    Month.NOVEMBER -> Res.string.month_11
    Month.DECEMBER -> Res.string.month_12
}

/**
 * Buscador sobre el valor y sobre la nota.
 *
 * Está siempre y no detrás de un icono: cuando el historial tiene doscientas filas, buscar deja de
 * ser una función avanzada y pasa a ser la forma normal de usar la pantalla. La cruz para limpiar
 * solo aparece cuando hay algo que limpiar, que es la convención de todos los buscadores.
 */
@Composable
private fun SearchField(query: String, onAction: (HistoryAction) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onAction(HistoryAction.Search(it)) },
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        placeholder = { Text(stringResource(Res.string.history_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onAction(HistoryAction.Search("")) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.history_search_clear),
                    )
                }
            }
        },
        singleLine = true,
    )
}

/**
 * Barra del historial: filtros a la izquierda, exportar y borrar a la derecha.
 *
 * Filtrar por motor es lo que convierte el historial en una herramienta de comparación: permite ver
 * qué leyó cada alternativa sobre los mismos códigos. Por eso los filtros solo salen en modo
 * avanzado — sus chips se llaman `mlkit-camerax` y `zxing-cpp`, que es un vocabulario que solo tiene
 * sentido para quien eligió un motor a mano. Exportar y borrar sí están siempre: son operaciones
 * sobre *los datos del usuario*, no sobre el banco de pruebas.
 */
@Composable
private fun HistoryToolbar(
    state: HistoryState,
    onAction: (HistoryAction) -> Unit,
    advancedMode: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (advancedMode) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    FilterChip(
                        selected = state.engineFilter == null,
                        onClick = { onAction(HistoryAction.FilterByEngine(null)) },
                        label = { Text(stringResource(Res.string.history_filter_all)) },
                    )
                }
                items(state.presentEngines, key = { it.id }) { engineId ->
                    FilterChip(
                        selected = state.engineFilter == engineId,
                        onClick = { onAction(HistoryAction.FilterByEngine(engineId)) },
                        label = { Text(engineId.id) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Exportar lo que se está viendo, no todo: el archivo debe parecerse a la pantalla.
            ExportFormat.entries.forEach { format ->
                OutlinedButton(
                    onClick = { onAction(HistoryAction.Export(format)) },
                    enabled = !state.isExporting,
                ) {
                    Text(stringResource(format.labelResource()))
                }
            }
            OutlinedButton(onClick = { onAction(HistoryAction.ConfirmClear) }) {
                Text(stringResource(Res.string.history_clear))
            }
        }
    }
}

/**
 * Confirmación de vaciado.
 *
 * Dice **cuántas** lecturas se van a borrar y que no hay vuelta atrás. Las dos cosas importan: el
 * número convierte una advertencia genérica en un hecho comprobable, y "no se puede deshacer" es
 * literal aquí — sin cuenta, sin nube y sin papelera, el historial es el único sitio donde existen
 * esos datos.
 *
 * El botón de confirmar va en color de error y el de cancelar es el que queda a mano, que es la
 * convención para que el gesto por inercia sea el que no destruye nada.
 */
@Composable
private fun ClearConfirmation(count: Int, onAction: (HistoryAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(HistoryAction.DismissClear) },
        title = { Text(stringResource(Res.string.history_clear_title)) },
        text = { Text(stringResource(Res.string.history_clear_body, count)) },
        confirmButton = {
            TextButton(onClick = { onAction(HistoryAction.Clear) }) {
                Text(
                    text = stringResource(Res.string.history_clear_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(HistoryAction.DismissClear) }) {
                Text(stringResource(Res.string.history_clear_cancel))
            }
        },
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * El ViewModel dice qué pasó; aquí se le pone nombre.
 *
 * Es `suspend` y usa `getString` en lugar de ser `@Composable` con `stringResource`: se la llama
 * desde dentro de un `LaunchedEffect`, que es una corrutina y **no** un contexto composable.
 */
private suspend fun resolve(message: HistoryMessage): String = when (message) {
    HistoryMessage.Copied -> getString(Res.string.message_copied)
    HistoryMessage.CopyFailed -> getString(Res.string.message_copy_failed)
    HistoryMessage.ShareFailed -> getString(Res.string.message_share_failed)
    HistoryMessage.OpenFailed -> getString(Res.string.message_open_failed)
    HistoryMessage.NothingToExport -> getString(Res.string.message_nothing_to_export)
    HistoryMessage.NoteSaved -> getString(Res.string.message_note_saved)
    HistoryMessage.NoteRemoved -> getString(Res.string.message_note_removed)
    HistoryMessage.EntryDeleted -> getString(Res.string.message_entry_deleted)

    // iOS y el navegador no revelan dónde acabó el archivo, así que hay dos mensajes: uno que dice
    // el destino y otro que solo confirma. Fingir una ruta sería peor que no darla.
    is HistoryMessage.Exported ->
        message.location
            ?.let { getString(Res.string.message_exported_to, it) }
            ?: getString(Res.string.message_exported)

    is HistoryMessage.ExportFailed -> message.reason
}

private fun ExportFormat.labelResource(): StringResource = when (this) {
    ExportFormat.Csv -> Res.string.history_export_csv
    ExportFormat.Json -> Res.string.history_export_json
    ExportFormat.Text -> Res.string.history_export_text
}

/**
 * Redacta lo que se copia o se comparte.
 *
 * El dominio dice qué datos son relevantes; el texto se arma aquí, donde están los recursos
 * traducibles. Antes la frase se componía en `ResultActionsFactory`, que era español dentro del
 * dominio (deuda D15).
 */
@Composable
internal fun ShareableContent.asText(): String = when (this) {
    is ShareableContent.Raw -> value

    is ShareableContent.Wifi ->
        password
            ?.let { stringResource(Res.string.share_wifi_with_password, ssid, it) }
            ?: stringResource(Res.string.share_wifi, ssid)

    is ShareableContent.Contact -> parts.joinToString(stringResource(Res.string.share_separator))
}
