package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthResponse
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream
import javax.net.ssl.X509TrustManager

/**
 * Handles the cryptographic verification of Google Cast devices.
 * This class is isolated from the network session to allow independent testing of crypto logic.
 */
class GoogleCastAuthManager(
    private val trustManager: X509TrustManager
) {

    /**
     * Verifies the device authentication response.
     *
     * @param challenge The original challenge sent to the device.
     * @param response The response received from the device.
     * @param tlsPeerCertDer The DER-encoded certificate of the TLS peer captured during handshake.
     * @return Result indicating success or a specific authentication failure.
     */
    fun verifyResponse(
        challenge: AuthChallenge,
        response: AuthResponse,
        tlsPeerCertDer: ByteArray
    ): Result<Unit> {
        // 1. Verify Nonce match
        if (!challenge.senderNonce.toByteArray().contentEquals(response.senderNonce.toByteArray())) {
            return Result.failure(SecurityException("Nonce mismatch: the device responded with a different nonce than requested"))
        }

        // 2. Validate Algorithms
        if (!isAlgorithmSupported(response.signatureAlgorithm, response.hashAlgorithm)) {
            return Result.failure(SecurityException("Unsupported cryptographic algorithms: ${response.signatureAlgorithm}, ${response.hashAlgorithm}"))
        }

        try {
            // 3. Parse Certificates
            val cf = CertificateFactory.getInstance("X.509")
            val deviceCert = cf.generateCertificate(ByteArrayInputStream(response.clientAuthCertificate.toByteArray())) as X509Certificate

            val chain = mutableListOf<X509Certificate>()
            chain.add(deviceCert)
            response.intermediateCertificateList.forEach { certBytes ->
                chain.add(cf.generateCertificate(ByteArrayInputStream(certBytes.toByteArray())) as X509Certificate)
            }

            // 4. Verify Certificate Chain to Trust Anchor
            verifyChain(chain)

            // C1 SECURITY FIX: Check certificate validity (NotBefore/NotAfter)
            chain.forEach { it.checkValidity() }

            // 5. Verify Signature
            // IMPORTANT: Signed data must be (sender_nonce || TLS_peer_certificate_DER)
            val signedData = challenge.senderNonce.toByteArray() + tlsPeerCertDer
            verifySignature(signedData, response.signature.toByteArray(), deviceCert.publicKey, response.signatureAlgorithm)

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(SecurityException("Authentication verification failed: ${e.message}", e))
        }
    }

    private fun isAlgorithmSupported(sigAlgo: AuthChallenge.SignatureAlgorithm, hashAlgo: AuthChallenge.HashAlgorithm): Boolean {
        // Enforce SHA256 for all supported algorithms. Reject SHA1.
        if (hashAlgo != AuthChallenge.HashAlgorithm.SHA256) return false

        return when (sigAlgo) {
            AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15,
            AuthChallenge.SignatureAlgorithm.RSASSA_PSS -> true
            else -> false
        }
    }

    private fun verifyChain(chain: List<X509Certificate>) {
        // Convert our chain to the format expected by X509TrustManager
        // The trustManager is mandatory in the constructor, so we don't need null checks here.
        trustManager.checkServerTrusted(chain.toTypedArray(), "RSA")
    }

    private fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey, algo: AuthChallenge.SignatureAlgorithm) {
        val sigInstance = when (algo) {
            AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15 -> Signature.getInstance("SHA256withRSA")
            AuthChallenge.SignatureAlgorithm.RSASSA_PSS -> Signature.getInstance("SHA256withRSA/PSS")
            else -> throw IllegalArgumentException("Unsupported signature algorithm")
        }

        sigInstance.initVerify(publicKey)
        sigInstance.update(data)
        if (!sigInstance.verify(signatureBytes)) {
            throw SecurityException("Invalid device signature: the response was not signed by the device's private key")
        }
    }
}

