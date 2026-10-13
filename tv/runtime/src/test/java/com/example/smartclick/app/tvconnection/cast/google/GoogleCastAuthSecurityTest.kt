package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthResponse
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
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

class GoogleCastAuthSecurityTest {

    private lateinit var authManager: GoogleCastAuthManager
    private lateinit var mockTrustManager: MockTrustManager
    private lateinit var keyPair: KeyPair
    private lateinit var deviceCertDer: ByteArray

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

        // Generate self-signed real X509 certificate
        val issuer = X500Name("CN=TestDevice")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24)
        val notAfter = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365)
        
        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, serialNumber, notBefore, notAfter, issuer, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer))

        deviceCertDer = cert.encoded
    }

    @Test
    fun `verifyResponse fails when nonce does not match`() {
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(ByteArray(16) { 1 }))
            .build()
        val response = AuthResponse.newBuilder()
            .setSignature(com.google.protobuf.ByteString.copyFrom(ByteArray(256) { 0 }))
            .setClientAuthCertificate(com.google.protobuf.ByteString.copyFrom(deviceCertDer))
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(ByteArray(16) { 2 }))
            .build()

        val result = authManager.verifyResponse(challenge, response, deviceCertDer)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("Nonce mismatch"))
    }

    @Test
    fun `verifyResponse fails when TLS peer certificate is not used for signed data`() {
        val nonce = ByteArray(16) { 1 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val tlsPeerCertDer = ByteArray(32) { 7 } // The real TLS cert hash

        // Sign the data using WRONG binding
        val wrongSignedData = nonce + ByteArray(32) { 8 }
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(wrongSignedData)
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
    fun `verifyResponse fails when trust manager rejects chain`() {
        mockTrustManager.shouldTrust = false

        val nonce = ByteArray(16) { 1 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .build()

        val tlsPeerCertDer = ByteArray(32) { 1 }

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(nonce + tlsPeerCertDer)
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
        // the error will be from trustManager bubbling up inside verification
    }

    @Test
    fun `verifyResponse succeeds with valid payload`() {
        val nonce = ByteArray(16) { 1 }
        val challenge = AuthChallenge.newBuilder()
            .setSenderNonce(com.google.protobuf.ByteString.copyFrom(nonce))
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PKCS1v15)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val tlsPeerCertDer = ByteArray(32) { 9 }

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(nonce + tlsPeerCertDer)
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

    private class MockTrustManager : X509TrustManager {
        var shouldTrust = true
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (!shouldTrust) throw java.security.cert.CertificateException("Untrusted chain")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
