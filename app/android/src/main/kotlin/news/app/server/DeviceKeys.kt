package news.app.server

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import news.app.server.graphql.type.KeyProtection

/** A new key ready for a certificate request: its public half ([publicKeyInfo], DER) and how it signs. */
interface DeviceKey {
    val protection: KeyProtection
    val publicKeyInfo: ByteArray
    fun sign(data: ByteArray): ByteArray
}

/**
 * The phone's keys for the news server, in the Android Keystore: an EC P-256 key in StrongBox when the phone has it,
 * else in the TEE (in software on an emulator). The private key never leaves it. As the journal app keeps its own.
 */
interface DeviceKeys {
    /** A new key under [alias], replacing any there. Blocks. */
    fun generate(alias: String): DeviceKey

    /** The server's certificate for [alias]'s key: the key is then an identity for TLS. */
    fun saveCertificate(alias: String, certificateDer: ByteArray)

    /** [alias]'s key and certificate as a TLS identity; null without them. */
    fun identity(alias: String): DeviceKeyManager?

    fun delete(alias: String)
}

class AndroidDeviceKeys : DeviceKeys {

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }

    override fun generate(alias: String): DeviceKey {
        delete(alias)
        val pair = try {
            generate(alias, strongBox = true)
        } catch (e: ProviderException) {
            // StrongBoxUnavailableException, or a StrongBox that can't make this key.
            generate(alias, strongBox = false)
        }
        return KeystoreKey(pair, protection(pair.private))
    }

    override fun saveCertificate(alias: String, certificateDer: ByteArray) {
        val store = keyStore()
        val key = store.getKey(alias, null) ?: error("No key for $alias")
        // For a Keystore key this replaces the placeholder self-signed certificate only.
        store.setKeyEntry(alias, key, null, arrayOf(parseCertificate(certificateDer)))
    }

    override fun identity(alias: String): DeviceKeyManager? {
        val store = keyStore()
        if (!store.containsAlias(alias)) return null
        val key = store.getKey(alias, null) as? PrivateKey ?: return null
        val chain = store.getCertificateChain(alias)?.map { it as X509Certificate }?.toTypedArray() ?: return null
        return DeviceKeyManager(alias, key, chain)
    }

    override fun delete(alias: String) {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun generate(alias: String, strongBox: Boolean): KeyPair {
        // StrongBox signs with SHA-256 only; the TLS stack may also ask for other digests, or none.
        val digests = if (strongBox) {
            arrayOf(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
        } else {
            arrayOf(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
        }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(*digests)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(strongBox) else if (strongBox) throw ProviderException("No StrongBox before Android 9") }
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEY_STORE).apply { initialize(spec) }.generateKeyPair()
    }

    private fun protection(key: PrivateKey): KeyProtection {
        val info = KeyFactory.getInstance(key.algorithm, KEY_STORE).getKeySpec(key, KeyInfo::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyProtection.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeyProtection.TEE
                else -> KeyProtection.SOFTWARE
            }
        }
        @Suppress("DEPRECATION")
        return if (info.isInsideSecureHardware) KeyProtection.TEE else KeyProtection.SOFTWARE
    }

    private companion object {
        const val KEY_STORE = "AndroidKeyStore"
    }
}

private class KeystoreKey(private val pair: KeyPair, override val protection: KeyProtection) : DeviceKey {
    override val publicKeyInfo: ByteArray get() = pair.public.encoded

    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(pair.private)
        update(data)
        sign()
    }
}

fun parseCertificate(der: ByteArray): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
