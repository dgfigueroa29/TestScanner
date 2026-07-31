package com.testscanner.core.designsystem

/**
 * La paleta como **datos**, sin Compose de por medio.
 *
 * Está separada de `Theme.kt` por una razón concreta: así el contraste se puede comprobar con
 * aritmética en `commonTest`, sin renderizar nada ni necesitar dispositivo. RNF-05 exige contraste
 * AA, y hasta ahora era una intención escrita en un documento que ningún test defendía.
 *
 * Los colores son ARGB en `Int`, igual que espera el tipo `Color` de Compose.
 */
object ScannerPalette {

    /** Combinación de un color y el que se pinta encima. La unidad que se mide. */
    data class ColorPair(val name: String, val foreground: Int, val background: Int)

    object Light {
        const val PRIMARY = 0xFF2563EB.toInt()
        const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        const val SECONDARY = 0xFF0F766E.toInt()
        const val ON_SECONDARY = 0xFFFFFFFF.toInt()
        const val TERTIARY = 0xFFB45309.toInt()
        const val ON_TERTIARY = 0xFFFFFFFF.toInt()
        const val BACKGROUND = 0xFFF8FAFC.toInt()
        const val ON_BACKGROUND = 0xFF0F172A.toInt()
        const val SURFACE = 0xFFFFFFFF.toInt()
        const val ON_SURFACE = 0xFF0F172A.toInt()
        const val SURFACE_VARIANT = 0xFFE7EBF0.toInt()
        const val ON_SURFACE_VARIANT = 0xFF3F4A5A.toInt()
        const val ERROR = 0xFFB3261E.toInt()
        const val ON_ERROR = 0xFFFFFFFF.toInt()
    }

    object Dark {
        const val PRIMARY = 0xFF93B4FF.toInt()
        const val ON_PRIMARY = 0xFF0B1020.toInt()
        const val SECONDARY = 0xFF5EEAD4.toInt()
        const val ON_SECONDARY = 0xFF06302B.toInt()
        const val TERTIARY = 0xFFFCD34D.toInt()
        const val ON_TERTIARY = 0xFF3A2A00.toInt()
        const val BACKGROUND = 0xFF0B1020.toInt()
        const val ON_BACKGROUND = 0xFFE6EAF2.toInt()
        const val SURFACE = 0xFF141A2A.toInt()
        const val ON_SURFACE = 0xFFE6EAF2.toInt()
        const val SURFACE_VARIANT = 0xFF232B3E.toInt()
        const val ON_SURFACE_VARIANT = 0xFFC2CADB.toInt()
        const val ERROR = 0xFFFFB4AB.toInt()
        const val ON_ERROR = 0xFF601410.toInt()
    }

    /**
     * Los pares que hay que medir: no solo los de Material, también los que **la UI usa de hecho**.
     *
     * La distinción importa. Material garantiza que `onPrimary` se lee sobre `primary`, pero las
     * pantallas usan además `primary`, `tertiary` y `error` como **color de texto sobre la
     * tarjeta** —la disponibilidad del motor, el aviso de degradación, el error de sesión—, y esos
     * pares no los cubre ninguna convención. Son justo los que se pueden romper sin que nadie note
     * nada hasta que alguien no los pueda leer.
     */
    fun measuredPairs(): List<ColorPair> = light() + dark()

    private fun light(): List<ColorPair> = with(Light) {
        listOf(
            ColorPair("claro: onPrimary sobre primary", ON_PRIMARY, PRIMARY),
            ColorPair("claro: onSecondary sobre secondary", ON_SECONDARY, SECONDARY),
            ColorPair("claro: onTertiary sobre tertiary", ON_TERTIARY, TERTIARY),
            ColorPair("claro: onError sobre error", ON_ERROR, ERROR),
            ColorPair("claro: onSurface sobre surface", ON_SURFACE, SURFACE),
            ColorPair("claro: onBackground sobre background", ON_BACKGROUND, BACKGROUND),
            ColorPair("claro: onSurfaceVariant sobre surface", ON_SURFACE_VARIANT, SURFACE),
            ColorPair("claro: onSurfaceVariant sobre surfaceVariant", ON_SURFACE_VARIANT, SURFACE_VARIANT),
            ColorPair("claro: primary como texto sobre surface", PRIMARY, SURFACE),
            ColorPair("claro: tertiary como texto sobre surface", TERTIARY, SURFACE),
            ColorPair("claro: error como texto sobre surface", ERROR, SURFACE),
        )
    }

    private fun dark(): List<ColorPair> = with(Dark) {
        listOf(
            ColorPair("oscuro: onPrimary sobre primary", ON_PRIMARY, PRIMARY),
            ColorPair("oscuro: onSecondary sobre secondary", ON_SECONDARY, SECONDARY),
            ColorPair("oscuro: onTertiary sobre tertiary", ON_TERTIARY, TERTIARY),
            ColorPair("oscuro: onError sobre error", ON_ERROR, ERROR),
            ColorPair("oscuro: onSurface sobre surface", ON_SURFACE, SURFACE),
            ColorPair("oscuro: onBackground sobre background", ON_BACKGROUND, BACKGROUND),
            ColorPair("oscuro: onSurfaceVariant sobre surface", ON_SURFACE_VARIANT, SURFACE),
            ColorPair("oscuro: onSurfaceVariant sobre surfaceVariant", ON_SURFACE_VARIANT, SURFACE_VARIANT),
            ColorPair("oscuro: primary como texto sobre surface", PRIMARY, SURFACE),
            ColorPair("oscuro: tertiary como texto sobre surface", TERTIARY, SURFACE),
            ColorPair("oscuro: error como texto sobre surface", ERROR, SURFACE),
        )
    }
}
