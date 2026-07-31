package com.testscanner.core.domain.scan

import com.testscanner.core.model.Barcode
import com.testscanner.core.model.BarcodeValueType

/** Algo que el usuario puede hacer con un código ya leído (RF-13). */
sealed interface ResultAction {

    /** Etiqueta para el botón. Vive en el dominio porque depende de *qué* es el valor. */
    val label: String

    data object Copy : ResultAction {
        override val label: String get() = "Copiar"
    }

    data object Share : ResultAction {
        override val label: String get() = "Compartir"
    }

    /**
     * Abrir el contenido en la app que corresponda.
     *
     * [uri] no siempre es el `rawValue`: un teléfono leído como texto plano no se puede abrir, pero
     * el mismo valor interpretado como [BarcodeValueType.Phone] se abre con `tel:`. Esa traducción
     * es justamente lo que aporta el parseo semántico del dominio.
     */
    data class Open(val uri: String, override val label: String) : ResultAction
}

/**
 * Decide qué se puede hacer con un código, a partir de **lo que significa** y no de su formato.
 *
 * Es lógica pura sobre [BarcodeValueType], así que se testea sin plataforma. La ejecución — el
 * portapapeles, la hoja de compartir, abrir el enlace — la hace `PlatformActions` en `:core:platform`;
 * aquí solo se decide qué ofrecer.
 */
object ResultActionsFactory {

    /**
     * @param canShare si el sistema tiene hoja de compartir. En escritorio no la hay, y ofrecer un
     *   botón que no hace nada es peor que no ofrecerlo.
     */
    fun actionsFor(barcode: Barcode, canShare: Boolean): List<ResultAction> = buildList {
        openActionFor(barcode.valueType)?.let(::add)
        // Copiar siempre tiene sentido: sea lo que sea, el usuario puede querer pegarlo.
        add(ResultAction.Copy)
        if (canShare) add(ResultAction.Share)
    }

    /** Texto que se copia o comparte. Para un WiFi no es el QR crudo sino algo legible. */
    fun shareableText(barcode: Barcode): String = when (val value = barcode.valueType) {
        is BarcodeValueType.Wifi -> buildString {
            append("Red: ${value.ssid}")
            value.password?.let { append(" · Clave: $it") }
        }

        is BarcodeValueType.ContactInfo -> listOfNotNull(
            value.formattedName,
            value.organization,
            value.phones.firstOrNull(),
            value.emails.firstOrNull(),
        ).joinToString(" · ").ifEmpty { barcode.rawValue }

        // Para todo lo demás el valor crudo es lo que el usuario espera pegar.
        else -> barcode.rawValue
    }

    private fun openActionFor(value: BarcodeValueType): ResultAction.Open? = when (value) {
        is BarcodeValueType.Url -> ResultAction.Open(value.url, "Abrir enlace")

        is BarcodeValueType.Email -> ResultAction.Open(
            uri = buildString {
                append("mailto:${value.address}")
                value.subject?.let { append("?subject=$it") }
            },
            label = "Escribir",
        )

        is BarcodeValueType.Phone -> ResultAction.Open("tel:${value.number}", "Llamar")

        is BarcodeValueType.Sms -> ResultAction.Open("sms:${value.number}", "Enviar SMS")

        is BarcodeValueType.GeoPoint -> ResultAction.Open(
            uri = "geo:${value.latitude},${value.longitude}",
            label = "Ver en el mapa",
        )

        // Un GTIN no es una URL. Buscarlo en un catálogo de productos sería inventar un destino que
        // el código no contiene, así que solo se copia o se comparte.
        is BarcodeValueType.Product -> null

        // Wifi, vCard, evento y texto no tienen un esquema estándar que abrir desde un enlace.
        else -> null
    }
}
