package com.testscanner.feature.scanner

import androidx.compose.runtime.Composable
import com.testscanner.core.domain.scan.OpenKind
import com.testscanner.core.domain.scan.ResultAction
import com.testscanner.core.domain.scan.ShareableContent
import com.testscanner.feature.scanner.resources.Res
import com.testscanner.feature.scanner.resources.a11y_copy_value
import com.testscanner.feature.scanner.resources.a11y_open_value
import com.testscanner.feature.scanner.resources.a11y_share_value
import com.testscanner.feature.scanner.resources.result_copy
import com.testscanner.feature.scanner.resources.result_open_email
import com.testscanner.feature.scanner.resources.result_open_link
import com.testscanner.feature.scanner.resources.result_open_map
import com.testscanner.feature.scanner.resources.result_open_phone
import com.testscanner.feature.scanner.resources.result_open_sms
import com.testscanner.feature.scanner.resources.result_share
import com.testscanner.feature.scanner.resources.share_separator
import com.testscanner.feature.scanner.resources.share_wifi
import com.testscanner.feature.scanner.resources.share_wifi_with_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Cómo se nombran las acciones sobre un resultado (RF-13).
//
// Están en su propio archivo porque las usan la hoja de resultados y el banco de motores, que ya no
// viven en el mismo fichero. Son `internal` y no `private` justo por eso.

/** Cómo la anuncia un lector de pantalla, con el valor dentro para poder distinguir un botón de otro. */
internal fun ResultAction.spokenResource(): StringResource = when (this) {
    ResultAction.Copy -> Res.string.a11y_copy_value
    ResultAction.Share -> Res.string.a11y_share_value
    is ResultAction.Open -> Res.string.a11y_open_value
}

/** Cómo se llama en pantalla cada acción sobre el resultado (RF-13). */
internal fun ResultAction.labelResource(): StringResource = when (this) {
    ResultAction.Copy -> Res.string.result_copy
    ResultAction.Share -> Res.string.result_share
    is ResultAction.Open -> when (kind) {
        OpenKind.Link -> Res.string.result_open_link
        OpenKind.Email -> Res.string.result_open_email
        OpenKind.Phone -> Res.string.result_open_phone
        OpenKind.Sms -> Res.string.result_open_sms
        OpenKind.Map -> Res.string.result_open_map
    }
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
