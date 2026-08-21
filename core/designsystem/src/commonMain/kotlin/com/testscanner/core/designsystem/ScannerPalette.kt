package com.testscanner.core.designsystem

/**
 * La paleta de Scanly como **datos**, sin Compose de por medio.
 *
 * Está separada de `Theme.kt` por una razón concreta: así el contraste se puede comprobar con
 * aritmética en `commonTest`, sin renderizar nada ni necesitar dispositivo. RNF-05 exige contraste
 * AA, y hasta que existió `ContrastTest` era una intención escrita en un documento.
 *
 * Los colores son ARGB en `Int`, igual que espera el tipo `Color` de Compose.
 *
 * ## Por qué están **todos** los roles y no solo los seis de siempre
 *
 * Material 3 define una treintena de roles y `lightColorScheme()` rellena con su paleta de fábrica
 * —morados y granates— cada uno que no se le pase. Eso no es teórico: aquí ya había pasado con los
 * `on*`, y seguía pasando con los `*Container`. Un `FilterChip` seleccionado se pinta con
 * `secondaryContainer`; como nadie lo declaraba, **los chips de formato y el de la linterna salían
 * morados** en una app cuya marca es azul. Lo mismo la `Card` (`surfaceContainerLow`), el
 * `NavigationBar` (`surfaceContainer`) y el indicador del ítem activo (`secondaryContainer`).
 *
 * Declararlos todos cuesta un fichero largo y elimina la clase entera de defecto.
 */
object ScannerPalette {

    /** Combinación de un color y el que se pinta encima. La unidad que se mide. */
    data class ColorPair(val name: String, val foreground: Int, val background: Int)

    /**
     * Claro: azul de marca sobre neutros fríos (familia *slate*).
     *
     * El neutro no es gris puro sino ligeramente azulado. Sobre un visor de cámara, que casi siempre
     * trae dominante cálida, un gris frío separa mejor la UI de la imagen.
     */
    object Light {
        const val PRIMARY = 0xFF2563EB.toInt()
        const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        const val PRIMARY_CONTAINER = 0xFFDBE6FF.toInt()
        const val ON_PRIMARY_CONTAINER = 0xFF0A2472.toInt()

        const val SECONDARY = 0xFF0F766E.toInt()
        const val ON_SECONDARY = 0xFFFFFFFF.toInt()
        const val SECONDARY_CONTAINER = 0xFFCCF3EE.toInt()
        const val ON_SECONDARY_CONTAINER = 0xFF04322D.toInt()

        const val TERTIARY = 0xFFB45309.toInt()
        const val ON_TERTIARY = 0xFFFFFFFF.toInt()
        const val TERTIARY_CONTAINER = 0xFFFDECC8.toInt()
        const val ON_TERTIARY_CONTAINER = 0xFF452505.toInt()

        const val ERROR = 0xFFB3261E.toInt()
        const val ON_ERROR = 0xFFFFFFFF.toInt()
        const val ERROR_CONTAINER = 0xFFF9DEDC.toInt()
        const val ON_ERROR_CONTAINER = 0xFF410E0B.toInt()

        const val BACKGROUND = 0xFFF8FAFC.toInt()
        const val ON_BACKGROUND = 0xFF0F172A.toInt()
        const val SURFACE = 0xFFFFFFFF.toInt()
        const val ON_SURFACE = 0xFF0F172A.toInt()
        const val SURFACE_VARIANT = 0xFFE7EBF0.toInt()
        const val ON_SURFACE_VARIANT = 0xFF3F4A5A.toInt()

        // Los cinco niveles de contenedor de Material 3. Son la jerarquía de elevación **por color**
        // que sustituyó a las sombras: una tarjeta dentro de otra se distingue por tono y no por
        // sombra, y eso es justo lo que hace legible una pantalla en modo oscuro.
        const val SURFACE_CONTAINER_LOWEST = 0xFFFFFFFF.toInt()
        const val SURFACE_CONTAINER_LOW = 0xFFF8FAFC.toInt()
        const val SURFACE_CONTAINER = 0xFFF1F5F9.toInt()
        const val SURFACE_CONTAINER_HIGH = 0xFFE9EEF4.toInt()
        const val SURFACE_CONTAINER_HIGHEST = 0xFFE2E8F0.toInt()

