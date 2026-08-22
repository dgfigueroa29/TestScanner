package com.whyscan.core.domain.scan

import com.whyscan.core.model.BarcodeFamily
import com.whyscan.core.model.BarcodeFormat
import com.whyscan.core.model.BarcodeValueType
import com.whyscan.core.model.GtinChecksum

/**
 * Interpreta el contenido de un código.
 *
 * Vive en el dominio y no se delega al SDK del motor a propósito: si usáramos el parser de ML Kit,
 * el mismo código leído con ZXing devolvería menos información, y la comparación entre motores
 * — que es el objetivo del producto — dejaría de ser justa.
 */
// Una función por tipo de valor: quince funciones pequeñas y sin ramificar, no una clase que
// hace quince cosas. Añadir una simbología con semántica propia añade una función y no toca las
// demás, que es justo lo que se busca.
@Suppress("TooManyFunctions")
object BarcodeValueParser {

    fun parse(rawValue: String, format: BarcodeFormat): BarcodeValueType {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return BarcodeValueType.Text(rawValue)

        if (format.family == BarcodeFamily.ProductLinear) {
            return parseProduct(trimmed)
        }

        return parseUrl(trimmed)
            ?: parseWifi(trimmed)
            ?: parseMailto(trimmed)
            ?: parseTel(trimmed)
            ?: parseSms(trimmed)
            ?: parseGeo(trimmed)
            ?: parseVCard(trimmed)
            ?: parseVEvent(trimmed)
            ?: BarcodeValueType.Text(rawValue)
    }

    private fun parseProduct(value: String): BarcodeValueType =
        if (value.all { it.isDigit() }) {
            BarcodeValueType.Product(gtin = value, checksumValid = GtinChecksum.isValid(value))
        } else {
            BarcodeValueType.Text(value)
        }

