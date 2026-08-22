package com.whyscan.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Escala tipográfica de WhyScan.
//
// Hasta ahora no había ninguna: `MaterialTheme` se quedaba con la de fábrica, que está pensada para
// el catálogo de Material y no para esta app. Dos consecuencias concretas que se veían en pantalla:
// los títulos venían en `Normal` donde esta app quiere `SemiBold`, y el `bodyMedium` con el que se
// pinta **el valor de un código leído** traía `letterSpacing` positivo, que es lo peor posible para
// una tirada de dígitos que alguien va a comparar a ojo con la etiqueta que tiene delante.

/**
 * La familia del sistema, a conciencia, y no una fuente de marca empaquetada.
 *
 * Roboto en Android, San Francisco en iOS y la del navegador en Web ya están optimizadas para cada
 * plataforma, pesan cero en el binario y respetan los ajustes de accesibilidad del usuario.
 * Empaquetar una fuente propia es una decisión de marca que cuesta unos 300 KB por peso y que
 * conviene tomar con la ficha de Play delante, no de pasada.
 */
private val BodyFontFamily = FontFamily.Default

private fun whyScanStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double,
    family: FontFamily = BodyFontFamily,
): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * Monoespaciada para datos: el valor de un código y las latencias.
 *
 * Que `1`, `l` e `I` se distingan no es estética. Quien escanea un lote compara lo que ve en
 * pantalla con lo que hay impreso en la caja, y en una proporcional esos tres glifos se parecen.
 */
val MonoNumbers: FontFamily = FontFamily.Monospace

/**
 * Estilo de los valores leídos. No está en [Typography] porque no es un rol de Material: es un
 * estilo de dominio, y meterlo en `bodyMedium` obligaría a que **todo** el cuerpo fuese mono.
 */
val CodeValueStyle: TextStyle = whyScanStyle(
    size = 16,
    lineHeight = 24,
    weight = FontWeight.Medium,
    letterSpacing = 0.0,
    family = MonoNumbers,
)

internal val WhyScanTypography = Typography(
    // Display: solo la usan los estados vacíos y la pantalla "acerca de". Apretada de tracking,
    // que es lo que hace que un texto grande parezca diseñado y no ampliado.
    displayLarge = whyScanStyle(size = 57, lineHeight = 64, weight = FontWeight.SemiBold, letterSpacing = -0.5),
    displayMedium = whyScanStyle(size = 45, lineHeight = 52, weight = FontWeight.SemiBold, letterSpacing = -0.4),
    displaySmall = whyScanStyle(size = 36, lineHeight = 44, weight = FontWeight.SemiBold, letterSpacing = -0.3),

    headlineLarge = whyScanStyle(size = 32, lineHeight = 40, weight = FontWeight.SemiBold, letterSpacing = -0.2),
    headlineMedium = whyScanStyle(size = 28, lineHeight = 36, weight = FontWeight.SemiBold, letterSpacing = -0.2),
    headlineSmall = whyScanStyle(size = 24, lineHeight = 32, weight = FontWeight.SemiBold, letterSpacing = -0.1),

    // Title: cabeceras de sección y título de la barra superior. `SemiBold` y no `Medium`: con el
    // peso de Material la jerarquía entre un título de sección y el cuerpo casi no se leía.
    titleLarge = whyScanStyle(size = 22, lineHeight = 28, weight = FontWeight.SemiBold, letterSpacing = 0.0),
    titleMedium = whyScanStyle(size = 17, lineHeight = 24, weight = FontWeight.SemiBold, letterSpacing = 0.1),
    titleSmall = whyScanStyle(size = 15, lineHeight = 20, weight = FontWeight.SemiBold, letterSpacing = 0.1),

    // Body: prosa. `letterSpacing` a cero o casi; el 0.5 de Material está pensado para Roboto a
    // tamaños pequeños y aquí solo separaba las palabras sin ganar nada.
    bodyLarge = whyScanStyle(size = 16, lineHeight = 24, weight = FontWeight.Normal, letterSpacing = 0.0),
    bodyMedium = whyScanStyle(size = 14, lineHeight = 20, weight = FontWeight.Normal, letterSpacing = 0.1),
    bodySmall = whyScanStyle(size = 13, lineHeight = 18, weight = FontWeight.Normal, letterSpacing = 0.1),

    // Label: botones, chips y metadatos. Aquí el tracking positivo **sí** ayuda: son textos cortos
    // en mayúscula o casi, donde separar las letras mejora la lectura de golpe.
    labelLarge = whyScanStyle(size = 14, lineHeight = 20, weight = FontWeight.SemiBold, letterSpacing = 0.1),
    labelMedium = whyScanStyle(size = 12, lineHeight = 16, weight = FontWeight.Medium, letterSpacing = 0.4),
    labelSmall = whyScanStyle(size = 11, lineHeight = 16, weight = FontWeight.Medium, letterSpacing = 0.4),
)