        const val OUTLINE = 0xFF6B7789.toInt()
        const val OUTLINE_VARIANT = 0xFFC7CFDA.toInt()
        const val SCRIM = 0xFF000000.toInt()

        // Lo que usa el Snackbar: fondo oscuro en tema claro. Sin declararlos, el mensaje de
        // "Copiado" —el feedback más frecuente de toda la app— salía con la paleta de fábrica.
        const val INVERSE_SURFACE = 0xFF1E293B.toInt()
        const val INVERSE_ON_SURFACE = 0xFFF1F5F9.toInt()
        const val INVERSE_PRIMARY = 0xFF93B4FF.toInt()
    }

    /** Oscuro: el azul se aclara y los neutros bajan a un azul casi negro, no a gris. */
    object Dark {
        const val PRIMARY = 0xFF93B4FF.toInt()
        const val ON_PRIMARY = 0xFF0B1020.toInt()
        const val PRIMARY_CONTAINER = 0xFF1E3A8A.toInt()
        const val ON_PRIMARY_CONTAINER = 0xFFD8E4FF.toInt()

        const val SECONDARY = 0xFF5EEAD4.toInt()
        const val ON_SECONDARY = 0xFF06302B.toInt()
        const val SECONDARY_CONTAINER = 0xFF115E56.toInt()
        const val ON_SECONDARY_CONTAINER = 0xFFB8F5EA.toInt()

        const val TERTIARY = 0xFFFCD34D.toInt()
        const val ON_TERTIARY = 0xFF3A2A00.toInt()
        const val TERTIARY_CONTAINER = 0xFF7A5310.toInt()
        const val ON_TERTIARY_CONTAINER = 0xFFFDEFC4.toInt()

        const val ERROR = 0xFFFFB4AB.toInt()
        const val ON_ERROR = 0xFF601410.toInt()
        const val ERROR_CONTAINER = 0xFF8C1D18.toInt()
        const val ON_ERROR_CONTAINER = 0xFFF9DEDC.toInt()

        const val BACKGROUND = 0xFF0B1020.toInt()
        const val ON_BACKGROUND = 0xFFE6EAF2.toInt()
        const val SURFACE = 0xFF141A2A.toInt()
        const val ON_SURFACE = 0xFFE6EAF2.toInt()
        const val SURFACE_VARIANT = 0xFF232B3E.toInt()
        const val ON_SURFACE_VARIANT = 0xFFC2CADB.toInt()

        const val SURFACE_CONTAINER_LOWEST = 0xFF070B16.toInt()
        const val SURFACE_CONTAINER_LOW = 0xFF141A2A.toInt()
        const val SURFACE_CONTAINER = 0xFF1A2233.toInt()
        const val SURFACE_CONTAINER_HIGH = 0xFF232B3E.toInt()
        const val SURFACE_CONTAINER_HIGHEST = 0xFF2C354A.toInt()

        const val OUTLINE = 0xFF8A94A8.toInt()
        const val OUTLINE_VARIANT = 0xFF3A4459.toInt()
        const val SCRIM = 0xFF000000.toInt()

        const val INVERSE_SURFACE = 0xFFE6EAF2.toInt()
        const val INVERSE_ON_SURFACE = 0xFF141A2A.toInt()

        // Un tono más oscuro que el `primary` del tema claro (`#2563EB`): sobre el `inverseSurface`
        // de este tema —que es casi blanco— aquel se quedaba en 4.29:1 y no llegaba a AA. Es
        // exactamente el par que nadie mira: el botón de acción de un Snackbar en modo oscuro.
        const val INVERSE_PRIMARY = 0xFF1D4ED8.toInt()
    }