    private fun parseUrl(value: String): BarcodeValueType? {
        val lower = value.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") ->
                BarcodeValueType.Url(value)

            lower.startsWith("www.") -> BarcodeValueType.Url("https://$value")
            else -> null
        }
    }

    /** Formato `WIFI:T:WPA;S:mi-red;P:clave;H:true;;` */
    private fun parseWifi(value: String): BarcodeValueType? {
        if (!value.startsWith(WIFI_PREFIX, ignoreCase = true)) return null
        val fields = parseSemicolonFields(value.stripPrefix(WIFI_PREFIX))
        val ssid = fields["S"] ?: return null
        return BarcodeValueType.Wifi(
            ssid = ssid,
            password = fields["P"]?.takeIf { it.isNotEmpty() },
            encryption = when (fields["T"]?.uppercase()) {
                "WPA", "WPA2", "WPA3" -> BarcodeValueType.WifiEncryption.WPA
                "WEP" -> BarcodeValueType.WifiEncryption.WEP
                "SAE" -> BarcodeValueType.WifiEncryption.SAE
                "NOPASS", "", null -> BarcodeValueType.WifiEncryption.OPEN
                else -> BarcodeValueType.WifiEncryption.UNKNOWN
            },
            hidden = fields["H"]?.equals("true", ignoreCase = true) == true,
        )
    }

    private fun parseMailto(value: String): BarcodeValueType? {
        if (!value.startsWith(MAILTO_PREFIX, ignoreCase = true)) return null
        val body = value.stripPrefix(MAILTO_PREFIX)
        val address = body.substringBefore('?')
        val query = body.substringAfter('?', missingDelimiterValue = "")
        val params = parseQuery(query)
        return BarcodeValueType.Email(
            address = address,
            subject = params["subject"],
            body = params["body"],
        )
    }

    private fun parseTel(value: String): BarcodeValueType? =
        if (value.startsWith(TEL_PREFIX, ignoreCase = true)) {
            BarcodeValueType.Phone(value.stripPrefix(TEL_PREFIX))
        } else {
            null
        }

    private fun parseSms(value: String): BarcodeValueType? {
        val prefix = listOf(SMSTO_PREFIX, SMS_PREFIX)
            .firstOrNull { value.startsWith(it, ignoreCase = true) }
            ?: return null
        val body = value.stripPrefix(prefix)
        val separator = if (prefix == SMSTO_PREFIX) ':' else '?'
        return BarcodeValueType.Sms(
            number = body.substringBefore(separator),
            message = body.substringAfter(separator, missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() },
        )
    }

    /** Formato `geo:41.3874,2.1686?q=Barcelona` */
    private fun parseGeo(value: String): BarcodeValueType? {
        if (!value.startsWith(GEO_PREFIX, ignoreCase = true)) return null
        val body = value.stripPrefix(GEO_PREFIX)
        val coordinates = body.substringBefore('?').split(',')
        if (coordinates.size < 2) return null
        val latitude = coordinates[0].toDoubleOrNull() ?: return null
        val longitude = coordinates[1].toDoubleOrNull() ?: return null
        return BarcodeValueType.GeoPoint(
            latitude = latitude,
            longitude = longitude,
            label = parseQuery(body.substringAfter('?', ""))["q"],
        )
    }

    private fun parseVCard(value: String): BarcodeValueType? {
        if (!value.startsWith("BEGIN:VCARD", ignoreCase = true)) return null
        val lines = value.lines().map { it.trim() }
        return BarcodeValueType.ContactInfo(
            formattedName = lines.valueOf("FN") ?: lines.valueOf("N"),
            organization = lines.valueOf("ORG"),
            title = lines.valueOf("TITLE"),
            phones = lines.allValuesOf("TEL"),
            emails = lines.allValuesOf("EMAIL"),
        )
    }

    private fun parseVEvent(value: String): BarcodeValueType? {
        if (!value.contains("BEGIN:VEVENT", ignoreCase = true)) return null
        val lines = value.lines().map { it.trim() }
        return BarcodeValueType.CalendarEvent(
            summary = lines.valueOf("SUMMARY"),
            start = lines.valueOf("DTSTART"),
            end = lines.valueOf("DTEND"),
            location = lines.valueOf("LOCATION"),
        )
    }

    // --- utilidades ---

    /**
     * Divide `T:WPA;S:red;P:clave;;` respetando el escape `\;` que define el estándar de QR WiFi:
     * un SSID puede contener `;` legítimamente.
     */
    private fun parseSemicolonFields(input: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val current = StringBuilder()
        var escaped = false

        fun flush() {
            val chunk = current.toString()
            current.clear()
            val key = chunk.substringBefore(':', missingDelimiterValue = "")
            if (key.isNotEmpty() && chunk.contains(':')) {
                fields[key.uppercase()] = chunk.substringAfter(':')
            }
        }

        input.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' -> escaped = true
                char == ';' -> flush()
                else -> current.append(char)
            }
        }
        flush()
        return fields
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .filter { it.contains('=') }
        .associate { it.substringBefore('=').lowercase() to it.substringAfter('=') }

    private fun List<String>.valueOf(key: String): String? = firstOrNull {
        it.startsWith("$key:", ignoreCase = true) || it.startsWith("$key;", ignoreCase = true)
    }?.substringAfter(':')?.takeIf { it.isNotEmpty() }

    private fun List<String>.allValuesOf(key: String): List<String> = filter {
        it.startsWith("$key:", ignoreCase = true) || it.startsWith("$key;", ignoreCase = true)
    }.mapNotNull { it.substringAfter(':').takeIf(String::isNotEmpty) }

    private fun String.stripPrefix(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private const val WIFI_PREFIX = "WIFI:"
    private const val MAILTO_PREFIX = "mailto:"
    private const val TEL_PREFIX = "tel:"
    private const val SMSTO_PREFIX = "smsto:"
    private const val SMS_PREFIX = "sms:"
    private const val GEO_PREFIX = "geo:"
}
