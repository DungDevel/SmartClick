package com.example.smartclick.app.tvconnection.security

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Cast TLS transport pin. The first certificate is provisional only until Cast DeviceAuth succeeds. */
class GoogleCastTlsTrustManager(
    private val expectedSha256: String?,
    private val firstUseAllowed: Boolean
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing Cast TLS certificate")
        leaf.checkValidity()
        if (expectedSha256 == null) {
            if (firstUseAllowed) return
            throw CertificateException("Cast TLS pin is missing for a paired device")
        }
        val actual = sha256(leaf)
        if (!actual.equals(expectedSha256, ignoreCase = true)) throw CertificateException("Cast TLS certificate pin mismatch")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun sha256(cert: X509Certificate): String = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}
