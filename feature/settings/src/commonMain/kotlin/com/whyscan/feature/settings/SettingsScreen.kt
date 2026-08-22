package com.whyscan.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whyscan.core.designsystem.Spacing
import com.whyscan.core.designsystem.WhyScanMark
import com.whyscan.core.domain.repository.AppLanguage
import com.whyscan.core.domain.repository.ThemeMode
import com.whyscan.feature.settings.resources.Res
import com.whyscan.feature.settings.resources.a11y_language_option
import com.whyscan.feature.settings.resources.a11y_theme_option
import com.whyscan.feature.settings.resources.settings_about
import com.whyscan.feature.settings.resources.settings_advanced
import com.whyscan.feature.settings.resources.settings_advanced_mode
import com.whyscan.feature.settings.resources.settings_advanced_mode_hint
import com.whyscan.feature.settings.resources.settings_appearance
import com.whyscan.feature.settings.resources.settings_language
import com.whyscan.feature.settings.resources.settings_language_english
import com.whyscan.feature.settings.resources.settings_language_spanish
import com.whyscan.feature.settings.resources.settings_language_system
import com.whyscan.feature.settings.resources.settings_privacy
import com.whyscan.feature.settings.resources.settings_theme
import com.whyscan.feature.settings.resources.settings_theme_dark
import com.whyscan.feature.settings.resources.settings_theme_hint
import com.whyscan.feature.settings.resources.settings_theme_light
import com.whyscan.feature.settings.resources.settings_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun SettingsContent(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            SettingsSection(stringResource(Res.string.settings_appearance)) {
                ThemePicker(state.preferences.themeMode, onAction)

                Text(
                    text = stringResource(Res.string.settings_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.canChooseLanguage) {
                    LanguagePicker(state.preferences.language, onAction)
                }
            }
        }

        item {
            SettingsSection(stringResource(Res.string.settings_advanced)) {
                // Etiqueta e interruptor se fusionan en un solo nodo de accesibilidad: por
                // separado, un lector de pantalla enfoca el Switch y dice "activado" sin decir
                // activado *qué*. Es el mismo criterio que en el escaneo continuo.
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_advanced_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.preferences.advancedMode,
                        onCheckedChange = { onAction(SettingsAction.SetAdvancedMode(it)) },
                    )
                }

                Text(
                    text = stringResource(Res.string.settings_advanced_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(stringResource(Res.string.settings_about)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WhyScanMark,
                        // Decorativo: lo que dice el icono ya lo dice el texto de al lado, y
                        // anunciarlo dos veces solo alarga el recorrido del lector de pantalla.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.xl),
                    )
                    Text(
                        text = stringResource(Res.string.settings_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onAction: (SettingsAction) -> Unit) {
    val spoken = stringResource(Res.string.a11y_theme_option, stringResource(selected.labelResource()))

    OptionRow(
        title = stringResource(Res.string.settings_theme),
        options = ThemeMode.entries,
        selected = selected,
        label = { stringResource(it.labelResource()) },
        // La descripción va en la fila y no en cada chip: un grupo de opciones excluyentes se
        // anuncia mejor una vez con el valor elegido que tres veces con las tres alternativas.
        spokenState = spoken,
        onSelect = { onAction(SettingsAction.SetThemeMode(it)) },
    )
}

@Composable
private fun LanguagePicker(selected: AppLanguage, onAction: (SettingsAction) -> Unit) {
    val spoken = stringResource(Res.string.a11y_language_option, stringResource(selected.labelResource()))

    OptionRow(
        title = stringResource(Res.string.settings_language),
        options = AppLanguage.entries,
        selected = selected,
        label = { stringResource(it.labelResource()) },
        spokenState = spoken,
        onSelect = { onAction(SettingsAction.SetLanguage(it)) },
    )
}

/**
 * Grupo de opciones excluyentes como chips.
 *
 * Chips y no un `RadioButton` por fila: son tres opciones cortas que caben en una línea, y verlas a
 * la vez es lo que hace obvio que son alternativas de lo mismo. Una lista de radios ocuparía tres
 * filas para decir menos.
 */
@Composable
private fun <T> OptionRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    spokenState: String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.semantics { contentDescription = spokenState },
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    // El check no es redundante con el color: distingue lo elegido sin depender de
                    // que se perciba la diferencia de tono (RNF-05).
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * Bloque de ajustes: título fuera de la tarjeta y contenido dentro.
 *
 * El título fuera y no dentro por una razón de lectura: así la columna de títulos queda alineada con
 * el margen de la pantalla y se puede recorrer de un vistazo, en vez de estar sangrada dentro de
 * cajas del mismo color.
 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

private fun ThemeMode.labelResource(): StringResource = when (this) {
    ThemeMode.System -> Res.string.settings_theme_system
    ThemeMode.Light -> Res.string.settings_theme_light
    ThemeMode.Dark -> Res.string.settings_theme_dark
}

private fun AppLanguage.labelResource(): StringResource = when (this) {
    AppLanguage.System -> Res.string.settings_language_system
    AppLanguage.English -> Res.string.settings_language_english
    AppLanguage.Spanish -> Res.string.settings_language_spanish
}
