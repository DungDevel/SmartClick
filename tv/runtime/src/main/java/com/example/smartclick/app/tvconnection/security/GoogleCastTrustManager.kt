package com.example.smartclick.app.tvconnection.security

import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.io.ByteArrayInputStream
import javax.net.ssl.X509TrustManager

/**
 * TrustManager specifically for Google Cast Device Authentication.
 * It enforces strict chain validation against approved Cast Root CAs.
 */
class GoogleCastTrustManager(
    private val trustedRootCerts: List<X509Certificate>
) : X509TrustManager {

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")

        try {
            val cf = CertificateFactory.getInstance("X.509")
            val certPath = cf.generateCertPath(chain.toList())

            val trustAnchors = trustedRootCerts.map { TrustAnchor(it, null) }.toSet()
            val pkixParams = PKIXParameters(trustAnchors).apply {
                isRevocationEnabled = false // TODO: Configure CRL handling
            }

            val validator = CertPathValidator.getInstance("PKIX")
            validator.validate(certPath, pkixParams)

            // Additional check: Validate all certs in chain
            chain.forEach { it.checkValidity() }

        } catch (e: Exception) {
            throw CertificateException("Chain validation failed: ${e.message}", e)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Not used for client auth
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = trustedRootCerts.toTypedArray()
}
