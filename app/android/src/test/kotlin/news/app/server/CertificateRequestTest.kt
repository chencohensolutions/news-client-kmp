package news.app.server

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateRequestTest {

    @Test
    fun theRequestCarriesTheKeyAndAValidSignature() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        var signed = ByteArray(0)
        val csr = CertificateRequest.build("Pixel", pair.public.encoded) { info ->
            signed = info
            Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(info); sign() }
        }
        // A DER SEQUENCE holding the signed info, which holds the public key as given.
        assertEquals(0x30, csr[0].toInt() and 0xff)
        assertTrue(csr.containsSlice(signed))
        assertTrue(signed.containsSlice(pair.public.encoded))
        assertTrue(signed.containsSlice("Pixel".toByteArray()))
        // The signature at its end verifies over the info with the key.
        val signature = csr.copyOfRange(csr.size - signatureLength(csr), csr.size)
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(pair.public); update(signed); verify(signature) })
    }

    /** The BIT STRING at the end of the request: its length, without the leading "no unused bits" byte. */
    private fun signatureLength(csr: ByteArray): Int {
        // ECDSA signatures in DER are 70–72 bytes: a SEQUENCE whose length byte follows its tag.
        for (n in 68..74) {
            val start = csr.size - n
            if (csr[start].toInt() == 0x30 && (csr[start + 1].toInt() and 0xff) == n - 2 && csr[start - 1].toInt() == 0) return n
        }
        error("No signature found")
    }

    private fun ByteArray.containsSlice(part: ByteArray): Boolean =
        (0..size - part.size).any { i -> part.indices.all { this[i + it] == part[it] } }
}
