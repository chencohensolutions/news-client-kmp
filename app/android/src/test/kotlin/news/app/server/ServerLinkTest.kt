package news.app.server

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServerLinkTest {

    private val fingerprint = "903ed60d03a8101e46d164881f9b181197c1b8b2da9ae7755935377f3c0143f7"

    @Test
    fun readsTheEnrollmentLinkAsTheServerWritesIt() {
        // news-server-go's enrollmentLink: url.Values.Encode, keys sorted, the server's URL escaped.
        val link = parseEnrollmentLink("news://enroll?code=K7QM-3XRP&fingerprint=$fingerprint&server=https%3A%2F%2Fmyhome.example.org%3A56399")
        assertEquals(EnrollmentLink("K7QM-3XRP", "https://myhome.example.org:56399", fingerprint), link)
    }

    @Test
    fun aLinkMayLackTheServerAndTheKey() {
        assertEquals(EnrollmentLink("K7QM-3XRP", null, null), parseEnrollmentLink("news://enroll?code=K7QM-3XRP"))
        // A key that isn't 64 hex digits is no key: the app then asks the owner to confirm the one it sees.
        assertEquals(EnrollmentLink("K7QM-3XRP", null, null), parseEnrollmentLink("news://enroll?code=K7QM-3XRP&fingerprint=abcd"))
        assertEquals(fingerprint, parseEnrollmentLink("news://enroll?code=X&fingerprint=${fingerprint.uppercase()}")?.fingerprint)
    }

    @Test
    fun otherCodesAreNotEnrollmentLinks() {
        assertNull(parseEnrollmentLink("https://alpha.example/"))
        assertNull(parseEnrollmentLink("journal://enroll?code=K7QM-3XRP"))
        assertNull(parseEnrollmentLink("news://enroll?server=https%3A%2F%2Fa"))
        assertNull(parseEnrollmentLink("not a link"))
    }

    @Test
    fun aPastedLinkIsFoundInTheTextAroundIt() {
        val link = "news://enroll?code=Y7ME-9ADT&fingerprint=$fingerprint&server=https%3A%2F%2F203.0.113.7%3A56400"
        val expected = EnrollmentLink("Y7ME-9ADT", "https://203.0.113.7:56400", fingerprint)
        assertEquals(expected, findEnrollmentLink("  $link\n"))
        assertEquals(expected, findEnrollmentLink("Enrollment link (tap it on the phone):\n$link\n\nCode Y7ME-9ADT expires at 23:11."))
        // Quoted, or ending a sentence.
        assertEquals(expected, findEnrollmentLink("\"$link\"."))
        assertEquals(expected, findEnrollmentLink("הקישור: $link"))
        assertNull(findEnrollmentLink("192.168.50.2"))
        assertNull(findEnrollmentLink("Y7ME-9ADT"))
    }

    @Test
    fun theDhtKeyAndTheAddressesAreKept() {
        val link = parseEnrollmentLink(
            "news://enroll?address=https%3A%2F%2F203.0.113.7%3A56400&address=https%3A%2F%2F192.168.50.150%3A56399" +
                "&code=K7QM-3XRP&fingerprint=$fingerprint&key=AB12cd34&server=https%3A%2F%2F203.0.113.7%3A56400",
        )
        assertEquals("ab12cd34", link?.key)
        assertEquals(listOf("https://203.0.113.7:56400", "https://192.168.50.150:56399"), link?.addresses)
        // A key that isn't hex is no key.
        assertNull(parseEnrollmentLink("news://enroll?code=X&key=zz")?.key)
    }

    @Test
    fun anAddressBecomesTheServersUrl() {
        assertEquals("https://192.168.50.2:56399", serverUrl("192.168.50.2"))
        assertEquals("https://192.168.50.2:8443", serverUrl(" 192.168.50.2:8443 "))
        assertEquals("https://myhome.example.org:56399", serverUrl("https://myhome.example.org/"))
        assertEquals("https://myhome.example.org:56399", serverUrl("https://myhome.example.org:56399/query"))
        assertEquals("https://203.0.113.7:56401", serverUrl("https://203.0.113.7:56401"))
        // Typed or pasted into the right-to-left screen: wrapped in direction marks and a zero-width space.
        assertEquals("https://203.0.113.7:56401", serverUrl("\u200Ehttps://203.0.113.7:56401\u200F"))
        assertEquals("https://203.0.113.7:56401", serverUrl("\u202Ahttps://203.0.113.7:56401\u202C\u200B"))
        assertEquals("https://203.0.113.7:56401", serverUrl("\u2066203.0.113.7:56401\u2069"))
        assertNull(serverUrl(""))
        assertNull(serverUrl("http://192.168.50.2"))
        assertNull(serverUrl("my home"))
    }

    @Test
    fun anAddressIsShownWithoutTheSchemeAndTheDefaultPort() {
        assertEquals("192.168.50.2", displayAddress("https://192.168.50.2:56399"))
        assertEquals("192.168.50.2:8443", displayAddress("https://192.168.50.2:8443"))
        assertEquals("https://192.168.50.2:8443/query", graphqlUrl("https://192.168.50.2:8443"))
    }

    @Test
    fun theKeyIsShownAsEnrollCodePrintsIt() {
        assertEquals("903e d60d 03a8 101e", shortKey(fingerprint))
        // SHA-256 of "abc", the standard's test vector: lowercase hex.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", fingerprintOf("abc".toByteArray()))
    }

    @Test
    fun thePinnedKeyIsTheOnlyOneTrusted() {
        // TEST_CERTIFICATE's key, by openssl: x509 -pubkey | pkey -pubin -outform der | sha256.
        PinnedTrustManager(fingerprint).checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
        PinnedTrustManager(fingerprint.uppercase()).checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
        assertFailsWith<WrongServerKeyException> {
            PinnedTrustManager("0".repeat(64)).checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
        }
        assertFailsWith<CertificateException> { PinnedTrustManager(fingerprint).checkServerTrusted(emptyArray(), "ECDHE_ECDSA") }
    }

    @Test
    fun aTypedCodeSeesTheServersKey() {
        val seeing = KeySeeingTrustManager()
        assertNull(seeing.seen)
        seeing.checkServerTrusted(arrayOf(testCertificate()), "ECDHE_ECDSA")
        assertEquals(fingerprint, seeing.seen)
    }
}