    /**
     * Los pares que hay que medir a 4.5:1: no solo los de Material, también los que **la UI usa de
     * hecho**.
     *
     * La distinción importa. Material garantiza que `onPrimary` se lee sobre `primary`, pero las
     * pantallas usan además `primary`, `tertiary` y `error` como **color de texto sobre la
     * tarjeta** —la disponibilidad del motor, el aviso de degradación, el error de sesión—, y esos
     * pares no los cubre ninguna convención. Son justo los que se pueden romper sin que nadie note
     * nada hasta que alguien no los pueda leer.
     */
    fun measuredPairs(): List<ColorPair> =
        pairs("claro", lightRoles(), TEXT_PAIRS) + pairs("oscuro", darkRoles(), TEXT_PAIRS)

    /**
     * Pares que solo tienen que llegar a 3.0:1, el umbral de WCAG para **componentes no textuales**.
     *
     * Aquí vive `outline`, que es el borde de un `OutlinedButton` o de un `OutlinedTextField`: es
     * información —dónde termina el control— pero no es texto, y exigirle 4.5 obligaría a un borde
     * tan oscuro que la UI parecería un formulario de los noventa.
     */
    fun measuredNonTextPairs(): List<ColorPair> =
        pairs("claro", lightRoles(), NON_TEXT_PAIRS) + pairs("oscuro", darkRoles(), NON_TEXT_PAIRS)

    /**
     * Los pares se declaran **una vez** y se aplican a los dos esquemas.
     *
     * Antes la lista estaba escrita dos veces y `los_dos_temas_miden_los_mismos_pares` existía justo
     * para cazar el día en que una de las dos copias se quedara corta. Ahora ese caso no puede
     * ocurrir, y el test sigue valiendo la pena: protege de que alguien vuelva a separarlas.
     *
     * Los nombres son los de Material y no cadenas libres: se resuelven contra el mapa de roles con
     * `getValue`, así que un rol mal escrito revienta el test en vez de saltarse el par en silencio.
     */
    private val TEXT_PAIRS = listOf(
        "onPrimary" to "primary",
        "onPrimaryContainer" to "primaryContainer",
        "onSecondary" to "secondary",
        "onSecondaryContainer" to "secondaryContainer",
        "onTertiary" to "tertiary",
        "onTertiaryContainer" to "tertiaryContainer",
        "onError" to "error",
        "onErrorContainer" to "errorContainer",
        "onBackground" to "background",
        "onSurface" to "surface",
        "onSurfaceVariant" to "surface",
        "onSurfaceVariant" to "surfaceVariant",
        "onSurface" to "surfaceContainerLowest",
        "onSurface" to "surfaceContainerLow",
        "onSurface" to "surfaceContainer",
        "onSurface" to "surfaceContainerHigh",
        "onSurface" to "surfaceContainerHighest",
        "onSurfaceVariant" to "surfaceContainer",
        "onSurfaceVariant" to "surfaceContainerHighest",
        // Los cuatro que ninguna convención de Material cubre: colores de acento usados como
        // **texto** sobre una superficie.
        "primary" to "surface",
        "primary" to "surfaceContainer",
        "tertiary" to "surface",
        "error" to "surface",
        // El Snackbar, que en tema claro es oscuro y al revés.
        "inverseOnSurface" to "inverseSurface",
        "inversePrimary" to "inverseSurface",
    )

    private val NON_TEXT_PAIRS = listOf(
        "outline" to "surface",
        "outline" to "surfaceContainer",
        "outline" to "background",
    )

    private fun pairs(
        theme: String,
        roles: Map<String, Int>,
        spec: List<Pair<String, String>>,
    ): List<ColorPair> = spec.map { (foreground, background) ->
        ColorPair(
            name = "$theme: $foreground sobre $background",
            foreground = roles.getValue(foreground),
            background = roles.getValue(background),
        )
    }

