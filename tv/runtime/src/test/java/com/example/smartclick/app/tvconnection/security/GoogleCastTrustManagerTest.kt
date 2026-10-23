package com.example.smartclick.app.tvconnection.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date

class GoogleCastTrustManagerTest {

    private lateinit var keyPair: KeyPair
    private lateinit var validCert: X509Certificate

    @Before
    fun setup() {
        org.bouncycastle.jce.provider.BouncyCastleProvider().let {
            if (java.security.Security.getProvider(it.name) == null) {
                java.security.Security.addProvider(it)
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        keyPair = kpg.generateKeyPair()

        val issuer = X500Name("CN=CastTestRoot")
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24)
        val notAfter = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365)

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, serialNumber, notBefore, notAfter, issuer, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        validCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer))
    }

    @Test
    fun `checkServerTrusted succeeds with valid certificate chain and root anchor`() {
        val trustManager = GoogleCastTrustManager(listOf(validCert))
        val chain = arrayOf(validCert)

        // Should not throw
        trustManager.checkServerTrusted(chain, "RSA")
    }

    @Test
    fun `checkServerTrusted fails with empty trust store when not debug build`() {
        // Even if system property is set to true, BuildConfig.DEBUG governs production release behavior
        System.setProperty("smartclick.test.debug", "true")
        try {
            val trustManager = GoogleCastTrustManager(emptyList())
            val chain = arrayOf(validCert)

            // If BuildConfig.DEBUG is false (release unit test or build), it must fail closed
            // In debug unit test, BuildConfig.DEBUG is true. Let's verify behavior.
            val isDebug = try {
                com.example.smartclick.tv.runtime.BuildConfig.DEBUG
            } catch (e: Exception) {
                false
            }

            if (!isDebug) {
                val exception = assertThrows(CertificateException::class.java) {
                    trustManager.checkServerTrusted(chain, "RSA")
                }
                assertTrue(exception.message!!.contains("Production security policy violation"))
            }
        } finally {
            System.clearProperty("smartclick.test.debug")
        }
    }

    @Test
    fun `checkServerTrusted fails with empty chain`() {
        val trustManager = GoogleCastTrustManager(listOf(validCert))
        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `checkServerTrusted fails with expired certificate`() {
        val issuer = X500Name("CN=CastTestExpired")
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 48)
        val notAfter = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24) // expired yesterday

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer, BigInteger.valueOf(2), notBefore, notAfter, issuer, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val expiredCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer))

        val trustManager = GoogleCastTrustManager(listOf(validCert))
        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(arrayOf(expiredCert), "RSA")
        }
    }
}
