package com.testscanner.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Fija el idioma de la interfaz por encima del que dice el sistema.
 *
 * ## Por qué hace falta esto y no basta con `values-es/`
 *
 * Los recursos de Compose resuelven el idioma contra el **entorno**, que por defecto es el del
 * sistema y se calcula una vez. Poner los textos en `values/` (inglés) y `values-es/` (español) hace
 * que la app siga al sistema, que es lo correcto por defecto — pero no da forma de que el usuario
 * elija otro idioma sin cambiar el del teléfono entero.
 *
 * [LocalComposeEnvironment] existe justo para eso: sustituye el entorno que usan `stringResource` y
 * `getString`. Como [ResourceEnvironment] no se puede construir a mano —su constructor es interno de
 * la librería—, el camino es el que documenta JetBrains: cambiar el idioma **de la plataforma** con
 * [ApplyPlatformLanguage] y volver a leer [getSystemResourceEnvironment] con el nuevo valor.
 *
 * El `key(tag)` no es decorativo: fuerza a recomponer el subárbol entero al cambiar de idioma. Sin
 * él, cualquier texto guardado en un `remember` —y hay unos cuantos— se quedaría en el idioma
 * anterior hasta que algo lo invalidara.
 *
 * @param tag etiqueta BCP-47 (`"en"`, `"es"`), o `null` para seguir al sistema.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit) {
    // Se aplica **antes** de leer el entorno: al revés, la primera composición tras el cambio
    // todavía leería el idioma viejo.
    ApplyPlatformLanguage(tag)

    val environment = remember(tag) {
        object : ComposeEnvironment {
            @Composable
            override fun rememberEnvironment(): ResourceEnvironment =
                remember(tag) { getSystemResourceEnvironment() }
        }
    }

    CompositionLocalProvider(LocalComposeEnvironment provides environment) {
        key(tag) { content() }
    }
}

/**
 * Cambia el idioma por defecto de la plataforma.
 *
 * Es un efecto durante la composición y no un `LaunchedEffect` a propósito: tiene que haber ocurrido
 * antes de que [ProvideAppLanguage] lea el entorno, y un efecto diferido corre después.
 */
@Composable
internal expect fun ApplyPlatformLanguage(tag: String?)

/**
 * Si esta plataforma puede honrar un idioma distinto al del sistema.
 *
 * Existe para que la pantalla de Ajustes **no ofrezca un control que no hace nada**. En el navegador
 * el idioma sale de `navigator.language`, que es de solo lectura: no hay forma de cambiarlo desde la
 * página, así que ahí el selector no se muestra y la app sigue al navegador.
 */
expect val PlatformSupportsLanguageOverride: Boolean
