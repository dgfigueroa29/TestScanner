package com.whyscan.core.designsystem

import androidx.compose.runtime.Composable

/**
 * En el navegador no hay nada que aplicar.
 *
 * El idioma sale de `navigator.language`, que la página no puede escribir: no es una limitación de
 * esta app ni de Compose, es cómo funciona la plataforma. Escribir `<html lang>` cambia lo que
 * anuncia el documento a los lectores de pantalla, pero no lo que devuelve `navigator`, así que no
 * movería los recursos ni un milímetro y solo daría la impresión de que algo se hizo.
 *
 * Por eso [PlatformSupportsLanguageOverride] es `false` aquí y la pantalla de Ajustes esconde el
 * selector: en Web la app habla el idioma del navegador, y decirlo es mejor que ofrecer un control
 * inerte.
 */
@Composable
internal actual fun ApplyPlatformLanguage(tag: String?) = Unit

actual val PlatformSupportsLanguageOverride: Boolean = false
