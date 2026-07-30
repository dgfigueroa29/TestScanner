package com.testscanner.core.model

/**
 * Interpretación semántica del contenido de un código.
 *
 * El parseo lo hace el dominio ([com.testscanner.core.model] + `BarcodeValueParser`) y **no** el
 * SDK del motor. Si delegáramos en el parser de ML Kit, el mismo código leído con ZXing tendría
 * menos información, y la comparación entre motores dejaría de ser justa — que es precisamente el
 * objetivo del producto.
 */
sealed interface BarcodeValueType {

    /** Texto sin estructura reconocida. Es el tipo por defecto. */
    data class Text(val value: String) : BarcodeValueType

    data class Url(val url: String, val title: String? = null) : BarcodeValueType

    data class Email(val address: String, val subject: String? = null, val body: String? = null) :
        BarcodeValueType

    data class Phone(val number: String) : BarcodeValueType

    data class Sms(val number: String, val message: String? = null) : BarcodeValueType

    data class Wifi(
        val ssid: String,
        val password: String?,
        val encryption: WifiEncryption,
        val hidden: Boolean = false,
    ) : BarcodeValueType

    data class GeoPoint(val latitude: Double, val longitude: Double, val label: String? = null) :
        BarcodeValueType

    data class ContactInfo(
        val formattedName: String?,
        val organization: String? = null,
        val title: String? = null,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
    ) : BarcodeValueType

    data class CalendarEvent(
        val summary: String?,
        val start: String?,
        val end: String?,
        val location: String? = null,
    ) : BarcodeValueType

    /** Código de producto (GTIN). [gtin] queda normalizado a 13 o 14 dígitos cuando es posible. */
    data class Product(val gtin: String, val checksumValid: Boolean) : BarcodeValueType

    enum class WifiEncryption { OPEN, WEP, WPA, SAE, UNKNOWN }
}
