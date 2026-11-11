package news.app.server

import okhttp3.OkHttpClient
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Accepts the server whose certificate's key hashes to [fingerprint] ([fingerprintOf]), whatever its CA, host name or
 * address; any other is refused. The news server's certificate is signed by its own CA, which the phone doesn't
 * know: the key comes from the enrollment QR code, or from the owner confirming it when the code is typed.
 */
class PinnedTrustManager(private val fingerprint: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        if (!fingerprintOf(leaf.publicKey.encoded).equals(fingerprint, ignoreCase = true)) throw WrongServerKeyException()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit =
        throw CertificateException("Not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** The server presented a key other than the one pinned: another server at that address, or a new key. */
class WrongServerKeyException : CertificateException("The server's key isn't the one this phone knows")

/**
 * Accepts any server and remembers its key in [seen]: only for asking enrollmentPreview when the code was typed, whose
 * answer is shown with the key for the owner to compare with what `enroll-code` printed. Nothing is sent but the code.
 */
class KeySeeingTrustManager : X509TrustManager {
    @Volatile
    var seen: String? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        seen = fingerprintOf(leaf.publicKey.encoded)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit =
        throw CertificateException("Not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** An OkHttp client for the news server: [trust] decides the server, [identity] (once enrolled) is the phone's. */
fun serverHttpClient(trust: X509TrustManager, identity: X509ExtendedKeyManager? = null): OkHttpClient {
    val ssl = SSLContext.getInstance("TLS").apply { init(identity?.let { arrayOf(it) }, arrayOf(trust), null) }
    return OkHttpClient.Builder()
        .sslSocketFactory(ssl.socketFactory, trust)
        // The key is checked instead of the name: an IP address the certificate doesn't list works.
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Presents the phone's one identity: its Keystore key, which never leaves the hardware, and the certificate the
 * server issued for it. Handed to the TLS stack directly, as the key can't be copied into another KeyStore.
 */
class DeviceKeyManager(private val alias: String, private val key: PrivateKey, private val chain: Array<X509Certificate>) :
    X509ExtendedKeyManager() {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
    override fun getCertificateChain(alias: String?) = chain
    override fun getPrivateKey(alias: String?) = key
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
}
