package news.app.server

import java.net.URI
import java.security.MessageDigest

/** The port news-server-go serves on when the address names none. */
const val DEFAULT_SERVER_PORT = 56399

/**
 * What the enrollment QR code (`news-server enroll-code`) holds: `news://enroll?code=…&server=…&fingerprint=…`. [server]
 * is the server's public_url (or the router's outside address), absent when it has none; [fingerprint] the SHA-256 of
 * its certificate's key, in hex, absent from an older server. [key] (its DHT key, hex) and [addresses] (each `address=`,
 * up to 6) are for finding the server when its address changes, as the journal app does; kept, not used yet.
 */
data class EnrollmentLink(
    val code: String,
    val server: String?,
    val fingerprint: String?,
    val key: String? = null,
    val addresses: List<String> = emptyList(),
)

/** [text] as an [EnrollmentLink]; null when it isn't one (another QR code, a web page). */
fun parseEnrollmentLink(text: String): EnrollmentLink? {
    val uri = runCatching { URI(withoutInvisibles(text).trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("news", ignoreCase = true) || !uri.host.equals("enroll", ignoreCase = true)) return null
    val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { part ->
        decode(part.substringBefore('=')) to decode(part.substringAfter('=', ""))
    }
    fun first(name: String) = query.firstOrNull { it.first == name }?.second?.takeIf { it.isNotBlank() }
    val code = first("code") ?: return null
    return EnrollmentLink(
        code = code,
        server = first("server"),
        fingerprint = first("fingerprint")?.lowercase()?.takeIf { it.matches(FINGERPRINT) },
        key = first("key")?.lowercase()?.takeIf { it.matches(HEX) },
        addresses = query.filter { it.first == "address" && it.second.isNotBlank() }.map { it.second }.take(MAX_ADDRESSES),
    )
}

/**
 * The enrollment link inside [text]: a message the link was pasted from, with words or spaces around it, or the link
 * alone. Null when there is none.
 */
fun findEnrollmentLink(text: String): EnrollmentLink? =
    LINK.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'', '>')?.let(::parseEnrollmentLink)

private val LINK = Regex("""news://enroll\?\S+""", RegexOption.IGNORE_CASE)

private val HEX = Regex("[0-9a-f]+")

private const val MAX_ADDRESSES = 6

private val FINGERPRINT = Regex("[0-9a-f]{64}")

private fun decode(s: String) = java.net.URLDecoder.decode(s, Charsets.UTF_8.name())

/**
 * [text] without the invisible characters a paste or a right-to-left keyboard adds around it: direction marks
 * (U+200E, U+200F, U+202A..U+202E, U+2066..U+2069), zero-width spaces and joiners, a byte order mark. Typed into the
 * Hebrew screen, `https://…` arrives wrapped in them, and a URL with one isn't a URL.
 */
fun withoutInvisibles(text: String): String = text.filterNot { Character.getType(it) == Character.FORMAT.toInt() }

/**
 * The server's base URL for an address as typed or in a link: `192.168.50.2:8443`, `myhome.example.org`,
 * `https://myhome.example.org:56399/`. Always https; [DEFAULT_SERVER_PORT] when no port is given; no path. Null when it
 * isn't an address.
 */
fun serverUrl(address: String): String? {
    val trimmed = withoutInvisibles(address).trim().trimEnd('/')
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host ?: return null
    val port = if (uri.port == -1) DEFAULT_SERVER_PORT else uri.port
    return "https://${if (':' in host && !host.startsWith('[')) "[$host]" else host}:$port"
}

/** How an address is shown: without `https://`, and without the port when it is the default one. */
fun displayAddress(url: String): String = url.removePrefix("https://").removeSuffix(":$DEFAULT_SERVER_PORT")

/** The GraphQL endpoint of the server at [url] (from [serverUrl]). */
fun graphqlUrl(url: String): String = "$url/query"

/** SHA-256 of a certificate's key ([spki]: its DER SubjectPublicKeyInfo), in lowercase hex: what the app pins. */
fun fingerprintOf(spki: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(spki).joinToString("") { "%02x".format(it) }

/**
 * The first 16 hex digits of a [fingerprintOf] in groups of four, as `news-server enroll-code` prints it as "Key":
 * `abcd ef01 2345 6789`.
 */
fun shortKey(fingerprint: String): String = fingerprint.take(16).chunked(4).joinToString(" ")
