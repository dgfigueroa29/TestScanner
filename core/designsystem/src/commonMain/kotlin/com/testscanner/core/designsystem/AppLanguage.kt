package com.testscanner.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

/**
 * Fija el idioma de la interfaz por encima del que dice el sistema.
 *
 * ## Por qué hace falta esto y no basta con `values-es/`
 *
 * Poner los textos en `values/` (inglés) y `values-es/` (español) hace que la app siga al sistema,
 * que es lo correcto por defecto — pero no da forma de que el usuario elija otro idioma sin cambiar
 * el del teléfono entero.
 *
 * ## Cómo funciona, y por qué no como parecía
 *
 * El primer intento fue sustituir el entorno de recursos con `LocalComposeEnvironment`, que es lo
 * que documentan varios ejemplos. **No compila con Compose Multiplatform 1.11.1**: tanto
 * `ComposeEnvironment` como su `CompositionLocal` son `internal` a la librería.
 *
 *     Cannot access 'interface ComposeEnvironment : Any': it is internal in file.
 *
 * Así que se va por donde la librería sí mira. `stringResource` resuelve el idioma leyendo
 * `androidx.compose.ui.text.intl.Locale.current`, y lo hace dentro de un `remember` cuya clave es
 * ese mismo locale. Eso deja dos cosas que hacer, y las dos son necesarias:
 *
 *  1. **Cambiar el locale de la plataforma** ([ApplyPlatformLanguage]), que es de donde
 *     `Locale.current` saca su valor en cada plataforma.
 *  2. **Tirar el subárbol entero** con `key(tag)`. Sin esto, los `remember` internos de cada
 *     `stringResource` conservarían el texto del idioma anterior hasta que algo los invalidara —y
 *     nada lo haría, porque para Compose no ha cambiado ningún estado observable.
 *
 * @param tag etiqueta BCP-47 (`"en"`, `"es"`), o `null` para seguir al sistema.
 */
@Composable
fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit) {
    // Se aplica antes de componer el contenido: al revés, la primera composición tras el cambio
    // todavía leería el idioma viejo.
    ApplyPlatformLanguage(tag)

    key(tag) { content() }
}

/**
 * Cambia el idioma por defecto de la plataforma.
 *
 * Es un efecto durante la composición y no un `LaunchedEffect` a propósito: tiene que haber ocurrido
 * antes de que el contenido lea un solo texto, y un efecto diferido corre después.
 */
@Composable
internal expect fun ApplyPlatformLanguage(tag: String?)

/**
 * Si esta plataforma puede honrar un idioma distinto al del sistema **en caliente**.
 *
 * Existe para que la pantalla de Ajustes **no ofrezca un control que no hace nada**. En el navegador
 * no puede: el idioma sale de `navigator.language`, que es de solo lectura desde la página. Ahí el
 * selector no se muestra y la app sigue al navegador.
 */
expect val PlatformSupportsLanguageOverride: Boolean
