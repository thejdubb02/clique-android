package dev.useclique.android.api

import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds an OkHttp client that still validates certificates.
 *
 * Extra PEM material, if any, is added to the trust store alongside the
 * platform CAs. There is no trust-all path.
 */
object Tls {

    fun httpClient(caPem: String?, readTimeoutSeconds: Long = 30): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        val pem = caPem?.trim().orEmpty()
        if (pem.isNotEmpty()) {
            val trust = compositeTrust(pem)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(trust), null)
            builder.sslSocketFactory(ctx.socketFactory, trust)
        }
        return builder.build()
    }

    private fun compositeTrust(pem: String): X509TrustManager {
        val extras = loadCerts(pem)
        val extraStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            extras.forEachIndexed { i, cert -> setCertificateEntry("user-ca-$i", cert) }
        }
        val extraTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        extraTmf.init(extraStore)
        val extra = extraTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val sysTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        sysTmf.init(null as KeyStore?)
        val system = sysTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkClientTrusted(chain, authType)
                } catch (_: CertificateException) {
                    extra.checkClientTrusted(chain, authType)
                }
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    extra.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return system.acceptedIssuers + extra.acceptedIssuers
            }
        }
    }

    private fun loadCerts(pem: String): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val blocks = PEM_BLOCK.findAll(pem).map { it.value }.toList().ifEmpty { listOf(pem) }
        return blocks.map { block ->
            factory.generateCertificate(ByteArrayInputStream(block.toByteArray())) as X509Certificate
        }
    }

    private val PEM_BLOCK = Regex(
        "-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
        RegexOption.DOT_MATCHES_ALL,
    )
}
