package com.example.smartclick.app.tvconnection.security

import android.annotation.SuppressLint
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TrustManager that implements TOFU (Trust On First Use) and Certificate Pinning.
 *
 * - During pairing ([isPairing] = true), it inherently accepts any server certificate (including self-signed)
 *   provided by the TV to capture its thumbprint. This creates a minor TOFU vulnerability window where
 *   an active MitM attack during the initial few seconds of pairing could become the trusted anchor.
 *   This is standard industry practice for smart TV pairing in a local network without pre-shared CA chains.
 *
 * - For connection, it strictly verifies the peer certificate's SHA-256 hash against the [expectedSha256].
 *   This hard-pinning completely defends against subsequent MitM attacks on the local network.
 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
class PinningTrustManager(private val expectedSha256: String?, private val isPairing: Boolean = false) : X509TrustManager {

    private val systemTrustManager: X509TrustManager

    init {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        systemTrustManager = factory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers

    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
        try {
            systemTrustManager.checkClientTrusted(certs, authType)
        } catch (e: CertificateException) {
            // For TOFU, we don't strictly validate client certificates if it's the TV connecting to us,
            // but in most cases, we act as the client connecting to the TV.
            if (!isPairing) throw e
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
        if (certs.isEmpty()) {
            throw CertificateException("No empty certificates allowed")
        }

        val serverCert = certs[0]
        val md = MessageDigest.getInstance("SHA-256")
        val certHash = md.digest(serverCert.encoded).joinToString("") { "%02x".format(it) }

        if (isPairing) {
            // TOFU mode: Accept the self-signed certificate during the pairing phase.
            // The driver will capture this hash from the peer certificates and persist it in TvCredentialStore.
            return
        }

        if (expectedSha256 == null) {
            throw CertificateException("Missing pinned certificate hash. Please pair the device again.")
        }

        if (!certHash.equals(expectedSha256, ignoreCase = true)) {
            throw CertificateException("Certificate pinning failed! Expected: $expectedSha256, Got: $certHash")
        }
    }
}
