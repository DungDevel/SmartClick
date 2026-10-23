package com.example.smartclick.app.tvconnection.security

import com.example.smartclick.tv.runtime.BuildConfig
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import javax.net.ssl.X509TrustManager

/**
 * TrustManager specifically for Google Cast Device Authentication.
 * It enforces strict chain validation against approved Cast Root CAs in production,
 * and fails closed if no trust anchors are provided unless running in a compiled debug build (BuildConfig.DEBUG).
 */
class GoogleCastTrustManager(
    private val trustedRootCerts: List<X509Certificate>
) : X509TrustManager {

    private val isDebugAllowed: Boolean
        get() = try {
            BuildConfig.DEBUG
        } catch (e: Exception) {
            false
        }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")

        try {
            // Check certificate validity (NotBefore/NotAfter) for all certs in chain
            chain.forEach { it.checkValidity() }

            if (trustedRootCerts.isNotEmpty()) {
                val cf = CertificateFactory.getInstance("X.509")
                val certPath = cf.generateCertPath(chain.toList())

                val trustAnchors = trustedRootCerts.map { TrustAnchor(it, null) }.toSet()
                val pkixParams = PKIXParameters(trustAnchors).apply {
                    isRevocationEnabled = false // Local network Cast devices do not use online CRL/OCSP
                }

                val validator = CertPathValidator.getInstance("PKIX")
                validator.validate(certPath, pkixParams)
            } else {
                // Fail closed in production when no trust anchors are provided
                if (!isDebugAllowed) {
                    throw CertificateException("Production security policy violation: empty trust store is not permitted in release builds.")
                }
                // Development-only fallback: strictly restricted to compiled debug builds (BuildConfig.DEBUG == true)
                if (chain.size == 1) {
                    chain[0].verify(chain[0].publicKey)
                } else {
                    for (i in 0 until chain.size - 1) {
                        chain[i].verify(chain[i + 1].publicKey)
                    }
                }
            }

        } catch (e: Exception) {
            if (e is CertificateException) throw e
            throw CertificateException("Chain validation failed: ${e.message}", e)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Not used for client auth
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = trustedRootCerts.toTypedArray()
}