/** A self-signed EC P-256 certificate, as news-server-go's scripts make them (made with openssl for the tests). */
internal const val TEST_CERTIFICATE = """-----BEGIN CERTIFICATE-----
MIIBazCCARKgAwIBAgIUe71AKu4pceHosqhXfxgQKSfG02QwCgYIKoZIzj0EAwIw
GzEZMBcGA1UEAwwQbmV3cy1zZXJ2ZXItdGVzdDAgFw0yNjExMTAyMDE1MTJaGA8y
MTI2MTAxNzIwMTUxMlowGzEZMBcGA1UEAwwQbmV3cy1zZXJ2ZXItdGVzdDBZMBMG
ByqGSM49AgEGCCqGSM49AwEHA0IABNX+p0W+ivGQb5QaFcplyCfzkKsTQRTsIJbW
ZVd9Kc1JpcifarSWNjHkocKVYPzfyB++PjoK9LbBG1Qfs4rx6UKjMjAwMB0GA1Ud
DgQWBBT2kxoOiWFz/2UsKRihEOvAhuaDljAPBgNVHRMBAf8EBTADAQH/MAoGCCqG
SM49BAMCA0cAMEQCIDAImuu5JytLxfbSSiTtj9ecO/a4L8alzY4dVvzCSrjLAiAj
OGBLslU+dluE3xWWYvPpjSjvS78FKhlSzAQ95Kz7Rw==
-----END CERTIFICATE-----"""

internal fun testCertificate(): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(TEST_CERTIFICATE.byteInputStream()) as X509Certificate
