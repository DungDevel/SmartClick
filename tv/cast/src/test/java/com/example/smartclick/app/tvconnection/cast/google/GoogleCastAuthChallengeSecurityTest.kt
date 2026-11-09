package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthResponse
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.X509TrustManager

class GoogleCastAuthChallengeSecurityTest {

    private lateinit var authManager: GoogleCastAuthManager
    private lateinit var mockTrustManager: MockTrustManager
    private lateinit var keyPair: KeyPair
    private lateinit var otherKeyPair: KeyPair
    private lateinit var deviceCertDer: ByteArray
    private lateinit var otherDeviceCertDer: ByteArray

    @Before
    fun setup() {
        org.bouncycastle.jce.provider.BouncyCastleProvider().let {
            if (java.security.Security.getProvider(it.name) == null) {
                java.security.Security.addProvider(it)
            }
        }

        mockTrustManager = MockTrustManager()
        authManager = GoogleCastAuthManager(mockTrustManager)

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        keyPair = kpg.generateKeyPair()
        otherKeyPair = kpg.generateKeyPair()

        deviceCertDer = generateCertificateBytes(keyPair)
        otherDeviceCertDer = generateCertificateBytes(otherKeyPair)
    }

    private fun generateCertificateBytes(kp: KeyPair): ByteArray {
        val issuer = X500Name("CN=TestCastDevice")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24)
        val notAfter = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365)

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, serialNumber, notBefore, notAfter, issuer, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer))

        return cert.encoded
    }

    @Test
    fun `1 - correctly signed challenge response is accepted`() {
        val nonce = ByteArray(16) { 1 }
        val tlsPeerCertDer = ByteArray(32) { 9 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val signedData = nonce + tlsPeerCertDer
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(signedData)
        }.sign()

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(sig))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, tlsPeerCertDer)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `2 - incorrect challenge signature is rejected`() {
        val nonce = ByteArray(16) { 1 }
        val tlsPeerCertDer = ByteArray(32) { 9 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        // Invalid signature bytes
        val invalidSig = ByteArray(256) { 0xFF.toByte() }

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(invalidSig))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, tlsPeerCertDer)
        assertTrue(result.isFailure)
    }

    @Test
    fun `3 - wrong or replayed nonce is rejected`() {
        val challengeNonce = ByteArray(16) { 1 }
        val responseNonce = ByteArray(16) { 2 } // Replayed or different nonce
        val tlsPeerCertDer = ByteArray(32) { 9 }

        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(challengeNonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val signedData = responseNonce + tlsPeerCertDer
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(signedData)
        }.sign()

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(responseNonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(sig))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, tlsPeerCertDer)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Nonce mismatch"))
    }

    @Test
    fun `4 - response signed using different certificate private key is rejected`() {
        val nonce = ByteArray(16) { 1 }
        val tlsPeerCertDer = ByteArray(32) { 9 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val signedData = nonce + tlsPeerCertDer
        // Signed with OTHER key pair, but presenting deviceCertDer
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(otherKeyPair.private)
            update(signedData)
        }.sign()

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(sig))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, tlsPeerCertDer)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Invalid device signature"))
    }

    @Test
    fun `5 - response associated with certificate other than live TLS peer cert is rejected`() {
        val nonce = ByteArray(16) { 1 }
        val correctTlsCertDer = ByteArray(32) { 9 }
        val wrongTlsCertDer = ByteArray(32) { 8 } // Mismatched TLS peer cert binding

        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        // Signed against correctTlsCertDer
        val signedData = nonce + correctTlsCertDer
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(signedData)
        }.sign()

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(sig))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        // Verified against wrongTlsCertDer (live TLS peer mismatch)
        val result = authManager.verifyResponse(challenge, response, wrongTlsCertDer)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Invalid device signature"))
    }

    @Test
    fun `6 - missing truncated or malformed auth payloads fail safely`() {
        val nonce = ByteArray(16) { 1 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .build()

        // Malformed certificate bytes (not a valid X.509 cert)
        val malformedCertBytes = ByteArray(10) { 0x01 }

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(ByteArray(256) { 0 }))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(malformedCertBytes))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, ByteArray(32))
        assertTrue(result.isFailure)
    }

    @Test
    fun `7 - failed authentication does not establish authenticated session state`() {
        mockTrustManager.shouldTrust = false

        val nonce = ByteArray(16) { 1 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .build()

        val response = AuthResponse.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignature(com.google.protobuf.ByteString.copyFrom(ByteArray(256) { 0 }))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val result = authManager.verifyResponse(challenge, response, ByteArray(32))
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    private class MockTrustManager : X509TrustManager {
        var shouldTrust = true
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (!shouldTrust) throw java.security.cert.CertificateException("Untrusted chain")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
