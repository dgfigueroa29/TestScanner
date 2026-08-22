package com.whyscan.core.model

/**
 * Interpretación semántica del contenido de un código.
 *
 * El parseo lo hace el dominio ([com.whyscan.core.model] + `BarcodeValueParser`) y **no** el
 * SDK del motor. Si delegáramos en el parser de ML Kit, el mismo código leído con ZXing tendría
 * menos información, y la comparación entre motores dejaría de ser justa — que es precisamente el
 * objetivo del producto.
 */
sealed interface BarcodeValueType {

    /**
     * Identificador estable del tipo, para exportar y para logs.
     *
     * Existe por el mismo motivo que [BarcodeFormat.id] y no es cosmético: la alternativa obvia
     * —`valueType::class.simpleName`— devuelve el nombre **ofuscado** en una build de release con
     * R8, así que el historial exportado diría `"a"` en vez de `"Url"`. Un identificador escrito a
     * mano no depende de cómo se llame la clase ni de si alguien la renombra.
     */
    val id: String

    /** Texto sin estructura reconocida. Es el tipo por defecto. */
    data class Text(val value: String) : BarcodeValueType {
        override val id: String get() = "TEXT"
    }

    data class Url(val url: String, val title: String? = null) : BarcodeValueType {
        override val id: String get() = "URL"
    }

    data class Email(val address: String, val subject: String? = null, val body: String? = null) :
        BarcodeValueType {
        override val id: String get() = "EMAIL"
    }

    data class Phone(val number: String) : BarcodeValueType {
        override val id: String get() = "PHONE"
    }

    data class Sms(val number: String, val message: String? = null) : BarcodeValueType {
        override val id: String get() = "SMS"
    }

    data class Wifi(
        val ssid: String,
        val password: String?,
        val encryption: WifiEncryption,
        val hidden: Boolean = false,
    ) : BarcodeValueType {
        override val id: String get() = "WIFI"
    }

    data class GeoPoint(val latitude: Double, val longitude: Double, val label: String? = null) :
        BarcodeValueType {
        override val id: String get() = "GEO"
    }

    data class ContactInfo(
        val formattedName: String?,
        val organization: String? = null,
        val title: String? = null,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
    ) : BarcodeValueType {
        override val id: String get() = "CONTACT"
    }

    data class CalendarEvent(
        val summary: String?,
        val start: String?,
        val end: String?,
        val location: String? = null,
    ) : BarcodeValueType {
        override val id: String get() = "CALENDAR_EVENT"
    }

    /** Código de producto (GTIN). [gtin] queda normalizado a 13 o 14 dígitos cuando es posible. */
    data class Product(val gtin: String, val checksumValid: Boolean) : BarcodeValueType {
        override val id: String get() = "PRODUCT"
    }

    enum class WifiEncryption { OPEN, WEP, WPA, SAE, UNKNOWN }
}