    private fun lightRoles(): Map<String, Int> = with(Light) {
        mapOf(
            "primary" to PRIMARY,
            "onPrimary" to ON_PRIMARY,
            "primaryContainer" to PRIMARY_CONTAINER,
            "onPrimaryContainer" to ON_PRIMARY_CONTAINER,
            "secondary" to SECONDARY,
            "onSecondary" to ON_SECONDARY,
            "secondaryContainer" to SECONDARY_CONTAINER,
            "onSecondaryContainer" to ON_SECONDARY_CONTAINER,
            "tertiary" to TERTIARY,
            "onTertiary" to ON_TERTIARY,
            "tertiaryContainer" to TERTIARY_CONTAINER,
            "onTertiaryContainer" to ON_TERTIARY_CONTAINER,
            "error" to ERROR,
            "onError" to ON_ERROR,
            "errorContainer" to ERROR_CONTAINER,
            "onErrorContainer" to ON_ERROR_CONTAINER,
            "background" to BACKGROUND,
            "onBackground" to ON_BACKGROUND,
            "surface" to SURFACE,
            "onSurface" to ON_SURFACE,
            "surfaceVariant" to SURFACE_VARIANT,
            "onSurfaceVariant" to ON_SURFACE_VARIANT,
            "surfaceContainerLowest" to SURFACE_CONTAINER_LOWEST,
            "surfaceContainerLow" to SURFACE_CONTAINER_LOW,
            "surfaceContainer" to SURFACE_CONTAINER,
            "surfaceContainerHigh" to SURFACE_CONTAINER_HIGH,
            "surfaceContainerHighest" to SURFACE_CONTAINER_HIGHEST,
            "outline" to OUTLINE,
            "outlineVariant" to OUTLINE_VARIANT,
            "inverseSurface" to INVERSE_SURFACE,
            "inverseOnSurface" to INVERSE_ON_SURFACE,
            "inversePrimary" to INVERSE_PRIMARY,
        )
    }

    private fun darkRoles(): Map<String, Int> = with(Dark) {
        mapOf(
            "primary" to PRIMARY,
            "onPrimary" to ON_PRIMARY,
            "primaryContainer" to PRIMARY_CONTAINER,
            "onPrimaryContainer" to ON_PRIMARY_CONTAINER,
            "secondary" to SECONDARY,
            "onSecondary" to ON_SECONDARY,
            "secondaryContainer" to SECONDARY_CONTAINER,
            "onSecondaryContainer" to ON_SECONDARY_CONTAINER,
            "tertiary" to TERTIARY,
            "onTertiary" to ON_TERTIARY,
            "tertiaryContainer" to TERTIARY_CONTAINER,
            "onTertiaryContainer" to ON_TERTIARY_CONTAINER,
            "error" to ERROR,
            "onError" to ON_ERROR,
            "errorContainer" to ERROR_CONTAINER,
            "onErrorContainer" to ON_ERROR_CONTAINER,
            "background" to BACKGROUND,
            "onBackground" to ON_BACKGROUND,
            "surface" to SURFACE,
            "onSurface" to ON_SURFACE,
            "surfaceVariant" to SURFACE_VARIANT,
            "onSurfaceVariant" to ON_SURFACE_VARIANT,
            "surfaceContainerLowest" to SURFACE_CONTAINER_LOWEST,
            "surfaceContainerLow" to SURFACE_CONTAINER_LOW,
            "surfaceContainer" to SURFACE_CONTAINER,
            "surfaceContainerHigh" to SURFACE_CONTAINER_HIGH,
            "surfaceContainerHighest" to SURFACE_CONTAINER_HIGHEST,
            "outline" to OUTLINE,
            "outlineVariant" to OUTLINE_VARIANT,
            "inverseSurface" to INVERSE_SURFACE,
            "inverseOnSurface" to INVERSE_ON_SURFACE,
            "inversePrimary" to INVERSE_PRIMARY,
        )
    }
}
